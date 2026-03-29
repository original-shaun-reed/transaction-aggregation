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
    private ObjectMapper testObjectMapper;

    private BankFeedAdapter testBankFeedAdapter;

    private BankFeedRecord testSampleRecord;

    @BeforeEach
    void setUp() {
        testBankFeedAdapter = new BankFeedAdapter(testObjectMapper);

        testSampleRecord = new BankFeedRecord(
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
        Object testInvalidPayload = "not a list";

        List<NormalisedTransaction> testResult = testBankFeedAdapter.normalise(testInvalidPayload);

        assertThat(testResult).isEmpty();
    }

    @Test
    void normalise_returnsNormalisedTransactions_whenPayloadIsValid() throws JsonProcessingException {
        List<BankFeedRecord> testRecords = List.of(testSampleRecord);
        when(testObjectMapper.writeValueAsString(any(BankFeedRecord.class))).thenReturn("{\"raw\": \"payload\"}");

        List<NormalisedTransaction> testResult = testBankFeedAdapter.normalise(testRecords);

        assertThat(testResult).hasSize(1);

        NormalisedTransaction testTransaction = testResult.get(0);
        assertThat(testTransaction.sourceId()).isEqualTo("BF-12345");
        assertThat(testTransaction.sourceType()).isEqualTo(SourceType.BANK_FEED);
        assertThat(testTransaction.accountId()).isEqualTo("ACC-0001");
        assertThat(testTransaction.amount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(testTransaction.currency()).isEqualTo(Currency.getInstance("ZAR"));
        assertThat(testTransaction.merchantName()).isEqualTo("Test Merchant");
        assertThat(testTransaction.merchantMcc()).isNull();
        assertThat(testTransaction.status()).isEqualTo(TransactionStatus.SETTLED);
    }

    @Test
    void normalise_filtersDuplicates() throws JsonProcessingException {
        BankFeedRecord testDuplicateRecord = new BankFeedRecord(
                "BF-12345",
                "ACC-0001",
                "Another Merchant",
                new BigDecimal("200.00"),
                Currency.getInstance("USD"),
                Instant.now().minusSeconds(7200),
                "PENDING"
        );

        List<BankFeedRecord> testRecords = List.of(testSampleRecord, testDuplicateRecord);
        when(testObjectMapper.writeValueAsString(any(BankFeedRecord.class))).thenReturn("{}");

        List<NormalisedTransaction> testFirstResult = testBankFeedAdapter.normalise(testRecords);
        assertThat(testFirstResult).hasSize(1);

        List<NormalisedTransaction> testSecondResult = testBankFeedAdapter.normalise(List.of(testDuplicateRecord));
        assertThat(testSecondResult).isEmpty();
    }

    @Test
    void normalise_handlesJsonSerializationError() throws JsonProcessingException {
        List<BankFeedRecord> testRecords = List.of(testSampleRecord);
        when(testObjectMapper.writeValueAsString(any(BankFeedRecord.class)))
                .thenThrow(new JsonProcessingException("JSON error") {});

        List<NormalisedTransaction> testResult = testBankFeedAdapter.normalise(testRecords);

        assertThat(testResult).hasSize(1);
        assertThat(testResult.get(0).rawPayload()).isEqualTo("{}");
    }

    @Test
    void constructor_throwsException_whenSourceIdIsBlank() {
        assertThatThrownBy(() -> new NormalisedTransaction(
                "",
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
                        new BigDecimal("-10.00"),
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
        assertThatThrownBy(() -> new NormalisedTransaction(
                "BF-12345",
                SourceType.BANK_FEED,
                "ACC-0001",
                new BigDecimal("100.00"),
                null,
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
                null,
                TransactionStatus.SETTLED,
                "{}"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transactedAt must not be null");
    }

    @Test
    void validate_doesNotThrow_whenTransactionIsValid() {
        NormalisedTransaction testValidTransaction = new NormalisedTransaction(
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

        testBankFeedAdapter.validate(testValidTransaction);
    }

    @Test
    void isDuplicate_returnsTrue_whenSourceIdAlreadySeen() {
        String testSourceId = "BF-12345";

        boolean testFirstCheck = testBankFeedAdapter.isDuplicate(testSourceId);
        assertThat(testFirstCheck).isFalse();

        boolean testSecondCheck = testBankFeedAdapter.isDuplicate(testSourceId);
        assertThat(testSecondCheck).isTrue();
    }

    @Test
    void isDuplicate_returnsFalse_whenSourceIdIsNew() {
        String testSourceId = "BF-NEW-123";

        boolean testResult = testBankFeedAdapter.isDuplicate(testSourceId);
        assertThat(testResult).isFalse();
    }

    @Test
    void normalise_validatesEachTransaction() throws JsonProcessingException {
        when(testObjectMapper.writeValueAsString(any(BankFeedRecord.class))).thenReturn("{}");

        List<BankFeedRecord> testRecords = List.of(testSampleRecord);

        List<NormalisedTransaction> testResult = testBankFeedAdapter.normalise(testRecords);
        assertThat(testResult).hasSize(1);
    }
}
