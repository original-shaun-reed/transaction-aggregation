package za.co.reed.mocksourceservice.generator;

import za.co.reed.mocksourceservice.dto.BankFeedRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BankFeedDataGenerator}.
 */
class BankFeedDataGeneratorTest {

    private final BankFeedDataGenerator generator = new BankFeedDataGenerator();

    @Test
    void generate_returnsCorrectNumberOfRecords() {
        // Given
        int accounts = 3;
        int transactionsPerAccount = 5;
        
        // When
        List<BankFeedRecord> records = generator.generate(accounts, transactionsPerAccount);
        
        // Then
        assertThat(records).hasSize(accounts * transactionsPerAccount);
    }
    
    @Test
    void generate_returnsEmptyList_whenZeroAccounts() {
        // Given
        int accounts = 0;
        int transactionsPerAccount = 5;
        
        // When
        List<BankFeedRecord> records = generator.generate(accounts, transactionsPerAccount);
        
        // Then
        assertThat(records).isEmpty();
    }
    
    @Test
    void generate_returnsEmptyList_whenZeroTransactionsPerAccount() {
        // Given
        int accounts = 3;
        int transactionsPerAccount = 0;
        
        // When
        List<BankFeedRecord> records = generator.generate(accounts, transactionsPerAccount);
        
        // Then
        assertThat(records).isEmpty();
    }
    
    @Test
    void generate_recordsHaveValidFields() {
        // Given
        int accounts = 2;
        int transactionsPerAccount = 3;
        
        // When
        List<BankFeedRecord> records = generator.generate(accounts, transactionsPerAccount);
        
        // Then
        for (BankFeedRecord record : records) {
            assertThat(record.transactionId()).startsWith("BF-");
            assertThat(record.transactionId()).hasSizeGreaterThan(3);
            
            assertThat(record.accountId()).matches("ACC-\\d{4}");
            
            assertThat(record.merchantName()).isNotBlank();
            
            assertThat(record.amount()).isPositive();
            assertThat(record.amount().scale()).isEqualTo(2); // 2 decimal places
            
            assertThat(record.currency()).isIn(
                Currency.getInstance("ZAR"),
                Currency.getInstance("USD"),
                Currency.getInstance("GBP"),
                Currency.getInstance("EUR")
            );
            
            assertThat(record.transactedAt()).isBeforeOrEqualTo(Instant.now());
            // Should be within last 24 hours (jitter up to 86,400 seconds)
            assertThat(record.transactedAt()).isAfter(Instant.now().minusSeconds(86400));
            
            assertThat(record.status()).isIn("SETTLED", "PENDING", "REVERSED");
        }
    }
    
    @Test
    void generate_accountIdsAreDistributed() {
        // Given
        int accounts = 15; // More than the 10 available account IDs
        int transactionsPerAccount = 2;
        
        // When
        List<BankFeedRecord> records = generator.generate(accounts, transactionsPerAccount);
        
        // Then - Should still generate records (account IDs wrap around)
        assertThat(records).hasSize(accounts * transactionsPerAccount);
        
        // Verify all account IDs are from the predefined list
        List<String> validAccountIds = List.of(
            "ACC-0001", "ACC-0002", "ACC-0003", "ACC-0004", "ACC-0005",
            "ACC-0006", "ACC-0007", "ACC-0008", "ACC-0009", "ACC-0010"
        );
        
        for (BankFeedRecord record : records) {
            assertThat(validAccountIds).contains(record.accountId());
        }
    }
    
    @Test
    void generate_amountsAreWithinRange() {
        // Given
        int accounts = 1;
        int transactionsPerAccount = 100; // Generate many to test distribution
        
        // When
        List<BankFeedRecord> records = generator.generate(accounts, transactionsPerAccount);
        
        // Then
        for (BankFeedRecord record : records) {
            assertThat(record.amount()).isBetween(
                BigDecimal.valueOf(1.0),
                BigDecimal.valueOf(5000.0)
            );
        }
    }
    
    @Test
    void generate_statusDistributionIsReasonable() {
        // Given
        int accounts = 1;
        int transactionsPerAccount = 1000; // Large sample to test distribution
        
        // When
        List<BankFeedRecord> records = generator.generate(accounts, transactionsPerAccount);
        
        // Then
        long settledCount = records.stream()
            .filter(r -> "SETTLED".equals(r.status()))
            .count();
        
        long pendingCount = records.stream()
            .filter(r -> "PENDING".equals(r.status()))
            .count();
        
        long reversedCount = records.stream()
            .filter(r -> "REVERSED".equals(r.status()))
            .count();
        
        // Rough distribution check (70% settled, 20% pending, 10% reversed)
        // Allow some variance due to randomness
        assertThat(settledCount).isGreaterThan(records.size() * 60 / 100); // >60%
        assertThat(settledCount).isLessThan(records.size() * 80 / 100);    // <80%
        
        assertThat(pendingCount).isGreaterThan(records.size() * 10 / 100); // >10%
        assertThat(pendingCount).isLessThan(records.size() * 30 / 100);    // <30%
        
        assertThat(reversedCount).isGreaterThan(records.size() * 5 / 100);  // >5%
        assertThat(reversedCount).isLessThan(records.size() * 15 / 100);    // <15%
    }
    
    @Test
    void generate_currenciesAreFromValidSet() {
        // Given
        int accounts = 1;
        int transactionsPerAccount = 100;
        
        // When
        List<BankFeedRecord> records = generator.generate(accounts, transactionsPerAccount);
        
        // Then
        for (BankFeedRecord record : records) {
            String currencyCode = record.currency().getCurrencyCode();
            assertThat(currencyCode).isIn("ZAR", "USD", "GBP", "EUR");
        }
    }
    
    @Test
    void generate_transactionIdsAreUnique() {
        // Given
        int accounts = 1;
        int transactionsPerAccount = 50;
        
        // When
        List<BankFeedRecord> records = generator.generate(accounts, transactionsPerAccount);
        
        // Then
        long uniqueIds = records.stream()
            .map(BankFeedRecord::transactionId)
            .distinct()
            .count();
        
        assertThat(uniqueIds).isEqualTo(records.size());
    }
}