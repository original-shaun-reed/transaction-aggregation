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

    private final PaymentDataGenerator generator = new PaymentDataGenerator();

    @Test
    void generate_returnsCorrectNumberOfRecords_whenNoRetries() {
        // Given
        int count = 10;
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        assertThat(events).hasSize(count);
    }
    
    @Test
    void generate_returnsMoreRecords_whenWithRetries() {
        // Given
        int count = 100;
        boolean withRetries = true;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then - Should have more than count due to retries (~15% more)
        assertThat(events.size()).isGreaterThan(count);
        // But not more than double
        assertThat(events.size()).isLessThan(count * 2);
    }
    
    @Test
    void generate_returnsEmptyList_whenZeroCount() {
        // Given
        int count = 0;
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        assertThat(events).isEmpty();
    }
    
    @Test
    void generate_eventsHaveValidFields() {
        // Given
        int count = 5;
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        for (PaymentEvent event : events) {
            assertThat(event.getEventId()).startsWith("evt_");
            assertThat(event.getEventId()).hasSizeGreaterThan(4);
            
            assertThat(event.getEventType()).isIn(
                "payment_intent.succeeded",
                "payment_intent.created",
                "charge.refunded"
            );
            
            assertThat(event.getPaymentId()).startsWith("pi_");
            assertThat(event.getPaymentId()).hasSizeGreaterThan(3);
            
            assertThat(event.getCustomerId()).matches("cus_\\d{3}");
            assertThat(event.getCustomerId()).isIn(
                "cus_001", "cus_002", "cus_003", "cus_004", "cus_005"
            );
            
            assertThat(event.getMerchantDescriptor()).isNotBlank();
            
            assertThat(event.getAmount()).isPositive();
            assertThat(event.getAmount().scale()).isEqualTo(2); // 2 decimal places
            
            assertThat(event.getCurrency()).isIn(
                Currency.getInstance("ZAR"),
                Currency.getInstance("USD"),
                Currency.getInstance("GBP"),
                Currency.getInstance("EUR")
            );
            
            assertThat(event.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
            // Should be recent (no jitter in payment events)
            
            assertThat(event.getRetryAttempt()).isBetween(0, 1);
        }
    }
    
    @Test
    void generate_amountsAreWithinRange() {
        // Given
        int count = 100;
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        for (PaymentEvent event : events) {
            assertThat(event.getAmount()).isBetween(
                BigDecimal.valueOf(5.0),
                BigDecimal.valueOf(10000.0)
            );
        }
    }
    
    @Test
    void generate_eventTypeDistributionIsReasonable() {
        // Given
        int count = 1000; // Large sample to test distribution
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        long succeededCount = events.stream()
            .filter(e -> "payment_intent.succeeded".equals(e.getEventType()))
            .count();
        
        long createdCount = events.stream()
            .filter(e -> "payment_intent.created".equals(e.getEventType()))
            .count();
        
        long refundedCount = events.stream()
            .filter(e -> "charge.refunded".equals(e.getEventType()))
            .count();
        
        // Rough distribution check (70% succeeded, 20% created, 10% refunded)
        // Allow some variance due to randomness
        assertThat(succeededCount).isGreaterThan(count * 60 / 100); // >60%
        assertThat(succeededCount).isLessThan(count * 80 / 100);    // <80%
        
        assertThat(createdCount).isGreaterThan(count * 10 / 100); // >10%
        assertThat(createdCount).isLessThan(count * 30 / 100);    // <30%
        
        assertThat(refundedCount).isGreaterThan(count * 5 / 100);  // >5%
        assertThat(refundedCount).isLessThan(count * 15 / 100);    // <15%
    }
    
    @Test
    void generate_currenciesAreFromValidSet() {
        // Given
        int count = 100;
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        for (PaymentEvent event : events) {
            String currencyCode = event.getCurrency().getCurrencyCode();
            assertThat(currencyCode).isIn("ZAR", "USD", "GBP", "EUR");
        }
    }
    
    @Test
    void generate_eventIdsAreUnique() {
        // Given
        int count = 50;
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        long uniqueIds = events.stream()
            .map(PaymentEvent::getEventId)
            .distinct()
            .count();
        
        assertThat(uniqueIds).isEqualTo(events.size());
    }
    
    @Test
    void generate_paymentIdsAreUnique() {
        // Given
        int count = 50;
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        long uniquePaymentIds = events.stream()
            .map(PaymentEvent::getPaymentId)
            .distinct()
            .count();
        
        // Payment IDs should be unique per event
        assertThat(uniquePaymentIds).isEqualTo(events.size());
    }
    
    @Test
    void generate_withRetries_includesDuplicateEvents() {
        // Given
        int count = 100;
        boolean withRetries = true;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then - Some events should have retryAttempt = 1
        long retryEvents = events.stream()
            .filter(e -> e.getRetryAttempt() == 1)
            .count();
        
        assertThat(retryEvents).isGreaterThan(0);
        
        // Check that retry events have same eventId/paymentId as some original
        // but with retryAttempt = 1
        List<PaymentEvent> originalEvents = events.stream()
            .filter(e -> e.getRetryAttempt() == 0)
            .toList();
        
        List<PaymentEvent> retryEventsList = events.stream()
            .filter(e -> e.getRetryAttempt() == 1)
            .toList();
        
        // Each retry event should match an original event's paymentId
        for (PaymentEvent retry : retryEventsList) {
            boolean hasMatchingOriginal = originalEvents.stream()
                .anyMatch(original -> original.getPaymentId().equals(retry.getPaymentId()));
            assertThat(hasMatchingOriginal).isTrue();
        }
    }
    
    @Test
    void generate_withoutRetries_allEventsHaveZeroRetryAttempt() {
        // Given
        int count = 50;
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        for (PaymentEvent event : events) {
            assertThat(event.getRetryAttempt()).isZero();
        }
    }
    
    @Test
    void generate_customerIdsAreFromValidSet() {
        // Given
        int count = 100;
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        List<String> validCustomerIds = List.of(
            "cus_001", "cus_002", "cus_003", "cus_004", "cus_005"
        );
        
        for (PaymentEvent event : events) {
            assertThat(validCustomerIds).contains(event.getCustomerId());
        }
    }
    
    @Test
    void generate_paymentIdFormatIsValid() {
        // Given
        int count = 50;
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        for (PaymentEvent event : events) {
            assertThat(event.getPaymentId()).startsWith("pi_");
            // Should be alphanumeric after "pi_"
            String idPart = event.getPaymentId().substring(3);
            assertThat(idPart).matches("[a-z0-9]+");
        }
    }
    
    @Test
    void generate_eventIdFormatIsValid() {
        // Given
        int count = 50;
        boolean withRetries = false;
        
        // When
        List<PaymentEvent> events = generator.generate(count, withRetries);
        
        // Then
        for (PaymentEvent event : events) {
            assertThat(event.getEventId()).startsWith("evt_");
            // Should be alphanumeric after "evt_"
            String idPart = event.getEventId().substring(4);
            assertThat(idPart).matches("[a-z0-9]+");
        }
    }
}