package za.co.reed.mocksourceservice.generator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import com.github.javafaker.Faker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.reed.mocksourceservice.dto.PaymentEvent;

/**
 * Generates Stripe-like payment events.
 */
@Slf4j
@Component
public class PaymentDataGenerator {

    private static final List<String> EVENT_TYPES = List.of(
            "payment_intent.succeeded",
            "payment_intent.succeeded",
            "payment_intent.succeeded",
            "payment_intent.succeeded",
            "payment_intent.succeeded",
            "payment_intent.succeeded",
            "payment_intent.succeeded",
            "payment_intent.created",
            "payment_intent.created",
            "charge.refunded"
    );

    private static final List<String> CURRENCIES = List.of(
            "ZAR",
            "USD",
            "GBP",
            "EUR"
    );

    private static final List<String> CUSTOMER_IDS = List.of(
            "cus_001",
            "cus_002",
            "cus_003",
            "cus_004",
            "cus_005"
    );

    private final Faker faker = new Faker();

    public List<PaymentEvent> generate(int count, boolean withRetries) {
        List<PaymentEvent> events = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            PaymentEvent event = buildEvent();
            events.add(event);

            // Simulate Stripe retry: re-add 15% of events with retryAttempt=1
            if (withRetries && ThreadLocalRandom.current().nextInt(100) < 15) {
                PaymentEvent paymentEvent = new PaymentEvent(event.getEventId(), event.getEventType(), event.getPaymentId(),
                        event.getCustomerId(), event.getMerchantDescriptor(), event.getAmount(), event.getCurrency(),
                        event.getCreatedAt(), 1);

                events.add(paymentEvent);
            }
        }

        log.debug("Generated {} payment events ({} requested, retries included)", events.size(), count);
        return events;
    }

    private PaymentEvent buildEvent() {
        int typeIndex = ThreadLocalRandom.current().nextInt(EVENT_TYPES.size());

        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String eventType = EVENT_TYPES.get(typeIndex);
        String paymentId = "pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String currency = CURRENCIES.get(ThreadLocalRandom.current().nextInt(CURRENCIES.size()));
        String customerId = CUSTOMER_IDS.get(ThreadLocalRandom.current().nextInt(CUSTOMER_IDS.size()));

        BigDecimal amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(5.0, 10_000.0))
                .setScale(2, RoundingMode.HALF_UP);

        return new PaymentEvent(eventId, eventType, paymentId, customerId, faker.company().name(), amount,
                Currency.getInstance(currency), Instant.now(), 0);
    }
}
