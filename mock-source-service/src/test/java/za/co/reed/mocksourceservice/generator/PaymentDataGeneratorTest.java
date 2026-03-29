package za.co.reed.mocksourceservice.generator;

import za.co.reed.mocksourceservice.dto.PaymentEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PaymentDataGenerator}.
 */
class PaymentDataGeneratorTest {

    private final PaymentDataGenerator testGenerator = new PaymentDataGenerator();

    @Test
    void generate_returnsCorrectNumberOfRecords_whenNoRetries() {
        // Given
        int testCount = 10;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        assertThat(testEvents).hasSize(testCount);
    }

    @Test
    void generate_returnsMoreRecords_whenWithRetries() {
        // Given
        int testCount = 100;
        boolean testWithRetries = true;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        assertThat(testEvents.size()).isGreaterThan(testCount);
        assertThat(testEvents.size()).isLessThan(testCount * 2);
    }

    @Test
    void generate_returnsEmptyList_whenZeroCount() {
        // Given
        int testCount = 0;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        assertThat(testEvents).isEmpty();
    }

    @Test
    void generate_eventsHaveValidFields() {
        // Given
        int testCount = 5;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        for (PaymentEvent testEvent : testEvents) {
            assertThat(testEvent.getEventId()).startsWith("evt_");
            assertThat(testEvent.getEventId()).hasSizeGreaterThan(4);

            assertThat(testEvent.getEventType()).isIn(
                    "payment_intent.succeeded",
                    "payment_intent.created",
                    "charge.refunded"
            );

            assertThat(testEvent.getPaymentId()).startsWith("pi_");
            assertThat(testEvent.getPaymentId()).hasSizeGreaterThan(3);

            assertThat(testEvent.getCustomerId()).matches("cus_\\d{3}");
            assertThat(testEvent.getCustomerId()).isIn("cus_001", "cus_002", "cus_003", "cus_004", "cus_005");

            assertThat(testEvent.getMerchantDescriptor()).isNotBlank();

            assertThat(testEvent.getAmount()).isPositive();
            assertThat(testEvent.getAmount().scale()).isEqualTo(2);

            assertThat(testEvent.getCurrency()).isIn(
                    Currency.getInstance("ZAR"),
                    Currency.getInstance("USD"),
                    Currency.getInstance("GBP"),
                    Currency.getInstance("EUR")
            );

            assertThat(testEvent.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
            assertThat(testEvent.getRetryAttempt()).isBetween(0, 1);
        }
    }

    @Test
    void generate_amountsAreWithinRange() {
        // Given
        int testCount = 100;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        for (PaymentEvent testEvent : testEvents) {
            assertThat(testEvent.getAmount()).isBetween(
                    BigDecimal.valueOf(5.0),
                    BigDecimal.valueOf(10000.0)
            );
        }
    }

    @Test
    void generate_eventTypeDistributionIsReasonable() {
        // Given
        int testCount = 1000;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        long testSucceededCount = testEvents.stream()
                .filter(e -> "payment_intent.succeeded".equals(e.getEventType()))
                .count();

        long testCreatedCount = testEvents.stream()
                .filter(e -> "payment_intent.created".equals(e.getEventType()))
                .count();

        long testRefundedCount = testEvents.stream()
                .filter(e -> "charge.refunded".equals(e.getEventType()))
                .count();

        assertThat(testSucceededCount).isGreaterThan(testCount * 60 / 100);
        assertThat(testSucceededCount).isLessThan(testCount * 80 / 100);

        assertThat(testCreatedCount).isGreaterThan(testCount * 10 / 100);
        assertThat(testCreatedCount).isLessThan(testCount * 30 / 100);

        assertThat(testRefundedCount).isGreaterThan(testCount * 5 / 100);
        assertThat(testRefundedCount).isLessThan(testCount * 15 / 100);
    }

    @Test
    void generate_currenciesAreFromValidSet() {
        // Given
        int testCount = 100;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        for (PaymentEvent testEvent : testEvents) {
            String testCurrencyCode = testEvent.getCurrency().getCurrencyCode();
            assertThat(testCurrencyCode).isIn("ZAR", "USD", "GBP", "EUR");
        }
    }

    @Test
    void generate_eventIdsAreUnique() {
        // Given
        int testCount = 50;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        long testUniqueIds = testEvents.stream()
                .map(PaymentEvent::getEventId)
                .distinct()
                .count();

        assertThat(testUniqueIds).isEqualTo(testEvents.size());
    }

    @Test
    void generate_paymentIdsAreUnique() {
        // Given
        int testCount = 50;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        long testUniquePaymentIds = testEvents.stream()
                .map(PaymentEvent::getPaymentId)
                .distinct()
                .count();

        assertThat(testUniquePaymentIds).isEqualTo(testEvents.size());
    }

    @Test
    void generate_withRetries_includesDuplicateEvents() {
        // Given
        int testCount = 100;
        boolean testWithRetries = true;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        long testRetryEvents = testEvents.stream()
                .filter(e -> e.getRetryAttempt() == 1)
                .count();

        assertThat(testRetryEvents).isGreaterThan(0);

        List<PaymentEvent> testOriginalEvents = testEvents.stream()
                .filter(e -> e.getRetryAttempt() == 0)
                .toList();

        List<PaymentEvent> testRetryEventsList = testEvents.stream()
                .filter(e -> e.getRetryAttempt() == 1)
                .toList();

        for (PaymentEvent testRetry : testRetryEventsList) {
            boolean testHasMatchingOriginal = testOriginalEvents.stream()
                    .anyMatch(testOriginal -> testOriginal.getPaymentId().equals(testRetry.getPaymentId()));
            assertThat(testHasMatchingOriginal).isTrue();
        }
    }

    @Test
    void generate_withoutRetries_allEventsHaveZeroRetryAttempt() {
        // Given
        int testCount = 50;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        for (PaymentEvent testEvent : testEvents) {
            assertThat(testEvent.getRetryAttempt()).isZero();
        }
    }

    @Test
    void generate_customerIdsAreFromValidSet() {
        // Given
        int testCount = 100;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        List<String> testValidCustomerIds = List.of("cus_001", "cus_002", "cus_003", "cus_004", "cus_005");

        for (PaymentEvent testEvent : testEvents) {
            assertThat(testValidCustomerIds).contains(testEvent.getCustomerId());
        }
    }

    @Test
    void generate_paymentIdFormatIsValid() {
        // Given
        int testCount = 50;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        for (PaymentEvent testEvent : testEvents) {
            assertThat(testEvent.getPaymentId()).startsWith("pi_");
            String testIdPart = testEvent.getPaymentId().substring(3);
            assertThat(testIdPart).matches("[a-z0-9]+");
        }
    }

    @Test
    void generate_eventIdFormatIsValid() {
        // Given
        int testCount = 50;
        boolean testWithRetries = false;

        // When
        List<PaymentEvent> testEvents = testGenerator.generate(testCount, testWithRetries);

        // Then
        for (PaymentEvent testEvent : testEvents) {
            assertThat(testEvent.getEventId()).startsWith("evt_");
            // Should be alphanumeric after "evt_"
            String testIdPart = testEvent.getEventId().substring(4);
            assertThat(testIdPart).matches("[a-z0-9]+");
        }
    }
}