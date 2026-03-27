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
    private BankFeedDataGenerator generator;
    
    @Mock
    private BankFeedAdapter adapter;
    
    @Mock
    private BankFeedProperties properties;
    
    @Mock
    private IngestorClient ingestorClient;
    
    private BankFeedScheduler bankFeedScheduler;
    
    @BeforeEach
    void setUp() {
        bankFeedScheduler = new BankFeedScheduler(generator, adapter, properties, ingestorClient);
    }
    
    @Test
    void poll_generatesAndSendsTransactions() {
        // Given
        when(properties.getAccountsPerBatch()).thenReturn(2);
        when(properties.getTransactionsPerAccount()).thenReturn(3);
        
        List<BankFeedRecord> rawRecords = List.of(
            new BankFeedRecord("BF-1", "ACC-0001", "Merchant1", 
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                Instant.now().minusSeconds(3600), "SETTLED"),
            new BankFeedRecord("BF-2", "ACC-0001", "Merchant2",
                new BigDecimal("200.00"), Currency.getInstance("USD"),
                Instant.now().minusSeconds(7200), "PENDING")
        );
        
        List<NormalisedTransaction> normalisedTransactions = List.of(
            new NormalisedTransaction("BF-1", SourceType.BANK_FEED, "ACC-0001",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                "Merchant1", null, Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED, "{}"),
            new NormalisedTransaction("BF-2", SourceType.BANK_FEED, "ACC-0001",
                new BigDecimal("200.00"), Currency.getInstance("USD"),
                "Merchant2", null, Instant.now().minusSeconds(7200),
                TransactionStatus.PENDING, "{}")
        );
        
        when(generator.generate(2, 3)).thenReturn(rawRecords);
        when(adapter.normalise(rawRecords)).thenReturn(normalisedTransactions);
        
        // When
        bankFeedScheduler.poll();
        
        // Then
        verify(generator).generate(2, 3);
        verify(adapter).normalise(rawRecords);
        verify(ingestorClient, times(2)).send(any(NormalisedTransaction.class));
        verify(ingestorClient).send(normalisedTransactions.get(0));
        verify(ingestorClient).send(normalisedTransactions.get(1));
    }
    
    @Test
    void poll_handlesEmptyBatch() {
        // Given
        when(properties.getAccountsPerBatch()).thenReturn(0);
        when(properties.getTransactionsPerAccount()).thenReturn(5);
        
        List<BankFeedRecord> rawRecords = List.of();
        List<NormalisedTransaction> normalisedTransactions = List.of();
        
        when(generator.generate(0, 5)).thenReturn(rawRecords);
        when(adapter.normalise(rawRecords)).thenReturn(normalisedTransactions);
        
        // When
        bankFeedScheduler.poll();
        
        // Then
        verify(generator).generate(0, 5);
        verify(adapter).normalise(rawRecords);
        verify(ingestorClient, never()).send(any(NormalisedTransaction.class));
    }
    
    @Test
    void poll_handlesDeduplication() {
        // Given
        when(properties.getAccountsPerBatch()).thenReturn(1);
        when(properties.getTransactionsPerAccount()).thenReturn(2);
        
        List<BankFeedRecord> rawRecords = List.of(
            new BankFeedRecord("BF-1", "ACC-0001", "Merchant1", 
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                Instant.now().minusSeconds(3600), "SETTLED"),
            new BankFeedRecord("BF-2", "ACC-0001", "Merchant2",
                new BigDecimal("200.00"), Currency.getInstance("USD"),
                Instant.now().minusSeconds(7200), "PENDING")
        );
        
        // Simulate adapter filtering out duplicates
        List<NormalisedTransaction> normalisedTransactions = List.of(
            new NormalisedTransaction("BF-1", SourceType.BANK_FEED, "ACC-0001",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                "Merchant1", null, Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED, "{}")
        );
        
        when(generator.generate(1, 2)).thenReturn(rawRecords);
        when(adapter.normalise(rawRecords)).thenReturn(normalisedTransactions);
        
        // When
        bankFeedScheduler.poll();
        
        // Then - Should only send the one non-duplicate transaction
        verify(ingestorClient, times(1)).send(any(NormalisedTransaction.class));
    }
    
    @Test
    void poll_handlesIngestorClientException() {
        // Given
        when(properties.getAccountsPerBatch()).thenReturn(1);
        when(properties.getTransactionsPerAccount()).thenReturn(1);
        
        List<BankFeedRecord> rawRecords = List.of(
            new BankFeedRecord("BF-1", "ACC-0001", "Merchant1", 
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                Instant.now().minusSeconds(3600), "SETTLED")
        );
        
        List<NormalisedTransaction> normalisedTransactions = List.of(
            new NormalisedTransaction("BF-1", SourceType.BANK_FEED, "ACC-0001",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                "Merchant1", null, Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED, "{}")
        );
        
        when(generator.generate(1, 1)).thenReturn(rawRecords);
        when(adapter.normalise(rawRecords)).thenReturn(normalisedTransactions);
        
        // Simulate ingestor client throwing exception
        doThrow(new RuntimeException("Network error")).when(ingestorClient).send(any(NormalisedTransaction.class));
        
        // When - Should not throw exception
        bankFeedScheduler.poll();
        
        // Then - Exception should be caught and logged, but poll should complete
        verify(generator).generate(1, 1);
        verify(adapter).normalise(rawRecords);
        verify(ingestorClient).send(any(NormalisedTransaction.class));
    }
    
    @Test
    void poll_logsCorrectStatistics() {
        // Given
        when(properties.getAccountsPerBatch()).thenReturn(2);
        when(properties.getTransactionsPerAccount()).thenReturn(3);
        
        // Generate 6 raw records, but adapter filters 2 as duplicates
        List<BankFeedRecord> rawRecords = List.of(
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
        
        // Adapter returns 4 transactions (filtering 2 duplicates)
        List<NormalisedTransaction> normalisedTransactions = List.of(
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
        
        when(generator.generate(2, 3)).thenReturn(rawRecords);
        when(adapter.normalise(rawRecords)).thenReturn(normalisedTransactions);
        
        // When
        bankFeedScheduler.poll();
        
        // Then - Should log statistics: 4 transactions (6 raw, 2 deduped out)
        verify(generator).generate(2, 3);
        verify(adapter).normalise(rawRecords);
        verify(ingestorClient, times(4)).send(any(NormalisedTransaction.class));
    }
    
    @Test
    void poll_usesPropertiesValues() {
        // Given
        when(properties.getAccountsPerBatch()).thenReturn(5);
        when(properties.getTransactionsPerAccount()).thenReturn(10);
        
        List<BankFeedRecord> rawRecords = List.of();
        List<NormalisedTransaction> normalisedTransactions = List.of();
        
        when(generator.generate(5, 10)).thenReturn(rawRecords);
        when(adapter.normalise(rawRecords)).thenReturn(normalisedTransactions);
        
        // When
        bankFeedScheduler.poll();
        
        // Then
        verify(generator).generate(5, 10);
        verify(adapter).normalise(rawRecords);
    }
}