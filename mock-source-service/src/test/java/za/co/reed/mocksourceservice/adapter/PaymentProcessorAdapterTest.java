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
    private ObjectMapper objectMapper;
    
    private PaymentProcessorAdapter paymentProcessorAdapter;
    
    private PaymentEvent sampleEvent;
    
    @BeforeEach
    void setUp() {
        paymentProcessorAdapter = new PaymentProcessorAdapter(objectMapper);
        
        sampleEvent = new PaymentEvent(
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
        Object invalidPayload = "not a list";
        
        // When
        List<NormalisedTransaction> result = paymentProcessorAdapter.normalise(invalidPayload);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void normalise_returnsNormalisedTransactions_whenPayloadIsValid() throws JsonProcessingException {
        // Given
        List<PaymentEvent> events = List.of(sampleEvent);
        when(objectMapper.writeValueAsString(any(PaymentEvent.class))).thenReturn("{\"raw\": \"payload\"}");
        
        // When
        List<NormalisedTransaction> result = paymentProcessorAdapter.normalise(events);
        
        // Then
        assertThat(result).hasSize(1);
        
        NormalisedTransaction transaction = result.get(0);
        assertThat(transaction.sourceId()).isEqualTo("pi_12345");
        assertThat(transaction.sourceType()).isEqualTo(SourceType.PAYMENT_PROCESSOR);
        assertThat(transaction.accountId()).isEqualTo("cus_001");
        assertThat(transaction.amount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(transaction.currency()).isEqualTo(Currency.getInstance("ZAR"));
        assertThat(transaction.merchantName()).isEqualTo("Test Merchant");
        assertThat(transaction.merchantMcc()).isNull(); // Payment processor doesn't provide MCC
        assertThat(transaction.status()).isEqualTo(TransactionStatus.SETTLED);
    }
    
    @Test
    void normalise_filtersDuplicates() throws JsonProcessingException {
        // Given
        PaymentEvent duplicateEvent = new PaymentEvent(
            "evt_12345", // Same eventId
            "payment_intent.created",
            "pi_12345", // Same paymentId
            "cus_002",
            "Another Merchant",
            new BigDecimal("200.00"),
            Currency.getInstance("USD"),
            Instant.now().minusSeconds(7200),
            0
        );
        
        List<PaymentEvent> events = List.of(sampleEvent, duplicateEvent);
        when(objectMapper.writeValueAsString(any(PaymentEvent.class))).thenReturn("{}");
        
        // When - First call
        List<NormalisedTransaction> firstResult = paymentProcessorAdapter.normalise(events);
        
        // Then - Should only include first record (second is duplicate)
        assertThat(firstResult).hasSize(1);
        
        // When - Second call with same event
        List<NormalisedTransaction> secondResult = paymentProcessorAdapter.normalise(List.of(duplicateEvent));
        
        // Then - Should be empty (duplicate)
        assertThat(secondResult).isEmpty();
    }
    
    @Test
    void normalise_handlesJsonSerializationError() throws JsonProcessingException {
        // Given
        List<PaymentEvent> events = List.of(sampleEvent);
        when(objectMapper.writeValueAsString(any(PaymentEvent.class)))
            .thenThrow(new JsonProcessingException("JSON error") {});
        
        // When
        List<NormalisedTransaction> result = paymentProcessorAdapter.normalise(events);
        
        // Then - Should still return transaction with fallback JSON
        assertThat(result).hasSize(1);
        assertThat(result.get(0).rawPayload()).isEqualTo("{}");
    }
    
    @Test
    void validate_throwsException_whenSourceIdIsBlank() {
        // Given, When & Then
        assertThatThrownBy(() -> new NormalisedTransaction(
                "", // Blank sourceId
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
                "", // Blank accountId
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
        NormalisedTransaction validTransaction = new NormalisedTransaction(
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
        
        // When & Then - Should not throw
        paymentProcessorAdapter.validate(validTransaction);
    }
    
    @Test
    void isDuplicate_returnsTrue_whenSourceIdAlreadySeen() {
        // Given
        String sourceId = "pi_12345";
        
        // When - First check
        boolean firstCheck = paymentProcessorAdapter.isDuplicate(sourceId);
        
        // Then - Should be false (not a duplicate yet)
        assertThat(firstCheck).isFalse();
        
        // When - Second check
        boolean secondCheck = paymentProcessorAdapter.isDuplicate(sourceId);
        
        // Then - Should be true (duplicate)
        assertThat(secondCheck).isTrue();
    }
    
    @Test
    void isDuplicate_returnsFalse_whenSourceIdIsNew() {
        // Given
        String sourceId = "pi_NEW_123";
        
        // When
        boolean result = paymentProcessorAdapter.isDuplicate(sourceId);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void mapStatus_returnsCorrectStatus() {
        // Test through normalise method
        PaymentEvent succeededEvent = new PaymentEvent(
            "evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant",
            new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0
        );
        
        PaymentEvent createdEvent = new PaymentEvent(
            "evt_2", "payment_intent.created", "pi_2", "cus_001", "Merchant",
            new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0
        );
        
        PaymentEvent refundedEvent = new PaymentEvent(
            "evt_3", "charge.refunded", "pi_3", "cus_001", "Merchant",
            new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0
        );
        
        PaymentEvent unknownEvent = new PaymentEvent(
            "evt_4", "unknown.event", "pi_4", "cus_001", "Merchant",
            new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0
        );
        
        try {
            when(objectMapper.writeValueAsString(any(PaymentEvent.class))).thenReturn("{}");
        } catch (JsonProcessingException e) {
            // Ignore for test
        }
        
        List<PaymentEvent> events = List.of(succeededEvent, createdEvent, refundedEvent, unknownEvent);
        List<NormalisedTransaction> result = paymentProcessorAdapter.normalise(events);
        
        assertThat(result).hasSize(4);
        assertThat(result.get(0).status()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(result.get(1).status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.get(2).status()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(result.get(3).status()).isEqualTo(TransactionStatus.PENDING); // default for unknown
    }
    
    @Test
    void normalise_usesPaymentIdAsSourceId() throws JsonProcessingException {
        // Given
        when(objectMapper.writeValueAsString(any(PaymentEvent.class))).thenReturn("{}");
        
        // When
        List<NormalisedTransaction> result = paymentProcessorAdapter.normalise(List.of(sampleEvent));
        
        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceId()).isEqualTo("pi_12345"); // paymentId, not eventId
    }
    
    @Test
    void normalise_includesRetryAttemptInRawPayload() throws JsonProcessingException {
        // Given
        PaymentEvent eventWithRetry = new PaymentEvent(
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
        
        when(objectMapper.writeValueAsString(any(PaymentEvent.class))).thenReturn("{\"retryAttempt\":1}");
        
        // When
        List<NormalisedTransaction> result = paymentProcessorAdapter.normalise(List.of(eventWithRetry));
        
        // Then
        assertThat(result).hasSize(1);
        // The raw payload should contain retryAttempt
        assertThat(result.get(0).rawPayload()).isEqualTo("{\"retryAttempt\":1}");
    }
}