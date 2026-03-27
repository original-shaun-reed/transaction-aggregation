package za.co.reed.mocksourceservice.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds to sources.card-network.* in application.yaml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "sources.card-network")
public class CardNetworkProperties {

    private boolean enabled = true;

    /** How often a new batch file is generated, in milliseconds. */
    private long batchIntervalMs = 300_000L;

    /** Number of card records per batch. */
    private int recordsPerBatch = 50;
}
