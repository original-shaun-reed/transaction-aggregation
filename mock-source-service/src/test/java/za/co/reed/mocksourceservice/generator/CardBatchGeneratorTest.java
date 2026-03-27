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

    private final CardBatchGenerator generator = new CardBatchGenerator();

    @Test
    void generate_returnsCorrectNumberOfRecords() {
        // Given
        int count = 10;
        
        // When
        List<CardRecord> records = generator.generate(count);
        
        // Then
        assertThat(records).hasSize(count);
    }
    
    @Test
    void generate_returnsEmptyList_whenZeroCount() {
        // Given
        int count = 0;
        
        // When
        List<CardRecord> records = generator.generate(count);
        
        // Then
        assertThat(records).isEmpty();
    }
    
    @Test
    void generate_recordsHaveValidFields() {
        // Given
        int count = 5;
        
        // When
        List<CardRecord> records = generator.generate(count);
        
        // Then
        for (CardRecord record : records) {
            assertThat(record.recordId()).startsWith("CN-");
            assertThat(record.recordId()).hasSizeGreaterThan(3);
            
            assertThat(record.authCode()).hasSize(6);
            assertThat(record.authCode()).matches("[A-Z0-9]{6}");
            
            assertThat(record.panLastFour()).matches("\\d{4}");
            assertThat(record.panLastFour()).isIn(
                "1234", "5678", "9012", "3456", "7890",
                "2345", "6789", "0123", "4567", "8901"
            );
            
            assertThat(record.merchantName()).isNotBlank();
            assertThat(record.merchantCity()).isNotBlank();
            
            assertThat(record.mcc()).matches("\\d{4}");
            // MCC should be from the predefined pool
            List<String> validMccs = List.of(
                "5411", "5912", "5541", "5812", "5311", "4111", "7011", "4121",
                "5999", "7372", "5045", "5961", "4814", "7941", "8011", "5261",
                "5732", "5651", "5661", "5944"
            );
            assertThat(validMccs).contains(record.mcc());
            
            assertThat(record.amount()).isPositive();
            assertThat(record.amount().scale()).isEqualTo(2); // 2 decimal places
            
            assertThat(record.currency()).isIn(
                Currency.getInstance("ZAR"),
                Currency.getInstance("USD"),
                Currency.getInstance("GBP"),
                Currency.getInstance("EUR")
            );
            
            assertThat(record.authorisedAt()).isBeforeOrEqualTo(Instant.now());
            // Authorised up to 48h ago
            assertThat(record.authorisedAt()).isAfter(Instant.now().minusSeconds(172800));
            
            assertThat(record.clearingStatus()).isIn("CLEARED", "AUTH", "REVERSED");
            
            if ("CLEARED".equals(record.clearingStatus())) {
                assertThat(record.clearedAt()).isNotNull();
                assertThat(record.clearedAt()).isAfter(record.authorisedAt());
            } else {
                assertThat(record.clearedAt()).isNull();
            }
        }
    }
    
    @Test
    void generate_amountsAreWithinRange() {
        // Given
        int count = 100;
        
        // When
        List<CardRecord> records = generator.generate(count);
        
        // Then
        for (CardRecord record : records) {
            assertThat(record.amount()).isBetween(
                BigDecimal.valueOf(1.0),
                BigDecimal.valueOf(8000.0)
            );
        }
    }
    
    @Test
    void generate_statusDistributionIsReasonable() {
        // Given
        int count = 1000; // Large sample to test distribution
        
        // When
        List<CardRecord> records = generator.generate(count);
        
        // Then
        long clearedCount = records.stream()
            .filter(r -> "CLEARED".equals(r.clearingStatus()))
            .count();
        
        long authCount = records.stream()
            .filter(r -> "AUTH".equals(r.clearingStatus()))
            .count();
        
        long reversedCount = records.stream()
            .filter(r -> "REVERSED".equals(r.clearingStatus()))
            .count();
        
        // Rough distribution check (60% cleared, 30% auth, 10% reversed)
        // Allow some variance due to randomness
        assertThat(clearedCount).isGreaterThan(count * 50 / 100); // >50%
        assertThat(clearedCount).isLessThan(count * 70 / 100);    // <70%
        
        assertThat(authCount).isGreaterThan(count * 20 / 100); // >20%
        assertThat(authCount).isLessThan(count * 40 / 100);    // <40%
        
        assertThat(reversedCount).isGreaterThan(count * 5 / 100);  // >5%
        assertThat(reversedCount).isLessThan(count * 15 / 100);    // <15%
    }
    
    @Test
    void generate_currenciesAreFromValidSet() {
        // Given
        int count = 100;
        
        // When
        List<CardRecord> records = generator.generate(count);
        
        // Then
        for (CardRecord record : records) {
            String currencyCode = record.currency().getCurrencyCode();
            assertThat(currencyCode).isIn("ZAR", "USD", "GBP", "EUR");
        }
    }
    
    @Test
    void generate_recordIdsAreUnique() {
        // Given
        int count = 50;
        
        // When
        List<CardRecord> records = generator.generate(count);
        
        // Then
        long uniqueIds = records.stream()
            .map(CardRecord::recordId)
            .distinct()
            .count();
        
        assertThat(uniqueIds).isEqualTo(records.size());
    }
    
    @Test
    void generate_authCodesAreValidFormat() {
        // Given
        int count = 50;
        
        // When
        List<CardRecord> records = generator.generate(count);
        
        // Then
        for (CardRecord record : records) {
            assertThat(record.authCode()).matches("[A-Z0-9]{6}");
            // Should be alphanumeric
            assertThat(record.authCode()).doesNotContainPattern("[^A-Z0-9]");
        }
    }
    
    @Test
    void generate_mccCodesHaveValidFormat() {
        // Given
        int count = 50;
        
        // When
        List<CardRecord> records = generator.generate(count);
        
        // Then
        for (CardRecord record : records) {
            // MCC should be 4 digits
            assertThat(record.mcc()).matches("\\d{4}");
            // Should be numeric
            assertThat(record.mcc()).doesNotContainPattern("[^0-9]");
        }
    }
    
    @Test
    void generate_clearedRecordsHaveClearedAtAfterAuthorisedAt() {
        // Given
        int count = 100;
        
        // When
        List<CardRecord> records = generator.generate(count);
        
        // Then
        for (CardRecord record : records) {
            if ("CLEARED".equals(record.clearingStatus())) {
                assertThat(record.clearedAt()).isNotNull();
                assertThat(record.clearedAt()).isAfter(record.authorisedAt());
                // Clearing should be within 24h of authorisation
                assertThat(record.clearedAt()).isBefore(record.authorisedAt().plusSeconds(86400));
            }
        }
    }
    
    @Test
    void generate_panLastFourDistribution() {
        // Given
        int count = 100;
        
        // When
        List<CardRecord> records = generator.generate(count);
        
        // Then - all PAN last four should be from the predefined list
        List<String> validPanLastFours = List.of(
            "1234", "5678", "9012", "3456", "7890",
            "2345", "6789", "0123", "4567", "8901"
        );
        
        for (CardRecord record : records) {
            assertThat(validPanLastFours).contains(record.panLastFour());
        }
    }
}