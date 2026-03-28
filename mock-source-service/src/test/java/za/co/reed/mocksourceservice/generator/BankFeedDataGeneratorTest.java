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

    private final BankFeedDataGenerator testGenerator = new BankFeedDataGenerator();

    @Test
    void generate_returnsCorrectNumberOfRecords() {
        // Given
        int testAccounts = 3;
        int testTransactionsPerAccount = 5;

        // When
        List<BankFeedRecord> testRecords = testGenerator.generate(testAccounts, testTransactionsPerAccount);

        // Then
        assertThat(testRecords).hasSize(testAccounts * testTransactionsPerAccount);
    }

    @Test
    void generate_returnsEmptyList_whenZeroAccounts() {
        // Given
        int testAccounts = 0;
        int testTransactionsPerAccount = 5;

        // When
        List<BankFeedRecord> testRecords = testGenerator.generate(testAccounts, testTransactionsPerAccount);

        // Then
        assertThat(testRecords).isEmpty();
    }

    @Test
    void generate_returnsEmptyList_whenZeroTransactionsPerAccount() {
        // Given
        int testAccounts = 3;
        int testTransactionsPerAccount = 0;

        // When
        List<BankFeedRecord> testRecords = testGenerator.generate(testAccounts, testTransactionsPerAccount);

        // Then
        assertThat(testRecords).isEmpty();
    }

    @Test
    void generate_recordsHaveValidFields() {
        // Given
        int testAccounts = 2;
        int testTransactionsPerAccount = 3;

        // When
        List<BankFeedRecord> testRecords = testGenerator.generate(testAccounts, testTransactionsPerAccount);

        // Then
        for (BankFeedRecord testRecord : testRecords) {
            assertThat(testRecord.transactionId()).startsWith("BF-");
            assertThat(testRecord.transactionId()).hasSizeGreaterThan(3);

            assertThat(testRecord.accountId()).matches("ACC-\\d{4}");

            assertThat(testRecord.merchantName()).isNotBlank();

            assertThat(testRecord.amount()).isPositive();
            assertThat(testRecord.amount().scale()).isEqualTo(2);

            assertThat(testRecord.currency()).isIn(
                    Currency.getInstance("ZAR"),
                    Currency.getInstance("USD"),
                    Currency.getInstance("GBP"),
                    Currency.getInstance("EUR")
            );

            assertThat(testRecord.transactedAt()).isBeforeOrEqualTo(Instant.now());
            assertThat(testRecord.transactedAt()).isAfter(Instant.now().minusSeconds(86400));

            assertThat(testRecord.status()).isIn("SETTLED", "PENDING", "REVERSED");
        }
    }

    @Test
    void generate_accountIdsAreDistributed() {
        // Given
        int testAccounts = 15;
        int testTransactionsPerAccount = 2;

        // When
        List<BankFeedRecord> testRecords = testGenerator.generate(testAccounts, testTransactionsPerAccount);

        // Then
        assertThat(testRecords).hasSize(testAccounts * testTransactionsPerAccount);

        List<String> testValidAccountIds = List.of(
                "ACC-0001", "ACC-0002", "ACC-0003", "ACC-0004", "ACC-0005",
                "ACC-0006", "ACC-0007", "ACC-0008", "ACC-0009", "ACC-0010"
        );

        for (BankFeedRecord testRecord : testRecords) {
            assertThat(testValidAccountIds).contains(testRecord.accountId());
        }
    }

    @Test
    void generate_amountsAreWithinRange() {
        // Given
        int testAccounts = 1;
        int testTransactionsPerAccount = 100;

        // When
        List<BankFeedRecord> testRecords = testGenerator.generate(testAccounts, testTransactionsPerAccount);

        // Then
        for (BankFeedRecord testRecord : testRecords) {
            assertThat(testRecord.amount()).isBetween(
                    BigDecimal.valueOf(1.0),
                    BigDecimal.valueOf(5000.0)
            );
        }
    }

    @Test
    void generate_statusDistributionIsReasonable() {
        // Given
        int testAccounts = 1;
        int testTransactionsPerAccount = 1000;

        // When
        List<BankFeedRecord> testRecords = testGenerator.generate(testAccounts, testTransactionsPerAccount);

        // Then
        long testSettledCount = testRecords.stream()
                .filter(r -> "SETTLED".equals(r.status()))
                .count();

        long testPendingCount = testRecords.stream()
                .filter(r -> "PENDING".equals(r.status()))
                .count();

        long testReversedCount = testRecords.stream()
                .filter(r -> "REVERSED".equals(r.status()))
                .count();

        assertThat(testSettledCount).isGreaterThan(testRecords.size() * 60 / 100);
        assertThat(testSettledCount).isLessThan(testRecords.size() * 80 / 100);

        assertThat(testPendingCount).isGreaterThan(testRecords.size() * 10 / 100);
        assertThat(testPendingCount).isLessThan(testRecords.size() * 30 / 100);

        assertThat(testReversedCount).isGreaterThan(testRecords.size() * 5 / 100);
        assertThat(testReversedCount).isLessThan(testRecords.size() * 15 / 100);
    }

    @Test
    void generate_currenciesAreFromValidSet() {
        // Given
        int testAccounts = 1;
        int testTransactionsPerAccount = 100;

        // When
        List<BankFeedRecord> testRecords = testGenerator.generate(testAccounts, testTransactionsPerAccount);

        // Then
        for (BankFeedRecord testRecord : testRecords) {
            String testCurrencyCode = testRecord.currency().getCurrencyCode();
            assertThat(testCurrencyCode).isIn("ZAR", "USD", "GBP", "EUR");
        }
    }

    @Test
    void generate_transactionIdsAreUnique() {
        // Given
        int testAccounts = 1;
        int testTransactionsPerAccount = 50;

        // When
        List<BankFeedRecord> testRecords = testGenerator.generate(testAccounts, testTransactionsPerAccount);

        // Then
        long testUniqueIds = testRecords.stream()
                .map(BankFeedRecord::transactionId)
                .distinct()
                .count();

        assertThat(testUniqueIds).isEqualTo(testRecords.size());
    }
}