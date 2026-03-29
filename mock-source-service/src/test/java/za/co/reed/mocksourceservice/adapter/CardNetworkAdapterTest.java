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
    private ObjectMapper testObjectMapper;

    private CardNetworkAdapter testCardNetworkAdapter;

    private CardRecord testSampleRecord;

    @BeforeEach
    void setUp() {
        testCardNetworkAdapter = new CardNetworkAdapter(testObjectMapper);

        testSampleRecord = new CardRecord(
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
        Object testInvalidPayload = "not a list";

        // When
        List<NormalisedTransaction> testResult = testCardNetworkAdapter.normalise(testInvalidPayload);

        // Then
        assertThat(testResult).isEmpty();
    }

    @Test
    void normalise_returnsNormalisedTransactions_whenPayloadIsValid() throws JsonProcessingException {
        // Given
        List<CardRecord> testRecords = List.of(testSampleRecord);
        when(testObjectMapper.writeValueAsString(any(CardRecord.class))).thenReturn("{\"raw\": \"payload\"}");

        // When
        List<NormalisedTransaction> testResult = testCardNetworkAdapter.normalise(testRecords);

        // Then
        assertThat(testResult).hasSize(1);

        NormalisedTransaction testTransaction = testResult.get(0);
        assertThat(testTransaction.sourceId()).isEqualTo("CN-12345");
        assertThat(testTransaction.sourceType()).isEqualTo(SourceType.CARD_NETWORK);
        assertThat(testTransaction.accountId()).isEqualTo("1234");
        assertThat(testTransaction.amount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(testTransaction.currency()).isEqualTo(Currency.getInstance("ZAR"));
        assertThat(testTransaction.merchantName()).isEqualTo("Test Merchant");
        assertThat(testTransaction.merchantMcc()).isEqualTo("5411");
        assertThat(testTransaction.status()).isEqualTo(TransactionStatus.SETTLED);
    }

    @Test
    void normalise_filtersDuplicates() throws JsonProcessingException {
        // Given
        CardRecord testDuplicateRecord = new CardRecord(
                "CN-12345",
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

        List<CardRecord> testRecords = List.of(testSampleRecord, testDuplicateRecord);
        when(testObjectMapper.writeValueAsString(any(CardRecord.class))).thenReturn("{}");

        // When - First call
        List<NormalisedTransaction> testFirstResult = testCardNetworkAdapter.normalise(testRecords);

        // Then - Should only include first record (second is duplicate)
        assertThat(testFirstResult).hasSize(1);

        // When - Second call with same record
        List<NormalisedTransaction> testSecondResult = testCardNetworkAdapter.normalise(List.of(testDuplicateRecord));

        // Then - Should be empty (duplicate)
        assertThat(testSecondResult).isEmpty();
    }

    @Test
    void normalise_handlesJsonSerializationError() throws JsonProcessingException {
        // Given
        List<CardRecord> testRecords = List.of(testSampleRecord);
        when(testObjectMapper.writeValueAsString(any(CardRecord.class)))
                .thenThrow(new JsonProcessingException("JSON error") {});

        // When
        List<NormalisedTransaction> testResult = testCardNetworkAdapter.normalise(testRecords);

        // Then - Should still return transaction with fallback JSON
        assertThat(testResult).hasSize(1);
        assertThat(testResult.get(0).rawPayload()).isEqualTo("{}");
    }

    @Test
    void validate_throwsException_whenSourceIdIsBlank() {
        // Given, When & Then
        assertThatThrownBy(() -> new NormalisedTransaction(
                "",
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
                new BigDecimal("-10.00"),
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
        NormalisedTransaction testInvalidTransaction = new NormalisedTransaction(
                "CN-12345",
                SourceType.CARD_NETWORK,
                "1234",
                new BigDecimal("100.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "",
                Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED,
                "{}"
        );

        // When & Then
        assertThatThrownBy(() -> testCardNetworkAdapter.validate(testInvalidTransaction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("merchantMcc must not be blank for card network records");
    }

    @Test
    void validate_doesNotThrow_whenTransactionIsValid() {
        // Given
        NormalisedTransaction testValidTransaction = new NormalisedTransaction(
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
        testCardNetworkAdapter.validate(testValidTransaction);
    }

    @Test
    void isDuplicate_returnsTrue_whenSourceIdAlreadySeen() {
        // Given
        String testSourceId = "CN-12345";

        // When - First check
        boolean testFirstCheck = testCardNetworkAdapter.isDuplicate(testSourceId);

        // Then - Should be false (not a duplicate yet)
        assertThat(testFirstCheck).isFalse();

        // When - Second check
        boolean testSecondCheck = testCardNetworkAdapter.isDuplicate(testSourceId);

        // Then - Should be true (duplicate)
        assertThat(testSecondCheck).isTrue();
    }

    @Test
    void isDuplicate_returnsFalse_whenSourceIdIsNew() {
        // Given
        String testSourceId = "CN-NEW-123";

        // When
        boolean testResult = testCardNetworkAdapter.isDuplicate(testSourceId);

        // Then
        assertThat(testResult).isFalse();
    }

    @Test
    void mapStatus_returnsCorrectStatus() {
        // Given
        CardRecord testClearedRecord = new CardRecord(
                "CN-1", "AUTH1", "1234", "Merchant", "City", "5411",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800), "CLEARED"
        );

        CardRecord testAuthRecord = new CardRecord(
                "CN-2", "AUTH2", "1234", "Merchant", "City", "5411",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                Instant.now().minusSeconds(3600), null, "AUTH"
        );

        CardRecord testReversedRecord = new CardRecord(
                "CN-3", "AUTH3", "1234", "Merchant", "City", "5411",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                Instant.now().minusSeconds(3600), null, "REVERSED"
        );

        CardRecord testUnknownRecord = new CardRecord(
                "CN-4", "AUTH4", "1234", "Merchant", "City", "5411",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                Instant.now().minusSeconds(3600), null, "UNKNOWN"
        );

        try {
            when(testObjectMapper.writeValueAsString(any(CardRecord.class))).thenReturn("{}");
        } catch (JsonProcessingException e) {
            // Ignore for test
        }

        // When
        List<CardRecord> testRecords = List.of(testClearedRecord, testAuthRecord, testReversedRecord, testUnknownRecord);
        List<NormalisedTransaction> testResult = testCardNetworkAdapter.normalise(testRecords);

        // Then
        assertThat(testResult).hasSize(4);
        assertThat(testResult.get(0).status()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(testResult.get(1).status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(testResult.get(2).status()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(testResult.get(3).status()).isEqualTo(TransactionStatus.PENDING); // default for unknown
    }

    @Test
    void toNormalised_usesClearedAtWhenAvailable() throws JsonProcessingException {
        // Given
        when(testObjectMapper.writeValueAsString(any(CardRecord.class))).thenReturn("{}");

        // When
        List<NormalisedTransaction> testResult = testCardNetworkAdapter.normalise(List.of(testSampleRecord));

        // Then
        assertThat(testResult).hasSize(1);
        // transactedAt should be clearedAt (not authorisedAt) because record has clearedAt
        assertThat(testResult.get(0).transactedAt()).isEqualTo(testSampleRecord.clearedAt());
    }

    @Test
    void toNormalised_usesAuthorisedAtWhenClearedAtIsNull() throws JsonProcessingException {
        // Given
        CardRecord testAuthOnlyRecord = new CardRecord(
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

        when(testObjectMapper.writeValueAsString(any(CardRecord.class))).thenReturn("{}");

        // When
        List<NormalisedTransaction> testResult = testCardNetworkAdapter.normalise(List.of(testAuthOnlyRecord));

        // Then
        assertThat(testResult).hasSize(1);
        // transactedAt should be authorisedAt because clearedAt is null
        assertThat(testResult.get(0).transactedAt()).isEqualTo(testAuthOnlyRecord.authorisedAt());
    }
}