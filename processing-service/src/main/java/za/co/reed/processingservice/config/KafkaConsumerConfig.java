package za.co.reed.processingservice.config;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import za.co.reed.commom.dto.NormalisedTransaction;

/**
 * Kafka consumer configuration.
 *
 * Key decisions:
 *
 *   MANUAL_IMMEDIATE ack mode:
 *     Offsets are committed only after acknowledgment.acknowledge() is called
 *     in the listener. This means a failed message is redelivered — giving
 *     Spring Retry a chance to retry before routing to the DLQ.
 *
 *   Concurrency = 3:
 *     Matches the number of partitions per consumer group. Each thread handles
 *     one partition. Increase concurrency if partitions increase.
 *
 *   Trusted packages:
 *     JsonDeserializer must trust the NormalisedTransaction package or it will
 *     refuse to deserialise messages. Using the specific package rather than *
 *     for security.
 *
 *   max.poll.records = 100:
 *     Processes up to 100 messages per poll. With per-message DB writes,
 *     this balances throughput vs memory pressure. Reduce if processing is slow.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:processing-service}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, NormalisedTransaction> consumerFactory() {
        JsonDeserializer<NormalisedTransaction> deserializer = new JsonDeserializer<>(NormalisedTransaction.class);
        deserializer.addTrustedPackages("za.co.reed.common");
        deserializer.setUseTypeMapperForKey(false);

        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,            groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,   "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG,    "100",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,  "false"
        );

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NormalisedTransaction>
    kafkaListenerContainerFactory(ConsumerFactory<String, NormalisedTransaction> consumerFactory, RetryConfig retryConfig) {
        ConcurrentKafkaListenerContainerFactory<String, NormalisedTransaction> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);

        // Manual immediate acknowledgment for fine-grained control
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Plug in retry + DLQ handling from RetryConfig
        factory.setCommonErrorHandler(retryConfig.errorHandler());

        return factory;
    }
}
