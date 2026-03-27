package za.co.reed.ingestorservice.service;

import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.ingestorservice.enums.IngestResult;
import za.co.reed.ingestorservice.kafka.TransactionProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the three-step ingest pipeline for every inbound transaction:
 *
 *   1. Validate  — NormalisedTransaction compact constructor already enforces
 *                  invariants; this layer adds business-level checks (e.g. future dates)
 *   2. Deduplicate — Redis TTL check keyed on sourceId
 *   3. Publish   — KafkaTemplate send to raw-transactions topic
 *
 * Returns an IngestResult enum so the caller (controller or scheduler)
 * can decide how to respond without catching exceptions for flow control.
 *
 * This class is intentionally thin — it delegates all meaningful work to
 * DeduplicationService and TransactionProducer so each can be unit-tested
 * in isolation with Mockito.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestorService {
    private final NormalisationService normalisationService;
    private final DeduplicationService deduplicationService;
    private final TransactionProducer transactionProducer;

    /**
     * Ingest a single NormalisedTransaction through the full pipeline.
     *
     * @param transaction the incoming transaction (already normalised by the source adapter)
     * @return ACCEPTED if published, DUPLICATE if already seen, FAILED on Kafka error
     */
    public IngestResult ingest(NormalisedTransaction transaction) {
        try {
            // Step 1: additional business-level validation beyond record constraints
            normalisationService.validate(transaction);

            // Step 2: deduplication — check Redis before doing any Kafka work
            if (deduplicationService.isDuplicate(transaction.sourceId())) {
                log.info("Duplicate transaction skipped — sourceId={} sourceType={}", transaction.sourceId(), transaction.sourceType());
                return IngestResult.DUPLICATE;
            }

            // Step 3: publish to Kafka
            transactionProducer.publish(transaction);

            // Step 4: mark as seen in Redis (after successful publish to avoid marking something seen that we failed to publish)
            deduplicationService.markSeen(transaction.sourceId());

            log.info("Transaction ingested — sourceId={} sourceType={} amount={} {}", transaction.sourceId(),
                    transaction.sourceType(), transaction.amount(), transaction.currency());

            return IngestResult.ACCEPTED;
        } catch (IllegalArgumentException e) {
            log.warn("Transaction validation failed — sourceId={} reason={}", transaction.sourceId(), e.getMessage());
            return IngestResult.FAILED;

        } catch (Exception e) {
            log.error("Transaction ingest failed — sourceId={}", transaction.sourceId(), e);
            return IngestResult.FAILED;
        }
    }
}
