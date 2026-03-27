package za.co.reed.processingservice.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import za.co.reed.commom.constants.KafkaTopics;
import za.co.reed.commom.dto.NormalisedTransaction;

/**
 * Consumes messages that failed all retry attempts and were routed to the DLQ.
 *
 * Responsibilities:
 *   - Log the failed message with full context for manual investigation
 *   - Acknowledge the DLQ offset (so the message is not re-processed indefinitely)
 *   - Emit a metric/alert (extension point — hook into Micrometer or SNS here)
 *
 * A message lands here when:
 *   - RawTransactionConsumer threw an exception on all maxAttempts
 *   - RetryConfig's DeadLetterPublishingRecoverer forwarded it to the DLQ topic
 *
 * This consumer does NOT attempt to reprocess the message — that would create
 * a retry loop. Manual intervention or a separate backfill job handles recovery.
 *
 * In production, wire this up to:
 *   - CloudWatch Metric → alarm → PagerDuty on DLQ message count > 0
 *   - An S3 sink for long-term storage of failed records
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {
    private final MeterRegistry meterRegistry;

    @KafkaListener(
            topics = KafkaTopics.RAW_TRANSACTIONS_DLQ,
            groupId = "${spring.kafka.consumer.group-id:processing-service}-dlq",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, NormalisedTransaction> record, Acknowledgment acknowledgment) {
        NormalisedTransaction source = record.value();

        // Extract the original exception class from Kafka headers if present
        String originalException = extractHeader(record, "kafka_dlt-exception-fqcn");
        String originalOffset    = extractHeader(record, "kafka_dlt-original-offset");
        String originalPartition = extractHeader(record, "kafka_dlt-original-partition");

        log.error("""
                DLQ message received — MANUAL INVESTIGATION REQUIRED
                  sourceId:          {}
                  sourceType:        {}
                  accountId:         {}
                  amount:            {} {}
                  status:            {}
                  originalPartition: {}
                  originalOffset:    {}
                  failureCause:      {}
                """,
                source.sourceId(),
                source.sourceType(),
                source.accountId(),
                source.amount(), source.currency(),
                source.status(),
                originalPartition,
                originalOffset,
                originalException
        );

        // Emit Micrometer metric
        meterRegistry.counter("transaction_aggregator.dlq.messages", "sourceType", source.sourceType().name()).increment();

        // Acknowledge so the DLQ offset advances — no infinite loop
        acknowledgment.acknowledge();
    }

    private String extractHeader(ConsumerRecord<?, ?> record, String headerName) {
        var header = record.headers().lastHeader(headerName);
        return header != null ? new String(header.value()) : "unknown";
    }
}
