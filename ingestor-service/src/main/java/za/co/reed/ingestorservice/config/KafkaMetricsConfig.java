package za.co.reed.ingestorservice.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Configuration for Kafka metrics using Micrometer.
 * 
 * This configuration binds Kafka producer metrics to Micrometer's MeterRegistry
 * so they appear in Spring Boot Actuator metrics endpoint.
 * 
 * Key metrics exposed:
 * - kafka.producer.record.send.total
 * - kafka.producer.record.error.total
 * - kafka.producer.record.retry.total
 * - kafka.producer.byte.total
 * - kafka.producer.request.total
 * - kafka.producer.record.queue.time.avg
 * - kafka.producer.request.latency.avg
 * 
 * These metrics are automatically collected and can be scraped by Prometheus
 * or viewed via /actuator/metrics endpoint.
 */
@Configuration
@RequiredArgsConstructor
public class KafkaMetricsConfig {

    private final ProducerFactory<String, ?> producerFactory;
    private final MeterRegistry meterRegistry;
    
    private KafkaClientMetrics kafkaClientMetrics;
    
    @PostConstruct
    public void init() {
        // Get the underlying Kafka producer to bind metrics
        Producer<String, ?> producer = producerFactory.createProducer();
        kafkaClientMetrics = new KafkaClientMetrics(producer);
        kafkaClientMetrics.bindTo(meterRegistry);
    }
    
    @PreDestroy
    public void close() {
        if (kafkaClientMetrics != null) {
            kafkaClientMetrics.close();
        }
    }
}