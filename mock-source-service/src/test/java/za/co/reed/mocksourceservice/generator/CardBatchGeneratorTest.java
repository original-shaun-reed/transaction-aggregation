package za.co.reed.mocksourceservice.generator;

import za.co.reed.mocksourceservice.dto.CardRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CardBatchGenerator}.
 */
class CardBatchGeneratorTest {

    private final CardBatchGenerator testGenerator = new CardBatchGenerator();

    @Test
    void generate_returnsCorrectNumberOfRecords() {
        // Given
        int testCount = 10;

        // When
        List<CardRecord> testRecords = testGenerator.generate(testCount);

        // Then
        assertThat(testRecords).hasSize(testCount);
    }

    @Test
    void generate_returnsEmptyList_whenZeroCount() {
        // Given
        int testCount = 0;

        // When
        List<CardRecord> testRecords = testGenerator.generate(testCount);

        // Then
        assertThat(testRecords).isEmpty();
    }

    @Test
    void generate_recordsHaveValidFields() {
        // Given
        int testCount = 5;

        // When
        List<CardRecord> testRecords = testGenerator.generate(testCount);

        // Then
        for (CardRecord testRecord : testRecords) {
            assertThat(testRecord.recordId()).startsWith("CN-");
            assertThat(testRecord.recordId()).hasSizeGreaterThan(3);

            assertThat(testRecord.authCode()).hasSize(6);
            assertThat(testRecord.authCode()).matches("[A-Z0-9]{6}");

            assertThat(testRecord.panLastFour()).matches("\\d{4}");
            assertThat(testRecord.panLastFour()).isIn(
                    "1234", "5678", "9012", "3456", "7890",
                    "2345", "6789", "0123", "4567", "8901"
            );

            assertThat(testRecord.merchantName()).isNotBlank();
            assertThat(testRecord.merchantCity()).isNotBlank();

            assertThat(testRecord.mcc()).matches("\\d{4}");
            List<String> testValidMccs = List.of(
                    "5411", "5912", "5541", "5812", "5311", "4111", "7011", "4121",
                    "5999", "7372", "5045", "5961", "4814", "7941", "8011", "5261",
                    "5732", "5651", "5661", "5944"
            );
            assertThat(testValidMccs).contains(testRecord.mcc());

            assertThat(testRecord.amount()).isPositive();
            assertThat(testRecord.amount().scale()).isEqualTo(2);

            assertThat(testRecord.currency()).isIn(
                    Currency.getInstance("ZAR"),
                    Currency.getInstance("USD"),
                    Currency.getInstance("GBP"),
                    Currency.getInstance("EUR")
            );

            assertThat(testRecord.authorisedAt()).isBeforeOrEqualTo(Instant.now());
            assertThat(testRecord.authorisedAt()).isAfter(Instant.now().minusSeconds(172800));

            assertThat(testRecord.clearingStatus()).isIn("CLEARED", "AUTH", "REVERSED");

            if ("CLEARED".equals(testRecord.clearingStatus())) {
                assertThat(testRecord.clearedAt()).isNotNull();
                assertThat(testRecord.clearedAt()).isAfter(testRecord.authorisedAt());
            } else {
                assertThat(testRecord.clearedAt()).isNull();
            }
        }
    }

    @Test
    void generate_amountsAreWithinRange() {
        // Given
        int testCount = 100;

        // When
        List<CardRecord> testRecords = testGenerator.generate(testCount);

        // Then
        for (CardRecord testRecord : testRecords) {
            assertThat(testRecord.amount()).isBetween(
                    BigDecimal.valueOf(1.0),
                    BigDecimal.valueOf(8000.0)
            );
        }
    }

    @Test
    void generate_statusDistributionIsReasonable() {
        // Given
        int testCount = 1000;

        // When
        List<CardRecord> testRecords = testGenerator.generate(testCount);

        // Then
        long testClearedCount = testRecords.stream()
                .filter(r -> "CLEARED".equals(r.clearingStatus()))
                .count();

        long testAuthCount = testRecords.stream()
                .filter(r -> "AUTH".equals(r.clearingStatus()))
                .count();

        long testReversedCount = testRecords.stream()
                .filter(r -> "REVERSED".equals(r.clearingStatus()))
                .count();

        assertThat(testClearedCount).isGreaterThan(testCount * 50 / 100);
        assertThat(testClearedCount).isLessThan(testCount * 70 / 100);

        assertThat(testAuthCount).isGreaterThan(testCount * 20 / 100);
        assertThat(testAuthCount).isLessThan(testCount * 40 / 100);

        assertThat(testReversedCount).isGreaterThan(testCount * 5 / 100);
        assertThat(testReversedCount).isLessThan(testCount * 15 / 100);
    }

    @Test
    void generate_currenciesAreFromValidSet() {
        // Given
        int testCount = 100;

        // When
        List<CardRecord> testRecords = testGenerator.generate(testCount);

        // Then
        for (CardRecord testRecord : testRecords) {
            String testCurrencyCode = testRecord.currency().getCurrencyCode();
            assertThat(testCurrencyCode).isIn("ZAR", "USD", "GBP", "EUR");
        }
    }

    @Test
    void generate_recordIdsAreUnique() {
        // Given
        int testCount = 50;

        // When
        List<CardRecord> testRecords = testGenerator.generate(testCount);

        // Then
        long testUniqueIds = testRecords.stream()
                .map(CardRecord::recordId)
                .distinct()
                .count();

        assertThat(testUniqueIds).isEqualTo(testRecords.size());
    }

    @Test
    void generate_authCodesAreValidFormat() {
        // Given
        int testCount = 50;

        // When
        List<CardRecord> testRecords = testGenerator.generate(testCount);

        // Then
        for (CardRecord testRecord : testRecords) {
            assertThat(testRecord.authCode()).matches("[A-Z0-9]{6}");
            assertThat(testRecord.authCode()).doesNotContainPattern("[^A-Z0-9]");
        }
    }

    @Test
    void generate_mccCodesHaveValidFormat() {
        // Given
        int testCount = 50;

        // When
        List<CardRecord> testRecords = testGenerator.generate(testCount);

        // Then
        for (CardRecord testRecord : testRecords) {
            assertThat(testRecord.mcc()).matches("\\d{4}");
            assertThat(testRecord.mcc()).doesNotContainPattern("[^0-9]");
        }
    }

    @Test
    void generate_clearedRecordsHaveClearedAtAfterAuthorisedAt() {
        // Given
        int testCount = 100;

        // When
        List<CardRecord> testRecords = testGenerator.generate(testCount);

        // Then
        for (CardRecord testRecord : testRecords) {
            if ("CLEARED".equals(testRecord.clearingStatus())) {
                assertThat(testRecord.clearedAt()).isNotNull();
                assertThat(testRecord.clearedAt()).isAfter(testRecord.authorisedAt());
                assertThat(testRecord.clearedAt()).isBefore(testRecord.authorisedAt().plusSeconds(86400));
            }
        }
    }

    @Test
    void generate_panLastFourDistribution() {
        // Given
        int testCount = 100;

        // When
        List<CardRecord> testRecords = testGenerator.generate(testCount);

        // Then
        List<String> testValidPanLastFours = List.of(
                "1234", "5678", "9012", "3456", "7890",
                "2345", "6789", "0123", "4567", "8901"
        );

        for (CardRecord testRecord : testRecords) {
            assertThat(testValidPanLastFours).contains(testRecord.panLastFour());
        }
    }
}