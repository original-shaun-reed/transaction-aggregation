package za.co.reed.mocksourceservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * Mimics a Stripe webhook event payload. (https://docs.stripe.com/webhooks)
 *
 * Event types:
 *   payment_intent.succeeded  — normal settled payment
 *   payment_intent.created    — payment initiated but not yet captured
 *   charge.refunded           — full or partial reversal
 */

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    /** Globally unique event ID — used for deduplication. */
    private String eventId;

    /** Stripe-style event type string. */
    private String eventType;

    /** Stripe-style charge/payment ID (e.g. ch_xxx, pi_xxx). */
    private String paymentId;

    private String customerId;
    private String merchantDescriptor;

    /** Amount in major currency units (not cents). */
    private BigDecimal amount;

    private Currency currency;
    private Instant createdAt;
    private int retryAttempt;
}
