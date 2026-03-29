package za.co.reed.mocksourceservice.adapter;

import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.mocksourceservice.dto.PaymentEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentProcessorAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentProcessorAdapterTest {

    @Mock
    private ObjectMapper testObjectMapper;

    private PaymentProcessorAdapter testPaymentProcessorAdapter;

    private PaymentEvent testSampleEvent;

    @BeforeEach
    void setUp() {
        testPaymentProcessorAdapter = new PaymentProcessorAdapter(testObjectMapper);

        testSampleEvent = new PaymentEvent(
                "evt_12345",
                "payment_intent.succeeded",
                "pi_12345",
                "cus_001",
                "Test Merchant",
                new BigDecimal("100.50"),
                Currency.getInstance("ZAR"),
                Instant.now().minusSeconds(3600),
                0
        );
    }

    @Test
    void normalise_returnsEmptyList_whenPayloadIsNotList() {
        // Given
        Object testInvalidPayload = "not a list";

        // When
        List<NormalisedTransaction> testResult = testPaymentProcessorAdapter.normalise(testInvalidPayload);

        // Then
        assertThat(testResult).isEmpty();
    }

    @Test
    void normalise_returnsNormalisedTransactions_whenPayloadIsValid() throws JsonProcessingException {
        // Given
        List<PaymentEvent> testEvents = List.of(testSampleEvent);
        when(testObjectMapper.writeValueAsString(any(PaymentEvent.class))).thenReturn("{\"raw\": \"payload\"}");

        // When
        List<NormalisedTransaction> testResult = testPaymentProcessorAdapter.normalise(testEvents);

        // Then
        assertThat(testResult).hasSize(1);

        NormalisedTransaction testTransaction = testResult.get(0);
        assertThat(testTransaction.sourceId()).isEqualTo("pi_12345");
        assertThat(testTransaction.sourceType()).isEqualTo(SourceType.PAYMENT_PROCESSOR);
        assertThat(testTransaction.accountId()).isEqualTo("cus_001");
        assertThat(testTransaction.amount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(testTransaction.currency()).isEqualTo(Currency.getInstance("ZAR"));
        assertThat(testTransaction.merchantName()).isEqualTo("Test Merchant");
        assertThat(testTransaction.merchantMcc()).isNull();
        assertThat(testTransaction.status()).isEqualTo(TransactionStatus.SETTLED);
    }

    @Test
    void normalise_filtersDuplicates() throws JsonProcessingException {
        // Given
        PaymentEvent testDuplicateEvent = new PaymentEvent(
                "evt_12345",
                "payment_intent.created",
                "pi_12345",
                "cus_002",
                "Another Merchant",
                new BigDecimal("200.00"),
                Currency.getInstance("USD"),
                Instant.now().minusSeconds(7200),
                0
        );

        List<PaymentEvent> testEvents = List.of(testSampleEvent, testDuplicateEvent);
        when(testObjectMapper.writeValueAsString(any(PaymentEvent.class))).thenReturn("{}");

        // When - First call
        List<NormalisedTransaction> testFirstResult = testPaymentProcessorAdapter.normalise(testEvents);

        // Then - Should only include first record
        assertThat(testFirstResult).hasSize(1);

        // When - Second call with same event
        List<NormalisedTransaction> testSecondResult = testPaymentProcessorAdapter.normalise(List.of(testDuplicateEvent));

        // Then - Should be empty
        assertThat(testSecondResult).isEmpty();
    }

    @Test
    void normalise_handlesJsonSerializationError() throws JsonProcessingException {
        // Given
        List<PaymentEvent> testEvents = List.of(testSampleEvent);
        when(testObjectMapper.writeValueAsString(any(PaymentEvent.class)))
                .thenThrow(new JsonProcessingException("JSON error") {});

        // When
        List<NormalisedTransaction> testResult = testPaymentProcessorAdapter.normalise(testEvents);

        // Then
        assertThat(testResult).hasSize(1);
        assertThat(testResult.get(0).rawPayload()).isEqualTo("{}");
    }

    @Test
    void validate_throwsException_whenSourceIdIsBlank() {
        // Given, When & Then
        assertThatThrownBy(() -> new NormalisedTransaction(
                "",
                SourceType.PAYMENT_PROCESSOR,
                "cus_001",
                new BigDecimal("100.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                null,
                Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED,
                "{}"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceId must not be blank");
    }

    @Test
    void validate_throwsException_whenAmountIsNotPositive() {
        // Given, When & Then
        assertThatThrownBy(() -> new NormalisedTransaction(
                "pi_12345",
                SourceType.PAYMENT_PROCESSOR,
                "cus_001",
                new BigDecimal("-10.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                null,
                Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED,
                "{}"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be positive");
    }

    @Test
    void validate_throwsException_whenAccountIdIsBlank() {
        // Given, When & Then
        assertThatThrownBy(() -> new NormalisedTransaction(
                "pi_12345",
                SourceType.PAYMENT_PROCESSOR,
                "",
                new BigDecimal("100.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                null,
                Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED,
                "{}"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accountId must not be blank");
    }

    @Test
    void validate_doesNotThrow_whenTransactionIsValid() {
        // Given
        NormalisedTransaction testValidTransaction = new NormalisedTransaction(
                "pi_12345",
                SourceType.PAYMENT_PROCESSOR,
                "cus_001",
                new BigDecimal("100.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                null,
                Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED,
                "{}"
        );

        // When & Then
        testPaymentProcessorAdapter.validate(testValidTransaction);
    }

    @Test
    void isDuplicate_returnsTrue_whenSourceIdAlreadySeen() {
        // Given
        String testSourceId = "pi_12345";

        // When - First check
        boolean testFirstCheck = testPaymentProcessorAdapter.isDuplicate(testSourceId);

        // Then
        assertThat(testFirstCheck).isFalse();

        // When - Second check
        boolean testSecondCheck = testPaymentProcessorAdapter.isDuplicate(testSourceId);

        // Then
        assertThat(testSecondCheck).isTrue();
    }

    @Test
    void isDuplicate_returnsFalse_whenSourceIdIsNew() {
        // Given
        String testSourceId = "pi_NEW_123";

        // When
        boolean testResult = testPaymentProcessorAdapter.isDuplicate(testSourceId);

        // Then
        assertThat(testResult).isFalse();
    }

    @Test
    void mapStatus_returnsCorrectStatus() {
        // Given
        PaymentEvent testSucceededEvent = new PaymentEvent(
                "evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0
        );

        PaymentEvent testCreatedEvent = new PaymentEvent(
                "evt_2", "payment_intent.created", "pi_2", "cus_001", "Merchant",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0
        );

        PaymentEvent testRefundedEvent = new PaymentEvent(
                "evt_3", "charge.refunded", "pi_3", "cus_001", "Merchant",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0
        );

        PaymentEvent testUnknownEvent = new PaymentEvent(
                "evt_4", "unknown.event", "pi_4", "cus_001", "Merchant",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0
        );

        try {
            when(testObjectMapper.writeValueAsString(any(PaymentEvent.class))).thenReturn("{}");
        } catch (JsonProcessingException e) {
            // Ignore for test
        }

        // When
        List<PaymentEvent> testEvents = List.of(testSucceededEvent, testCreatedEvent, testRefundedEvent, testUnknownEvent);
        List<NormalisedTransaction> testResult = testPaymentProcessorAdapter.normalise(testEvents);

        // Then
        assertThat(testResult).hasSize(4);
        assertThat(testResult.get(0).status()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(testResult.get(1).status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(testResult.get(2).status()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(testResult.get(3).status()).isEqualTo(TransactionStatus.PENDING); // default for unknown
    }

    @Test
    void normalise_usesPaymentIdAsSourceId() throws JsonProcessingException {
        // Given
        when(testObjectMapper.writeValueAsString(any(PaymentEvent.class))).thenReturn("{}");

        // When
        List<NormalisedTransaction> testResult = testPaymentProcessorAdapter.normalise(List.of(testSampleEvent));

        // Then
        assertThat(testResult).hasSize(1);
        assertThat(testResult.get(0).sourceId()).isEqualTo("pi_12345"); // paymentId, not eventId
    }

    @Test
    void normalise_includesRetryAttemptInRawPayload() throws JsonProcessingException {
        // Given
        PaymentEvent testEventWithRetry = new PaymentEvent(
                "evt_12345",
                "payment_intent.succeeded",
                "pi_12345",
                "cus_001",
                "Test Merchant",
                new BigDecimal("100.50"),
                Currency.getInstance("ZAR"),
                Instant.now().minusSeconds(3600),
                1 // retryAttempt
        );

        when(testObjectMapper.writeValueAsString(any(PaymentEvent.class))).thenReturn("{\"retryAttempt\":1}");

        // When
        List<NormalisedTransaction> testResult = testPaymentProcessorAdapter.normalise(List.of(testEventWithRetry));

        // Then
        assertThat(testResult).hasSize(1);
        assertThat(testResult.get(0).rawPayload()).isEqualTo("{\"retryAttempt\":1}");
    }
}