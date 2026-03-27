package za.co.reed.processingservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import za.co.reed.commom.constants.KafkaTopics;

import java.util.Map;

/**
 * Retry and dead-letter configuration for the Kafka consumer.
 *
 * Strategy:
 *   - Attempt processing up to maxAttempts times with a fixed backoff
 *   - On final failure, DeadLetterPublishingRecoverer routes the message
 *     to raw-transactions.dlq with the original exception attached as headers
 *
 * maxAttempts=3 with backoffMs=1000 means:
 *   Attempt 1 → fail → wait 1s
 *   Attempt 2 → fail → wait 1s
 *   Attempt 3 → fail → route to DLQ
 *
 * Exceptions that should NOT be retried (misconfigured data, deserialisation
 * errors) can be added to the non-retryable list — they skip straight to the DLQ.
 *
 * The DLQ producer is a separate KafkaTemplate to avoid using the main
 * consumer's template configuration (which may have different serialisers).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RetryConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${retry.backoff-ms:1000}")
    private long backoffMs;

    @Bean
    public CommonErrorHandler errorHandler() {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dlqKafkaTemplate(), this::routeToDlq);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(backoffMs, maxAttempts - 1L));

        // Exceptions that should bypass retries and go straight to DLQ
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,   // bad data — retry won’t help
                IllegalStateException.class       // misconfiguration — won’t self-heal
        );

        return errorHandler;
    }

    private TopicPartition routeToDlq(ConsumerRecord<?, ?> record, Exception ex) {
        log.error("Routing to DLQ after {} attempts — topic={} key={}",
                maxAttempts, record.topic(), record.key(), ex);

        return new TopicPartition(KafkaTopics.RAW_TRANSACTIONS_DLQ, 0);
    }

    // Separate producer factory for DLQ to keep serializer config independent
    @Bean
    public KafkaTemplate<Object, Object> dlqKafkaTemplate() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.RETRIES_CONFIG, 3
        );

        ProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }

}
