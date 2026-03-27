package za.co.reed.mocksourceservice.scheduler;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.mocksourceservice.adapter.BankFeedAdapter;
import za.co.reed.mocksourceservice.client.IngestorClient;
import za.co.reed.mocksourceservice.dto.BankFeedRecord;
import za.co.reed.mocksourceservice.generator.BankFeedDataGenerator;
import za.co.reed.mocksourceservice.properties.BankFeedProperties;

/**
 * Scheduled pull source — simulates an Open Banking poller.
 *
 * Every pollIntervalMs milliseconds:
 *   1. BankFeedDataGenerator produces a fresh batch of raw records
 *   2. BankFeedAdapter normalises and deduplicates them
 *   3. Each NormalisedTransaction is POSTed to the ingestor webhook endpoint
 *
 * Disabled entirely when sources.bank-feed.enabled=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sources.bank-feed.enabled", havingValue = "true", matchIfMissing = true)
public class BankFeedScheduler {

    private final BankFeedDataGenerator generator;
    private final BankFeedAdapter adapter;
    private final BankFeedProperties properties;
    private final IngestorClient ingestorClient;

    /**
     * Fixed-delay so the next poll only starts after the previous one completes.
     * Using fixedDelayString to allow the value to come from config.
     */
    @Scheduled(fixedDelayString = "${sources.bank-feed.poll-interval-ms:30000}")
    public void poll() {
        log.info("Bank feed poll starting — accounts={} txPerAccount={}", properties.getAccountsPerBatch(),
                properties.getTransactionsPerAccount());

        List<BankFeedRecord> rawRecords = generator.generate(properties.getAccountsPerBatch(), properties.getTransactionsPerAccount());
        List<NormalisedTransaction> normalised = adapter.normalise(rawRecords);

        log.info("Bank feed produced {} transactions ({} raw, {} deduped out)", normalised.size(), rawRecords.size(),
                rawRecords.size() - normalised.size());

        for (NormalisedTransaction transaction : normalised) {
            try {
                ingestorClient.send(transaction);
            } catch (Exception e) {
                log.error("Failed to send bank feed transaction {} to ingestor", transaction.sourceId(), e);
            }
        }
    }
}
