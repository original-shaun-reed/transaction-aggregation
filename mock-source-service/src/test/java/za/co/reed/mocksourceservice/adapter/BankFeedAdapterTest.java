package za.co.reed.mocksourceservice.adapter;

import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.mocksourceservice.dto.BankFeedRecord;
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
 * Unit tests for {@link BankFeedAdapter}.
 * 
 * Tests cover:
 * - Normalisation of bank feed records to NormalisedTransaction
 * - Duplicate detection using in-memory store
 * - Validation of NormalisedTransaction fields
 * - JSON serialization error handling
 * - Edge cases (empty payload, wrong payload type)
 */
@ExtendWith(MockitoExtension.class)
class BankFeedAdapterTest {

    @Mock
    private ObjectMapper objectMapper;
    
    private BankFeedAdapter bankFeedAdapter;
    
    private BankFeedRecord sampleRecord;
    
    @BeforeEach
    void setUp() {
        bankFeedAdapter = new BankFeedAdapter(objectMapper);
        
        sampleRecord = new BankFeedRecord(
            "BF-12345",
            "ACC-0001",
            "Test Merchant",
            new BigDecimal("100.50"),
            Currency.getInstance("ZAR"),
            Instant.now().minusSeconds(3600),
            "SETTLED"
        );
    }
    
    @Test
    void normalise_returnsEmptyList_whenPayloadIsNotList() {
        // Given
        Object invalidPayload = "not a list";
        
        // When
        List<NormalisedTransaction> result = bankFeedAdapter.normalise(invalidPayload);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void normalise_returnsNormalisedTransactions_whenPayloadIsValid() throws JsonProcessingException {
        // Given
        List<BankFeedRecord> records = List.of(sampleRecord);
        when(objectMapper.writeValueAsString(any(BankFeedRecord.class))).thenReturn("{\"raw\": \"payload\"}");
        
        // When
        List<NormalisedTransaction> result = bankFeedAdapter.normalise(records);
        
        // Then
        assertThat(result).hasSize(1);
        
        NormalisedTransaction transaction = result.get(0);
        assertThat(transaction.sourceId()).isEqualTo("BF-12345");
        assertThat(transaction.sourceType()).isEqualTo(SourceType.BANK_FEED);
        assertThat(transaction.accountId()).isEqualTo("ACC-0001");
        assertThat(transaction.amount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(transaction.currency()).isEqualTo(Currency.getInstance("ZAR"));
        assertThat(transaction.merchantName()).isEqualTo("Test Merchant");
        assertThat(transaction.merchantMcc()).isNull(); // Bank feed doesn't provide MCC
        assertThat(transaction.status()).isEqualTo(TransactionStatus.SETTLED);
    }
    
    @Test
    void normalise_filtersDuplicates() throws JsonProcessingException {
        // Given
        BankFeedRecord duplicateRecord = new BankFeedRecord(
            "BF-12345", // Same ID
            "ACC-0001",
            "Another Merchant",
            new BigDecimal("200.00"),
            Currency.getInstance("USD"),
            Instant.now().minusSeconds(7200),
            "PENDING"
        );
        
        List<BankFeedRecord> records = List.of(sampleRecord, duplicateRecord);
        when(objectMapper.writeValueAsString(any(BankFeedRecord.class))).thenReturn("{}");
        
        // When - First call
        List<NormalisedTransaction> firstResult = bankFeedAdapter.normalise(records);
        
        // Then - Should only include first record (second is duplicate)
        assertThat(firstResult).hasSize(1);
        
        // When - Second call with same record
        List<NormalisedTransaction> secondResult = bankFeedAdapter.normalise(List.of(duplicateRecord));
        
        // Then - Should be empty (duplicate)
        assertThat(secondResult).isEmpty();
    }
    
    @Test
    void normalise_handlesJsonSerializationError() throws JsonProcessingException {
        // Given
        List<BankFeedRecord> records = List.of(sampleRecord);
        when(objectMapper.writeValueAsString(any(BankFeedRecord.class)))
            .thenThrow(new JsonProcessingException("JSON error") {});
        
        // When
        List<NormalisedTransaction> result = bankFeedAdapter.normalise(records);
        
        // Then - Should still return transaction with fallback JSON
        assertThat(result).hasSize(1);
        assertThat(result.get(0).rawPayload()).isEqualTo("{}");
    }

    @Test
    void constructor_throwsException_whenSourceIdIsBlank() {
        //Given,  When & Then
        assertThatThrownBy(() -> new NormalisedTransaction(
                "", // Blank sourceId
                SourceType.BANK_FEED,
                "ACC-0001",
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
    void constructor_throwsException_whenAmountIsNotPositive() {
        assertThatThrownBy(() ->
                new NormalisedTransaction(
                        "BF-12345",
                        SourceType.BANK_FEED,
                        "ACC-0001",
                        new BigDecimal("-10.00"), // Negative amount
                        Currency.getInstance("ZAR"),
                        "Test Merchant",
                        null,
                        Instant.now().minusSeconds(3600),
                        TransactionStatus.SETTLED,
                        "{}"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be positive");
    }

    @Test
    void constructor_throwsException_whenCurrencyIsNull() {
        //Given,  When & Then
        assertThatThrownBy(() -> new NormalisedTransaction(
                "BF-12345",
                SourceType.BANK_FEED,
                "ACC-0001",
                new BigDecimal("100.00"),
                null, // Null currency
                "Test Merchant",
                null,
                Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED,
                "{}"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency must not be null");
    }


    @Test
    void constructor_throwsException_whenTransactedAtIsNull() {
        assertThatThrownBy(() -> new NormalisedTransaction(
                "BF-12345",
                SourceType.BANK_FEED,
                "ACC-0001",
                new BigDecimal("100.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                null,
                null, // Null transactedAt
                TransactionStatus.SETTLED,
                "{}"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transactedAt must not be null");
    }

    
    @Test
    void validate_doesNotThrow_whenTransactionIsValid() {
        // Given
        NormalisedTransaction validTransaction = new NormalisedTransaction(
            "BF-12345",
            SourceType.BANK_FEED,
            "ACC-0001",
            new BigDecimal("100.00"),
            Currency.getInstance("ZAR"),
            "Test Merchant",
            null,
            Instant.now().minusSeconds(3600),
            TransactionStatus.SETTLED,
            "{}"
        );
        
        // When & Then - Should not throw
        bankFeedAdapter.validate(validTransaction);
    }
    
    @Test
    void isDuplicate_returnsTrue_whenSourceIdAlreadySeen() {
        // Given
        String sourceId = "BF-12345";
        
        // When - First check
        boolean firstCheck = bankFeedAdapter.isDuplicate(sourceId);
        
        // Then - Should be false (not a duplicate yet)
        assertThat(firstCheck).isFalse();
        
        // When - Second check
        boolean secondCheck = bankFeedAdapter.isDuplicate(sourceId);
        
        // Then - Should be true (duplicate)
        assertThat(secondCheck).isTrue();
    }
    
    @Test
    void isDuplicate_returnsFalse_whenSourceIdIsNew() {
        // Given
        String sourceId = "BF-NEW-123";
        
        // When
        boolean result = bankFeedAdapter.isDuplicate(sourceId);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void normalise_validatesEachTransaction() throws JsonProcessingException {
        // Given - Create a record that will produce invalid transaction
        // We can't easily test this without mocking the toNormalised method,
        // but the code path is that each transaction is validated via peek()
        when(objectMapper.writeValueAsString(any(BankFeedRecord.class))).thenReturn("{}");
        
        List<BankFeedRecord> records = List.of(sampleRecord);
        
        // When & Then - Should not throw (transaction is valid)
        List<NormalisedTransaction> result = bankFeedAdapter.normalise(records);
        assertThat(result).hasSize(1);
    }
}