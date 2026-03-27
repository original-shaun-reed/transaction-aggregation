package za.co.reed.mocksourceservice.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.mocksourceservice.dto.CardRecord;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapts ISO 8583-style CardRecords into NormalisedTransactions.
 *
 * Key difference from other adapters: MCC is passed through directly.
 * The categorisation engine can use it immediately without inference,
 * making card network records the highest-quality input for categorisation.
 *
 * Status mapping:
 *   CLEARED  → SETTLED
 *   AUTH     → PENDING
 *   REVERSED → REVERSED
 *
 * Account ID mapping:
 *   panLastFour is used as the accountId proxy. In a real system
 *   this would be a tokenised card reference — sufficient for mock purposes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardNetworkAdapter implements TransactionSourceAdapter {

    private final ObjectMapper objectMapper;

    // In-memory dedup store — keyed on sourceId
    private final Set<String> seenIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    @SuppressWarnings("unchecked")
    public List<NormalisedTransaction> normalise(Object rawPayload) {
        if (!(rawPayload instanceof List<?> records)) {
            log.warn("CardNetworkAdapter received unexpected payload type: {}", rawPayload.getClass().getSimpleName());
            return List.of();
        }

        return ((List<CardRecord>) records).stream()
                .filter((CardRecord record) -> !isDuplicate(record.recordId()))
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

        if (!StringUtils.hasText(transaction.merchantMcc())) {
            throw new IllegalArgumentException("merchantMcc must not be blank for card network records");
        }
    }

    @Override
    public boolean isDuplicate(String sourceId) {
        boolean added = seenIds.add(sourceId);

        if (!added) {
            log.debug("Duplicate card network record skipped: {}", sourceId);
        }

        return !added;
    }

    private NormalisedTransaction toNormalised(CardRecord record) {
        seenIds.add(record.recordId());

        // Use clearing time if settled, otherwise authorisation time
        Instant transactedAt = record.clearedAt() != null ? record.clearedAt() : record.authorisedAt();

        // MCC passed through — no inference needed
        return new NormalisedTransaction(record.recordId(), SourceType.CARD_NETWORK, record.panLastFour(), record.amount(), record.currency(),
                record.merchantName(), record.mcc(), transactedAt, mapStatus(record.clearingStatus()), toJson(record));
    }

    private TransactionStatus mapStatus(String clearingStatus) {
        return switch (clearingStatus) {
            case "CLEARED"  -> TransactionStatus.SETTLED;
            case "AUTH"     -> TransactionStatus.PENDING;
            case "REVERSED" -> TransactionStatus.REVERSED;
            default -> {
                log.warn("Unknown card clearing status '{}' — defaulting to PENDING", clearingStatus);
                yield TransactionStatus.PENDING;
            }
        };
    }

    private String toJson(CardRecord r) {
        try {
            return objectMapper.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise CardRecord for recordId {}", r.recordId(), e);
            return "{}";
        }
    }
}
