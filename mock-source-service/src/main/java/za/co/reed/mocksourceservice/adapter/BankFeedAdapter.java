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
import za.co.reed.mocksourceservice.dto.BankFeedRecord;

/**
 * Adapts raw BankFeedRecord payloads into NormalisedTransactions.
 *
 * Deduplication is in-memory (ConcurrentHashSet). In production this would
 * delegate to the ingestor's Redis-backed DeduplicationService — but for the
 * mock source, in-process is sufficient to avoid re-sending the same record
 * across scheduler ticks within the same JVM lifecycle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankFeedAdapter implements TransactionSourceAdapter {

    private final ObjectMapper objectMapper;

    // In-memory dedup store — keyed on sourceId
    private final Set<String> seenIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    @SuppressWarnings("unchecked")
    public List<NormalisedTransaction> normalise(Object rawPayload) {
        if (!(rawPayload instanceof List<?> records)) {
            log.warn("BankFeedAdapter.normalise() received unexpected payload type: {}", rawPayload.getClass().getSimpleName());
            return List.of();
        }

        return ((List<BankFeedRecord>) records).stream()
                .filter(record -> !isDuplicate(record.transactionId()))
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

        if (transaction.currency() == null) {
            throw new IllegalArgumentException("currency must not be null");
        }

        if (transaction.transactedAt() == null) {
            throw new IllegalArgumentException("transactedAt must not be null");
        }
    }

    @Override
    public boolean isDuplicate(String sourceId) {
        // add() returns false if already present — that means it's a duplicate
        boolean added = seenIds.add(sourceId);

        if (!added) {
            log.debug("Duplicate bank feed transaction skipped: {}", sourceId);
        }

        return !added;
    }

    private NormalisedTransaction toNormalised(BankFeedRecord record) {
        seenIds.add(record.transactionId());

        // MCC unknown for bank feed — inferred later
        return new NormalisedTransaction(record.transactionId(), SourceType.BANK_FEED, record.accountId(),
                record.amount(), record.currency(), record.merchantName(), null, record.transactedAt(),
                TransactionStatus.valueOf(record.status()), toJson(record));
    }

    private String toJson(BankFeedRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise bank feed to JSON for sourceId {}", record.transactionId(), e);
            return "{}";
        }
    }
}
