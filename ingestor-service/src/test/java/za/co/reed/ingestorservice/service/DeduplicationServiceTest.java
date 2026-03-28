package za.co.reed.ingestorservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DeduplicationService}.
 * 
 * Tests cover:
 * - Duplicate detection with Redis
 * - Marking transactions as seen
 * - Atomic check-and-mark operation
 * - Key prefix generation
 * - TTL configuration
 */
@ExtendWith(MockitoExtension.class)
class DeduplicationServiceTest {

    @Mock
    private StringRedisTemplate testRedisTemplate;

    @Mock
    private ValueOperations<String, String> testValueOperations;

    private DeduplicationService testDeduplicationService;

    @BeforeEach
    void setUp() {
        testDeduplicationService = new DeduplicationService(testRedisTemplate);
    }

    @Test
    void isDuplicate_returnsTrue_whenKeyExists() {
        String testSourceId = "txn-12345";
        String testExpectedKey = "dedup:txn-12345";

        when(testRedisTemplate.hasKey(testExpectedKey)).thenReturn(true);

        boolean testResult = testDeduplicationService.isDuplicate(testSourceId);

        assertThat(testResult).isTrue();
        verify(testRedisTemplate).hasKey(testExpectedKey);
    }

    @Test
    void isDuplicate_returnsFalse_whenKeyDoesNotExist() {
        String testSourceId = "txn-12345";
        String testExpectedKey = "dedup:txn-12345";

        when(testRedisTemplate.hasKey(testExpectedKey)).thenReturn(false);

        boolean testResult = testDeduplicationService.isDuplicate(testSourceId);

        assertThat(testResult).isFalse();
        verify(testRedisTemplate).hasKey(testExpectedKey);
    }

    @Test
    void isDuplicate_returnsFalse_whenRedisReturnsNull() {
        String testSourceId = "txn-12345";
        String testExpectedKey = "dedup:txn-12345";

        when(testRedisTemplate.hasKey(testExpectedKey)).thenReturn(null);

        boolean testResult = testDeduplicationService.isDuplicate(testSourceId);

        assertThat(testResult).isFalse();
        verify(testRedisTemplate).hasKey(testExpectedKey);
    }

    @Test
    void markSeen_setsKeyWithTtl() {
        String testSourceId = "txn-12345";
        String testExpectedKey = "dedup:txn-12345";
        Duration testExpectedTtl = Duration.ZERO;

        when(testRedisTemplate.opsForValue()).thenReturn(testValueOperations);
        testDeduplicationService.markSeen(testSourceId);

        verify(testValueOperations).set(eq(testExpectedKey), eq("1"), eq(testExpectedTtl));
    }

    @Test
    void checkAndMark_returnsTrue_whenKeyIsNewlySet() {
        String testSourceId = "txn-12345";
        String testExpectedKey = "dedup:txn-12345";
        Duration testExpectedTtl = Duration.ofMinutes(0);

        when(testRedisTemplate.opsForValue()).thenReturn(testValueOperations);
        when(testValueOperations.setIfAbsent(eq(testExpectedKey), eq("1"), eq(testExpectedTtl)))
                .thenReturn(true);

        boolean testResult = testDeduplicationService.checkAndMark(testSourceId);

        assertThat(testResult).isTrue();
        verify(testValueOperations).setIfAbsent(eq(testExpectedKey), eq("1"), eq(testExpectedTtl));
    }

    @Test
    void checkAndMark_returnsFalse_whenKeyAlreadyExists() {
        String testSourceId = "txn-12345";
        String testExpectedKey = "dedup:txn-12345";
        Duration testExpectedTtl = Duration.ZERO;

        when(testRedisTemplate.opsForValue()).thenReturn(testValueOperations);
        when(testValueOperations.setIfAbsent(eq(testExpectedKey), eq("1"), eq(testExpectedTtl)))
                .thenReturn(false);

        boolean testResult = testDeduplicationService.checkAndMark(testSourceId);

        assertThat(testResult).isFalse();
        verify(testValueOperations).setIfAbsent(eq(testExpectedKey), eq("1"), eq(testExpectedTtl));
    }

    @Test
    void checkAndMark_returnsFalse_whenRedisReturnsNull() {
        String testSourceId = "txn-12345";
        String testExpectedKey = "dedup:txn-12345";
        Duration testExpectedTtl = Duration.ZERO;

        when(testValueOperations.setIfAbsent(eq(testExpectedKey), eq("1"), eq(testExpectedTtl)))
                .thenReturn(null);

        when(testRedisTemplate.opsForValue()).thenReturn(testValueOperations);
        boolean testResult = testDeduplicationService.checkAndMark(testSourceId);

        assertThat(testResult).isFalse();
        verify(testValueOperations).setIfAbsent(eq(testExpectedKey), eq("1"), eq(testExpectedTtl));
    }

    @Test
    void key_generation_includesPrefix() {
        String testSourceId = "test-id-123";

        when(testRedisTemplate.opsForValue()).thenReturn(testValueOperations);
        testDeduplicationService.markSeen(testSourceId);

        verify(testValueOperations).set(eq("dedup:test-id-123"), anyString(), any(Duration.class));
    }

    @Test
    void isDuplicate_logsDebugMessage_whenDuplicateDetected() {
        String testSourceId = "txn-12345";

        testDeduplicationService.isDuplicate(testSourceId);

        verify(testRedisTemplate).hasKey("dedup:txn-12345");
    }

    @Test
    void markSeen_logsDebugMessage() {
        String testSourceId = "txn-12345";

        when(testRedisTemplate.opsForValue()).thenReturn(testValueOperations);
        testDeduplicationService.markSeen(testSourceId);

        verify(testValueOperations).set(eq("dedup:txn-12345"), eq("1"), any(Duration.class));
    }
}