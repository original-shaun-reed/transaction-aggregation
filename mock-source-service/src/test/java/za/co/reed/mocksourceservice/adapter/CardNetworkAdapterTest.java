package za.co.reed.mocksourceservice.adapter;

import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.mocksourceservice.dto.CardRecord;
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
 * Unit tests for {@link CardNetworkAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class CardNetworkAdapterTest {

    @Mock
    private ObjectMapper objectMapper;
    
    private CardNetworkAdapter cardNetworkAdapter;
    
    private CardRecord sampleRecord;
    
    @BeforeEach
    void setUp() {
        cardNetworkAdapter = new CardNetworkAdapter(objectMapper);
        
        sampleRecord = new CardRecord(
            "CN-12345",
            "AUTH123",
            "1234",
            "Test Merchant",
            "Test City",
            "5411",
            new BigDecimal("100.50"),
            Currency.getInstance("ZAR"),
            Instant.now().minusSeconds(3600),
            Instant.now().minusSeconds(1800),
            "CLEARED"
        );
    }
    
    @Test
    void normalise_returnsEmptyList_whenPayloadIsNotList() {
        // Given
        Object invalidPayload = "not a list";
        
        // When
        List<NormalisedTransaction> result = cardNetworkAdapter.normalise(invalidPayload);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void normalise_returnsNormalisedTransactions_whenPayloadIsValid() throws JsonProcessingException {
        // Given
        List<CardRecord> records = List.of(sampleRecord);
        when(objectMapper.writeValueAsString(any(CardRecord.class))).thenReturn("{\"raw\": \"payload\"}");
        
        // When
        List<NormalisedTransaction> result = cardNetworkAdapter.normalise(records);
        
        // Then
        assertThat(result).hasSize(1);
        
        NormalisedTransaction transaction = result.get(0);
        assertThat(transaction.sourceId()).isEqualTo("CN-12345");
        assertThat(transaction.sourceType()).isEqualTo(SourceType.CARD_NETWORK);
        assertThat(transaction.accountId()).isEqualTo("1234");
        assertThat(transaction.amount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(transaction.currency()).isEqualTo(Currency.getInstance("ZAR"));
        assertThat(transaction.merchantName()).isEqualTo("Test Merchant");
        assertThat(transaction.merchantMcc()).isEqualTo("5411");
        assertThat(transaction.status()).isEqualTo(TransactionStatus.SETTLED);
    }
    
    @Test
    void normalise_filtersDuplicates() throws JsonProcessingException {
        // Given
        CardRecord duplicateRecord = new CardRecord(
            "CN-12345", // Same ID
            "AUTH456",
            "5678",
            "Another Merchant",
            "Another City",
            "5812",
            new BigDecimal("200.00"),
            Currency.getInstance("USD"),
            Instant.now().minusSeconds(7200),
            Instant.now().minusSeconds(3600),
            "CLEARED"
        );
        
        List<CardRecord> records = List.of(sampleRecord, duplicateRecord);
        when(objectMapper.writeValueAsString(any(CardRecord.class))).thenReturn("{}");
        
        // When - First call
        List<NormalisedTransaction> firstResult = cardNetworkAdapter.normalise(records);
        
        // Then - Should only include first record (second is duplicate)
        assertThat(firstResult).hasSize(1);
        
        // When - Second call with same record
        List<NormalisedTransaction> secondResult = cardNetworkAdapter.normalise(List.of(duplicateRecord));
        
        // Then - Should be empty (duplicate)
        assertThat(secondResult).isEmpty();
    }
    
    @Test
    void normalise_handlesJsonSerializationError() throws JsonProcessingException {
        // Given
        List<CardRecord> records = List.of(sampleRecord);
        when(objectMapper.writeValueAsString(any(CardRecord.class)))
            .thenThrow(new JsonProcessingException("JSON error") {});
        
        // When
        List<NormalisedTransaction> result = cardNetworkAdapter.normalise(records);
        
        // Then - Should still return transaction with fallback JSON
        assertThat(result).hasSize(1);
        assertThat(result.get(0).rawPayload()).isEqualTo("{}");
    }
    
    @Test
    void validate_throwsException_whenSourceIdIsBlank() {
        // Given, When & Then
        assertThatThrownBy(() -> new NormalisedTransaction(
                "", // Blank sourceId
                SourceType.CARD_NETWORK,
                "1234",
                new BigDecimal("100.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "5411",
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
                "CN-12345",
                SourceType.CARD_NETWORK,
                "1234",
                new BigDecimal("-10.00"), // Negative amount
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "5411",
                Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED,
                "{}"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be positive");
    }
    
    @Test
    void validate_throwsException_whenMerchantMccIsBlank() {
        // Given
        NormalisedTransaction invalidTransaction = new NormalisedTransaction(
            "CN-12345",
            SourceType.CARD_NETWORK,
            "1234",
            new BigDecimal("100.00"),
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "", // Blank MCC
            Instant.now().minusSeconds(3600),
            TransactionStatus.SETTLED,
            "{}"
        );
        
        // When & Then
        assertThatThrownBy(() -> cardNetworkAdapter.validate(invalidTransaction))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("merchantMcc must not be blank for card network records");
    }
    
    @Test
    void validate_doesNotThrow_whenTransactionIsValid() {
        // Given
        NormalisedTransaction validTransaction = new NormalisedTransaction(
            "CN-12345",
            SourceType.CARD_NETWORK,
            "1234",
            new BigDecimal("100.00"),
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "5411",
            Instant.now().minusSeconds(3600),
            TransactionStatus.SETTLED,
            "{}"
        );
        
        // When & Then - Should not throw
        cardNetworkAdapter.validate(validTransaction);
    }
    
    @Test
    void isDuplicate_returnsTrue_whenSourceIdAlreadySeen() {
        // Given
        String sourceId = "CN-12345";
        
        // When - First check
        boolean firstCheck = cardNetworkAdapter.isDuplicate(sourceId);
        
        // Then - Should be false (not a duplicate yet)
        assertThat(firstCheck).isFalse();
        
        // When - Second check
        boolean secondCheck = cardNetworkAdapter.isDuplicate(sourceId);
        
        // Then - Should be true (duplicate)
        assertThat(secondCheck).isTrue();
    }
    
    @Test
    void isDuplicate_returnsFalse_whenSourceIdIsNew() {
        // Given
        String sourceId = "CN-NEW-123";
        
        // When
        boolean result = cardNetworkAdapter.isDuplicate(sourceId);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void mapStatus_returnsCorrectStatus() {
        // Test through normalise method
        CardRecord clearedRecord = new CardRecord(
            "CN-1", "AUTH1", "1234", "Merchant", "City", "5411",
            new BigDecimal("100.00"), Currency.getInstance("ZAR"),
            Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800), "CLEARED"
        );
        
        CardRecord authRecord = new CardRecord(
            "CN-2", "AUTH2", "1234", "Merchant", "City", "5411",
            new BigDecimal("100.00"), Currency.getInstance("ZAR"),
            Instant.now().minusSeconds(3600), null, "AUTH"
        );
        
        CardRecord reversedRecord = new CardRecord(
            "CN-3", "AUTH3", "1234", "Merchant", "City", "5411",
            new BigDecimal("100.00"), Currency.getInstance("ZAR"),
            Instant.now().minusSeconds(3600), null, "REVERSED"
        );
        
        CardRecord unknownRecord = new CardRecord(
            "CN-4", "AUTH4", "1234", "Merchant", "City", "5411",
            new BigDecimal("100.00"), Currency.getInstance("ZAR"),
            Instant.now().minusSeconds(3600), null, "UNKNOWN"
        );
        
        try {
            when(objectMapper.writeValueAsString(any(CardRecord.class))).thenReturn("{}");
        } catch (JsonProcessingException e) {
            // Ignore for test
        }
        
        List<CardRecord> records = List.of(clearedRecord, authRecord, reversedRecord, unknownRecord);
        List<NormalisedTransaction> result = cardNetworkAdapter.normalise(records);
        
        assertThat(result).hasSize(4);
        assertThat(result.get(0).status()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(result.get(1).status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.get(2).status()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(result.get(3).status()).isEqualTo(TransactionStatus.PENDING); // default for unknown
    }
    
    @Test
    void toNormalised_usesClearedAtWhenAvailable() throws JsonProcessingException {
        // Given
        when(objectMapper.writeValueAsString(any(CardRecord.class))).thenReturn("{}");
        
        // When
        List<NormalisedTransaction> result = cardNetworkAdapter.normalise(List.of(sampleRecord));
        
        // Then
        assertThat(result).hasSize(1);
        // transactedAt should be clearedAt (not authorisedAt) because record has clearedAt
        assertThat(result.get(0).transactedAt()).isEqualTo(sampleRecord.clearedAt());
    }
    
    @Test
    void toNormalised_usesAuthorisedAtWhenClearedAtIsNull() throws JsonProcessingException {
        // Given
        CardRecord authOnlyRecord = new CardRecord(
            "CN-12345",
            "AUTH123",
            "1234",
            "Test Merchant",
            "Test City",
            "5411",
            new BigDecimal("100.50"),
            Currency.getInstance("ZAR"),
            Instant.now().minusSeconds(3600),
            null, // No clearedAt
            "AUTH"
        );
        
        when(objectMapper.writeValueAsString(any(CardRecord.class))).thenReturn("{}");
        
        // When
        List<NormalisedTransaction> result = cardNetworkAdapter.normalise(List.of(authOnlyRecord));
        
        // Then
        assertThat(result).hasSize(1);
        // transactedAt should be authorisedAt because clearedAt is null
        assertThat(result.get(0).transactedAt()).isEqualTo(authOnlyRecord.authorisedAt());
    }
}