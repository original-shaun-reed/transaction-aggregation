package za.co.reed.mocksourceservice.adapter;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.mocksourceservice.dto.PaymentEvent;

/**
 * Adapts Stripe-like PaymentEvents into NormalisedTransactions.
 *
 * Key mapping decisions:
 *   - eventId         → sourceId (globally unique per Stripe event)
 *   - customerId      → accountId (customer = account in our model)
 *   - charge.refunded → TxStatus.REVERSED
 *   - MCC is null     — payment processor doesn't provide it; categorisation engine infers
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessorAdapter implements TransactionSourceAdapter {

    private final ObjectMapper objectMapper;

    // In-memory dedup store — keyed on paymentId
    private final Set<String> seenIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    @SuppressWarnings("unchecked")
    public List<NormalisedTransaction> normalise(Object rawPayload) {
        if (!(rawPayload instanceof List<?> events)) {
            log.warn("PaymentProcessorAdapter received unexpected payload type: {}", rawPayload.getClass().getSimpleName());
            return List.of();
        }

        return ((List<PaymentEvent>) events).stream()
                .filter(e -> !isDuplicate(e.getEventId()))
                .map(this::toNormalised)
                .peek(this::validate)
                .toList();
    }

    @Override
    public void validate(NormalisedTransaction transaction) {
        if (!StringUtils.hasText(transaction.sourceId())) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }

        if (transaction.amount() == null || transaction.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + transaction.amount());
        }

        if (!StringUtils.hasText(transaction.accountId())) {
            throw new IllegalArgumentException("accountId (customerId) must not be blank");
        }
    }

    @Override
    public boolean isDuplicate(String sourceId) {
        boolean added = seenIds.add(sourceId);

        if (!added) {
            log.debug("Duplicate payment event skipped (retry simulation): {}", sourceId);
        }

        return !added;
    }

    private NormalisedTransaction toNormalised(PaymentEvent e) {
        seenIds.add(e.getPaymentId());

        // MCC not provided by payment processor
        return new NormalisedTransaction(e.getPaymentId(), SourceType.PAYMENT_PROCESSOR, e.getCustomerId(),
                e.getAmount(), e.getCurrency(), e.getMerchantDescriptor(), null, e.getCreatedAt(),
                mapStatus(e.getEventType()), toJson(e));
    }

    private TransactionStatus mapStatus(String eventType) {
        return switch (eventType) {
            case "payment_intent.succeeded" -> TransactionStatus.SETTLED;
            case "payment_intent.created"   -> TransactionStatus.PENDING;
            case "charge.refunded"          -> TransactionStatus.REVERSED;
            default -> {
                log.warn("Unknown payment event type '{}' — defaulting to PENDING", eventType);
                yield TransactionStatus.PENDING;
            }
        };
    }

    private String toJson(PaymentEvent e) {
        try {
            return objectMapper.writeValueAsString(e);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialise PaymentEvent for eventId {}", e.getEventId(), ex);
            return "{}";
        }
    }
}
