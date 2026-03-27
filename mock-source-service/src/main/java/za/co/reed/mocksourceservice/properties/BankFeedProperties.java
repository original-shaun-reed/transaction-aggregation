package za.co.reed.mocksourceservice.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds to sources.bank-feed.* in application.yaml.
 * All values have sensible defaults so local dev works without any config.
 */
@Data
@Component
@ConfigurationProperties(prefix = "sources.bank-feed")
public class BankFeedProperties {

    /** Toggle the bank feed source on/off without redeployment. */
    private boolean enabled = true;

    /** How often the scheduler polls, in milliseconds. */
    private long pollIntervalMs = 30_000L;

    /** Number of accounts to simulate per poll cycle. */
    private int accountsPerBatch = 10;

    /** Number of transactions to generate per account per cycle. */
    private int transactionsPerAccount = 5;
}
