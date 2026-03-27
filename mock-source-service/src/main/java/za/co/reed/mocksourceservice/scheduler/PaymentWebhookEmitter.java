package za.co.reed.mocksourceservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.mocksourceservice.dto.PaymentEvent;
import za.co.reed.mocksourceservice.client.IngestorClient;
import za.co.reed.mocksourceservice.adapter.PaymentProcessorAdapter;
import za.co.reed.mocksourceservice.generator.PaymentDataGenerator;
import za.co.reed.mocksourceservice.properties.PaymentProcessorProperties;

import java.util.List;

/**
 * Simulates a payment processor pushing events to a webhook endpoint.
 *
 * Unlike the bank feed (which the ingestor pulls), here the source pushes —
 * so this emitter fires outbound HTTP POSTs to the ingestor on a schedule,
 * mimicking how Stripe delivers webhook events.
 *
 * The ingestor's WebhookIngestController receives and processes these.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sources.payment-processor.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentWebhookEmitter {

    private final PaymentDataGenerator generator;
    private final PaymentProcessorAdapter adapter;
    private final PaymentProcessorProperties properties;
    private final IngestorClient ingestorClient;

    @Scheduled(fixedDelayString = "${sources.payment-processor.emit-interval-ms:15000}")
    public void emit() {
        log.info("Payment processor emitting — events={} retries={}", properties.getEventsPerEmit(), properties.isRetrySimulation());


        List<PaymentEvent> raw = generator.generate(properties.getEventsPerEmit(), properties.isRetrySimulation());

        List<NormalisedTransaction> normalised = adapter.normalise(raw);

        log.info("Payment processor emitting {} transactions ({} raw, {} deduped out)",
                normalised.size(), raw.size(), raw.size() - normalised.size());

        normalised.forEach((NormalisedTransaction transaction) -> {
            try {
                ingestorClient.send(transaction);
            } catch (Exception e) {
                log.error("Failed to emit payment event {} to ingestor", transaction.sourceId(), e);
            }
        });
    }
}
