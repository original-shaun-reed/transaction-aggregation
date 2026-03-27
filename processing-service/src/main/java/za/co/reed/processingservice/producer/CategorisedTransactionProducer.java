package za.co.reed.processingservice.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import za.co.reed.commom.constants.KafkaTopics;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.persistence.entity.Transaction;

/**
 * Publishes categorised transactions to the categorised-transactions Kafka topic.
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class CategorisedTransactionProducer {

    private final KafkaTemplate<String, NormalisedTransaction> kafkaTemplate;

    public void publish(Transaction transaction, NormalisedTransaction source) {
        kafkaTemplate.send(KafkaTopics.CATEGORISED_TRANSACTIONS, source.sourceId(), source)
                     .whenComplete((result, ex) -> handlePublishResult(transaction, source, ex));
    }

    private void handlePublishResult(Transaction transaction, NormalisedTransaction source, Throwable ex) {
        if (ex != null) {
            log.error("Failed to publish to categorised-transactions — sourceId={}", source.sourceId(), ex);
            return;
        }

        String categoryCode = transaction.getCategory() != null
                ? transaction.getCategory().getCode()
                : "none";

        log.debug("Published to categorised-transactions — sourceId={} category={}", source.sourceId(), categoryCode);
    }
}
