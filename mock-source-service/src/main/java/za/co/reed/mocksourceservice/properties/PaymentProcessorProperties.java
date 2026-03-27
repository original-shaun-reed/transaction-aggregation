package za.co.reed.mocksourceservice.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds to sources.payment-processor.* in application.yaml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "sources.payment-processor")
public class PaymentProcessorProperties {

    private boolean enabled = true;

    /** How often the emitter fires outbound webhook POSTs, in milliseconds. */
    private long emitIntervalMs = 15_000L;

    /** Number of payment events to emit per interval. */
    private int eventsPerEmit = 8;

    /**
     * When true, some events are re-sent with the same eventId to simulate
     * Stripe's at-least-once webhook delivery retry behaviour.
     */
    private boolean retrySimulation = true;
}
