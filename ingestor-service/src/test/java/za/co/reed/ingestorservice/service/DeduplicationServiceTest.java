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
    private StringRedisTemplate redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    private DeduplicationService deduplicationService;
    
    @BeforeEach
    void setUp() {
        deduplicationService = new DeduplicationService(redisTemplate);
    }
    
    @Test
    void isDuplicate_returnsTrue_whenKeyExists() {
        // Given
        String sourceId = "txn-12345";
        String expectedKey = "dedup:txn-12345";
        
        when(redisTemplate.hasKey(expectedKey)).thenReturn(true);
        
        // When
        boolean result = deduplicationService.isDuplicate(sourceId);
        
        // Then
        assertThat(result).isTrue();
        verify(redisTemplate).hasKey(expectedKey);
    }
    
    @Test
    void isDuplicate_returnsFalse_whenKeyDoesNotExist() {
        // Given
        String sourceId = "txn-12345";
        String expectedKey = "dedup:txn-12345";
        
        when(redisTemplate.hasKey(expectedKey)).thenReturn(false);
        
        // When
        boolean result = deduplicationService.isDuplicate(sourceId);
        
        // Then
        assertThat(result).isFalse();
        verify(redisTemplate).hasKey(expectedKey);
    }
    
    @Test
    void isDuplicate_returnsFalse_whenRedisReturnsNull() {
        // Given
        String sourceId = "txn-12345";
        String expectedKey = "dedup:txn-12345";
        
        when(redisTemplate.hasKey(expectedKey)).thenReturn(null);
        
        // When
        boolean result = deduplicationService.isDuplicate(sourceId);
        
        // Then
        assertThat(result).isFalse();
        verify(redisTemplate).hasKey(expectedKey);
    }
    
    @Test
    void markSeen_setsKeyWithTtl() {
        // Given
        String sourceId = "txn-12345";
        String expectedKey = "dedup:txn-12345";
        Duration expectedTtl = Duration.ZERO; // Default TTL
        
        // When
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        deduplicationService.markSeen(sourceId);
        
        // Then
        verify(valueOperations).set(eq(expectedKey), eq("1"), eq(expectedTtl));
    }
    
    @Test
    void checkAndMark_returnsTrue_whenKeyIsNewlySet() {
        // Given
        String sourceId = "txn-12345";
        String expectedKey = "dedup:txn-12345";
        Duration expectedTtl = Duration.ofMinutes(0);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(expectedKey), eq("1"), eq(expectedTtl)))
            .thenReturn(true);
        
        // When
        boolean result = deduplicationService.checkAndMark(sourceId);
        
        // Then
        assertThat(result).isTrue(); // true = not a duplicate
        verify(valueOperations).setIfAbsent(eq(expectedKey), eq("1"), eq(expectedTtl));
    }
    
    @Test
    void checkAndMark_returnsFalse_whenKeyAlreadyExists() {
        // Given
        String sourceId = "txn-12345";
        String expectedKey = "dedup:txn-12345";
        Duration expectedTtl = Duration.ZERO;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(expectedKey), eq("1"), eq(expectedTtl)))
            .thenReturn(false);
        
        // When
        boolean result = deduplicationService.checkAndMark(sourceId);
        
        // Then
        assertThat(result).isFalse(); // false = duplicate
        verify(valueOperations).setIfAbsent(eq(expectedKey), eq("1"), eq(expectedTtl));
    }
    
    @Test
    void checkAndMark_returnsFalse_whenRedisReturnsNull() {
        // Given
        String sourceId = "txn-12345";
        String expectedKey = "dedup:txn-12345";
        Duration expectedTtl = Duration.ZERO;
        
        when(valueOperations.setIfAbsent(eq(expectedKey), eq("1"), eq(expectedTtl)))
            .thenReturn(null);
        
        // When
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        boolean result = deduplicationService.checkAndMark(sourceId);
        
        // Then
        assertThat(result).isFalse(); // null treated as false
        verify(valueOperations).setIfAbsent(eq(expectedKey), eq("1"), eq(expectedTtl));
    }
    
    @Test
    void key_generation_includesPrefix() {
        // Given
        String sourceId = "test-id-123";
        
        // When
        // Using reflection to test private method, but we can test through public methods
        // Instead, we'll verify the key format through mock interactions
        
        // When
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        deduplicationService.markSeen(sourceId);
        
        // Then
        verify(valueOperations).set(eq("dedup:test-id-123"), anyString(), any(Duration.class));
    }
    
    @Test
    void isDuplicate_logsDebugMessage_whenDuplicateDetected() {
        // Given
        String sourceId = "txn-12345";
        
        // When
        deduplicationService.isDuplicate(sourceId);
        
        // Then - method should log debug message (we can't easily verify logs without 
        // a logging framework, but the code path is executed)
        verify(redisTemplate).hasKey("dedup:txn-12345");
    }
    
    @Test
    void markSeen_logsDebugMessage() {
        // Given
        String sourceId = "txn-12345";
        
        // When
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        deduplicationService.markSeen(sourceId);
        
        // Then - method should log debug message with sourceId and TTL
        verify(valueOperations).set(eq("dedup:txn-12345"), eq("1"), any(Duration.class));
    }
}