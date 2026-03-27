package za.co.reed.mocksourceservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.mocksourceservice.adapter.CardNetworkAdapter;
import za.co.reed.mocksourceservice.client.IngestorClient;
import za.co.reed.mocksourceservice.dto.BatchSnapshot;
import za.co.reed.mocksourceservice.dto.CardRecord;
import za.co.reed.mocksourceservice.generator.CardBatchGenerator;
import za.co.reed.mocksourceservice.properties.CardNetworkProperties;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardBatchService {

    private final CardBatchGenerator generator;
    private final CardNetworkAdapter adapter;
    private final CardNetworkProperties properties;
    private final IngestorClient ingestorClient;

    // Volatile ensures safe publication across threads
    private volatile BatchSnapshot currentBatch = BatchSnapshot.empty();

    public List<NormalisedTransaction> getLatest() {
        return currentBatch.records();
    }

    public void regenerateBatch() {
        log.info("Regenerating card network batch — records={}", properties.getRecordsPerBatch());

        List<CardRecord> raw = generator.generate(properties.getRecordsPerBatch());
        List<NormalisedTransaction> normalised = adapter.normalise(raw);

        currentBatch = new BatchSnapshot(normalised, Instant.now());

        log.info("Batch ready: {} records ({} raw, {} deduped out)",
                normalised.size(), raw.size(), raw.size() - normalised.size());

        normalised.forEach((NormalisedTransaction transaction) -> {
            try {
                ingestorClient.send(transaction);
            } catch (Exception e) {
                log.error("Failed to send record {} to ingestor", transaction.sourceId(), e);
            }
        });
    }
}
