package za.co.reed.mocksourceservice.bankfeed;

import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.mocksourceservice.adapter.BankFeedAdapter;
import za.co.reed.mocksourceservice.client.IngestorClient;
import za.co.reed.mocksourceservice.dto.BankFeedRecord;
import za.co.reed.mocksourceservice.generator.BankFeedDataGenerator;
import za.co.reed.mocksourceservice.properties.BankFeedProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.reed.mocksourceservice.scheduler.BankFeedScheduler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BankFeedScheduler}.
 */
@ExtendWith(MockitoExtension.class)
class BankFeedSchedulerTest {

    @Mock
    private BankFeedDataGenerator testGenerator;

    @Mock
    private BankFeedAdapter testAdapter;

    @Mock
    private BankFeedProperties testProperties;

    @Mock
    private IngestorClient testIngestorClient;

    private BankFeedScheduler testBankFeedScheduler;

    @BeforeEach
    void setUp() {
        testBankFeedScheduler = new BankFeedScheduler(testGenerator, testAdapter, testProperties, testIngestorClient);
    }

    @Test
    void poll_generatesAndSendsTransactions() {
        // Given
        when(testProperties.getAccountsPerBatch()).thenReturn(2);
        when(testProperties.getTransactionsPerAccount()).thenReturn(3);

        List<BankFeedRecord> testRawRecords = List.of(
                new BankFeedRecord("BF-1", "ACC-0001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        Instant.now().minusSeconds(3600), "SETTLED"),
                new BankFeedRecord("BF-2", "ACC-0001", "Merchant2",
                        new BigDecimal("200.00"), Currency.getInstance("USD"),
                        Instant.now().minusSeconds(7200), "PENDING")
        );

        List<NormalisedTransaction> testNormalisedTransactions = List.of(
                new NormalisedTransaction("BF-1", SourceType.BANK_FEED, "ACC-0001",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        "Merchant1", null, Instant.now().minusSeconds(3600),
                        TransactionStatus.SETTLED, "{}"),
                new NormalisedTransaction("BF-2", SourceType.BANK_FEED, "ACC-0001",
                        new BigDecimal("200.00"), Currency.getInstance("USD"),
                        "Merchant2", null, Instant.now().minusSeconds(7200),
                        TransactionStatus.PENDING, "{}")
        );

        when(testGenerator.generate(2, 3)).thenReturn(testRawRecords);
        when(testAdapter.normalise(testRawRecords)).thenReturn(testNormalisedTransactions);

        // When
        testBankFeedScheduler.poll();

        // Then
        verify(testGenerator).generate(2, 3);
        verify(testAdapter).normalise(testRawRecords);
        verify(testIngestorClient, times(2)).send(any(NormalisedTransaction.class));
        verify(testIngestorClient).send(testNormalisedTransactions.get(0));
        verify(testIngestorClient).send(testNormalisedTransactions.get(1));
    }

    @Test
    void poll_handlesEmptyBatch() {
        // Given
        when(testProperties.getAccountsPerBatch()).thenReturn(0);
        when(testProperties.getTransactionsPerAccount()).thenReturn(5);

        List<BankFeedRecord> testRawRecords = List.of();
        List<NormalisedTransaction> testNormalisedTransactions = List.of();

        when(testGenerator.generate(0, 5)).thenReturn(testRawRecords);
        when(testAdapter.normalise(testRawRecords)).thenReturn(testNormalisedTransactions);

        // When
        testBankFeedScheduler.poll();

        // Then
        verify(testGenerator).generate(0, 5);
        verify(testAdapter).normalise(testRawRecords);
        verify(testIngestorClient, never()).send(any(NormalisedTransaction.class));
    }

    @Test
    void poll_handlesDeduplication() {
        // Given
        when(testProperties.getAccountsPerBatch()).thenReturn(1);
        when(testProperties.getTransactionsPerAccount()).thenReturn(2);

        List<BankFeedRecord> testRawRecords = List.of(
                new BankFeedRecord("BF-1", "ACC-0001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        Instant.now().minusSeconds(3600), "SETTLED"),
                new BankFeedRecord("BF-2", "ACC-0001", "Merchant2",
                        new BigDecimal("200.00"), Currency.getInstance("USD"),
                        Instant.now().minusSeconds(7200), "PENDING")
        );

        List<NormalisedTransaction> testNormalisedTransactions = List.of(
                new NormalisedTransaction("BF-1", SourceType.BANK_FEED, "ACC-0001",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        "Merchant1", null, Instant.now().minusSeconds(3600),
                        TransactionStatus.SETTLED, "{}")
        );

        when(testGenerator.generate(1, 2)).thenReturn(testRawRecords);
        when(testAdapter.normalise(testRawRecords)).thenReturn(testNormalisedTransactions);

        // When
        testBankFeedScheduler.poll();

        // Then
        verify(testIngestorClient, times(1)).send(any(NormalisedTransaction.class));
    }

    @Test
    void poll_handlesIngestorClientException() {
        // Given
        when(testProperties.getAccountsPerBatch()).thenReturn(1);
        when(testProperties.getTransactionsPerAccount()).thenReturn(1);

        List<BankFeedRecord> testRawRecords = List.of(
                new BankFeedRecord("BF-1", "ACC-0001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        Instant.now().minusSeconds(3600), "SETTLED")
        );

        List<NormalisedTransaction> testNormalisedTransactions = List.of(
                new NormalisedTransaction("BF-1", SourceType.BANK_FEED, "ACC-0001",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        "Merchant1", null, Instant.now().minusSeconds(3600),
                        TransactionStatus.SETTLED, "{}")
        );

        when(testGenerator.generate(1, 1)).thenReturn(testRawRecords);
        when(testAdapter.normalise(testRawRecords)).thenReturn(testNormalisedTransactions);

        doThrow(new RuntimeException("Network error")).when(testIngestorClient).send(any(NormalisedTransaction.class));

        // When
        testBankFeedScheduler.poll();

        // Then
        verify(testGenerator).generate(1, 1);
        verify(testAdapter).normalise(testRawRecords);
        verify(testIngestorClient).send(any(NormalisedTransaction.class));
    }

    @Test
    void poll_logsCorrectStatistics() {
        // Given
        when(testProperties.getAccountsPerBatch()).thenReturn(2);
        when(testProperties.getTransactionsPerAccount()).thenReturn(3);

        List<BankFeedRecord> testRawRecords = List.of(
                new BankFeedRecord("BF-1", "ACC-0001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        Instant.now().minusSeconds(3600), "SETTLED"),
                new BankFeedRecord("BF-2", "ACC-0001", "Merchant2",
                        new BigDecimal("200.00"), Currency.getInstance("USD"),
                        Instant.now().minusSeconds(7200), "PENDING"),
                new BankFeedRecord("BF-3", "ACC-0002", "Merchant3",
                        new BigDecimal("300.00"), Currency.getInstance("GBP"),
                        Instant.now().minusSeconds(10800), "SETTLED"),
                new BankFeedRecord("BF-4", "ACC-0002", "Merchant4",
                        new BigDecimal("400.00"), Currency.getInstance("EUR"),
                        Instant.now().minusSeconds(14400), "PENDING"),
                new BankFeedRecord("BF-5", "ACC-0003", "Merchant5",
                        new BigDecimal("500.00"), Currency.getInstance("ZAR"),
                        Instant.now().minusSeconds(18000), "SETTLED"),
                new BankFeedRecord("BF-6", "ACC-0003", "Merchant6",
                        new BigDecimal("600.00"), Currency.getInstance("USD"),
                        Instant.now().minusSeconds(21600), "REVERSED")
        );

        List<NormalisedTransaction> testNormalisedTransactions = List.of(
                new NormalisedTransaction("BF-1", SourceType.BANK_FEED, "ACC-0001",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        "Merchant1", null, Instant.now().minusSeconds(3600),
                        TransactionStatus.SETTLED, "{}"),
                new NormalisedTransaction("BF-2", SourceType.BANK_FEED, "ACC-0001",
                        new BigDecimal("200.00"), Currency.getInstance("USD"),
                        "Merchant2", null, Instant.now().minusSeconds(7200),
                        TransactionStatus.PENDING, "{}"),
                new NormalisedTransaction("BF-3", SourceType.BANK_FEED, "ACC-0002",
                        new BigDecimal("300.00"), Currency.getInstance("GBP"),
                        "Merchant3", null, Instant.now().minusSeconds(10800),
                        TransactionStatus.SETTLED, "{}"),
                new NormalisedTransaction("BF-4", SourceType.BANK_FEED, "ACC-0002",
                        new BigDecimal("400.00"), Currency.getInstance("EUR"),
                        "Merchant4", null, Instant.now().minusSeconds(14400),
                        TransactionStatus.PENDING, "{}")
        );

        when(testGenerator.generate(2, 3)).thenReturn(testRawRecords);
        when(testAdapter.normalise(testRawRecords)).thenReturn(testNormalisedTransactions);

        // When
        testBankFeedScheduler.poll();

        // Then
        verify(testGenerator).generate(2, 3);
        verify(testAdapter).normalise(testRawRecords);
        verify(testIngestorClient, times(4)).send(any(NormalisedTransaction.class));
    }

    @Test
    void poll_usesPropertiesValues() {
        // Given
        when(testProperties.getAccountsPerBatch()).thenReturn(5);
        when(testProperties.getTransactionsPerAccount()).thenReturn(10);

        List<BankFeedRecord> testRawRecords = List.of();
        List<NormalisedTransaction> testNormalisedTransactions = List.of();

        when(testGenerator.generate(5, 10)).thenReturn(testRawRecords);
        when(testAdapter.normalise(testRawRecords)).thenReturn(testNormalisedTransactions);

        // When
        testBankFeedScheduler.poll();

        // Then
        verify(testGenerator).generate(5, 10);
        verify(testAdapter).normalise(testRawRecords);
    }
}