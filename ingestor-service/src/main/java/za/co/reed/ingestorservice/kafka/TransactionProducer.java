package za.co.reed.ingestorservice.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import za.co.reed.commom.constants.KafkaTopics;
import za.co.reed.commom.dto.NormalisedTransaction;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes NormalisedTransactions to the raw-transactions Kafka topic.
 *
 * Key design decisions:
 *
 *   Message key: sourceId
 *     Kafka uses the key to determine which partition a message lands on.
 *     Using sourceId as the key means all messages from the same source
 *     transaction always go to the same partition — preserving ordering
 *     within a single transaction's lifecycle (AUTH → CLEARED → REVERSED).
 *
 *   Async publish with callback:
 *     KafkaTemplate.send() is non-blocking. We attach a callback to log
 *     success/failure. If the send fails (broker unavailable, timeout),
 *     the exception propagates to IngestorService which returns FAILED,
 *     and the sourceId is NOT marked seen in Redis — so the next retry
 *     from the mock source will be accepted and re-attempted.
 *
 *   Producer config (in application.yml):
 *     acks=all          — wait for all in-sync replicas to confirm
 *     enable.idempotence=true — exactly-once producer semantics
 *     retries=3         — auto-retry transient broker errors
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionProducer {

    private final KafkaTemplate<String, NormalisedTransaction> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * Publish a transaction to the raw-transactions topic.
     * Blocks until the broker confirms receipt (due to acks=all).
     * Throws RuntimeException on publish failure.
     *
     * @param transaction the normalised, deduplicated transaction to publish
     */
    public void publish(NormalisedTransaction transaction) {
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<SendResult<String, NormalisedTransaction>> future =
                kafkaTemplate.send(
                        KafkaTopics.RAW_TRANSACTIONS,
                        transaction.sourceId(),   // partition key
                        transaction
                );

        future.whenComplete((result, ex) -> {
            long duration = System.currentTimeMillis() - startTime;
            
            if (ex != null) {
                log.error("Failed to publish transaction to Kafka — sourceId={} topic={}", transaction.sourceId(),
                        KafkaTopics.RAW_TRANSACTIONS, ex);
                
                // Record failure metrics
                meterRegistry.counter("transaction_aggregator.kafka.producer.errors",
                        "topic", KafkaTopics.RAW_TRANSACTIONS,
                        "sourceType", transaction.sourceType().name()).increment();
                
                throw new RuntimeException("Kafka publish failed for sourceId: "
                        + transaction.sourceId(), ex);
            }
            
            // Record success metrics
            meterRegistry.counter("transaction_aggregator.kafka.producer.messages.sent",
                    "topic", KafkaTopics.RAW_TRANSACTIONS,
                    "sourceType", transaction.sourceType().name()).increment();
            
            meterRegistry.timer("transaction_aggregator.kafka.producer.latency",
                    "topic", KafkaTopics.RAW_TRANSACTIONS).record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
            
            log.debug("Transaction published — sourceId={} partition={} offset={}",
                    transaction.sourceId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        });

        // Join to make publish synchronous from the caller's perspective.
        // This ensures we don't return ACCEPTED before Kafka has confirmed.
        future.join();
    }
}
