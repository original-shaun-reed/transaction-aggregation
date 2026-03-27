package za.co.reed.processingservice.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import za.co.reed.commom.constants.KafkaTopics;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.persistence.entity.Transaction;
import za.co.reed.processingservice.producer.CategorisedTransactionProducer;
import za.co.reed.persistence.repository.TransactionRepository;
import za.co.reed.processingservice.service.AggregationWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.reed.processingservice.service.CategorisationService;

/**
 * Consumes raw transactions from the raw-transactions Kafka topic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RawTransactionConsumer {

    private final TransactionRepository transactionRepository;
    private final CategorisationService categorisationService;
    private final AggregationWorker aggregationWorker;
    private final CategorisedTransactionProducer categorisedProducer;
    private final MeterRegistry meterRegistry;

    @KafkaListener(
            topics = KafkaTopics.RAW_TRANSACTIONS,
            groupId = "${spring.kafka.consumer.group-id:processing-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(ConsumerRecord<String, NormalisedTransaction> record, Acknowledgment acknowledgment) {
        NormalisedTransaction source = record.value();
        long startTime = System.currentTimeMillis();

        log.debug("Consuming — sourceId={} partition={} offset={}", source.sourceId(), record.partition(), record.offset());

        try {
            // Step 1: Map to JPA entity
            Transaction transaction = new Transaction(source);

            // Step 2: Persist
            transactionRepository.save(transaction);

            // Step 3: Categorise
            categorisationService.categorise(transaction, source);

            // Step 4: Update aggregations
            aggregationWorker.updateAggregations(transaction);

            // Step 5: Publish categorised event downstream
            categorisedProducer.publish(transaction, source);

            // Step 6: Commit Kafka offset — only reached on full success
            acknowledgment.acknowledge();

            // Record success metrics
            long duration = System.currentTimeMillis() - startTime;
            meterRegistry.counter("transaction_aggregator.kafka.consumer.messages.processed",
                    "topic", KafkaTopics.RAW_TRANSACTIONS,
                    "sourceType", source.sourceType().name(),
                    "status", "success").increment();
            
            meterRegistry.timer("transaction_aggregator.kafka.consumer.processing.time",
                    "topic", KafkaTopics.RAW_TRANSACTIONS).record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

            log.info("Processed — sourceId={} category={} status={}",
                    source.sourceId(),
                    transaction.getCategory() != null ? transaction.getCategory().getCode() : "none",
                    source.status());

        } catch (Exception e) {
            // Record failure metrics
            meterRegistry.counter(
                    "transaction_aggregator.kafka.consumer.messages.processed",
                    "topic",
                    KafkaTopics.RAW_TRANSACTIONS,
                    "sourceType",
                    source.sourceType().name(),
                    "status",
                    "failure"
            ).increment();
            
            // Do NOT acknowledge — let RetryConfig handle retry + DLQ routing
            log.error("Processing failed — sourceId={} partition={} offset={}",
                    source.sourceId(), record.partition(), record.offset(), e);
            throw e;
        }
    }
}
