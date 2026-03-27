package za.co.reed.ingestorservice.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import za.co.reed.commom.constants.KafkaTopics;

/**
 * Declares all Kafka topics as Spring-managed @Beans.
 *
 * Spring Kafka's KafkaAdmin auto-detects NewTopic beans and creates them
 * on application startup if they don't already exist. If they do exist,
 * it skips creation — safe to run against a pre-provisioned MSK cluster.
 *
 * Topic names and partition counts come from KafkaTopics constants in
 * common-lib — single source of truth across all modules.
 *
 * Replication factor is environment-specific:
 *   dev  — 1 (single local broker)
 *   prod — 3 (MSK default, set via application-prod.yml)
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.replication-factor:1}")
    private int replicationFactor;

    /**
     * Primary ingest topic. All source adapters publish here.
     * Consumed by: processing-service RawTransactionConsumer
     */
    @Bean
    public NewTopic rawTransactionsTopic() {
        return TopicBuilder.name(KafkaTopics.RAW_TRANSACTIONS)
                .partitions(KafkaTopics.RAW_TRANSACTIONS_PARTITIONS)
                .replicas(replicationFactor)
                .build();
    }

    /**
     * Categorised transactions — produced by processing-service after enrichment.
     * Declaring it here ensures it exists before processing-service starts consuming.
     */
    @Bean
    public NewTopic categorisedTransactionsTopic() {
        return TopicBuilder.name(KafkaTopics.CATEGORISED_TRANSACTIONS)
                .partitions(KafkaTopics.CATEGORISED_TRANSACTIONS_PARTITIONS)
                .replicas(replicationFactor)
                .build();
    }

    /**
     * Dead letter queue — failed messages after all retries in processing-service.
     * Single partition — DLQ messages are low volume and ordering matters for review.
     */
    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name(KafkaTopics.RAW_TRANSACTIONS_DLQ)
                .partitions(1)
                .replicas(replicationFactor)
                .build();
    }
}
