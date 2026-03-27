package za.co.reed.ingestorservice.config;


import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.ingestorservice.enums.IngestResult;
import za.co.reed.ingestorservice.service.IngestorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Scheduled pollers for pull-based mock sources.
 *
 * The payment processor pushes via webhook — no poller needed.
 * The bank feed and card network expose REST endpoints that this
 * service polls on a fixed schedule.
 *
 * Each poller:
 *   1. GETs the latest batch from the mock source endpoint
 *   2. Feeds each transaction through IngestorService (validate → dedup → publish)
 *   3. Logs a summary of accepted / duplicate / failed counts
 *
 * Uses fixedDelayString so the interval only starts after the previous
 * poll completes — prevents overlap if a poll takes longer than expected.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SchedulerConfig {

    private final IngestorService ingestorService;
    private final RestTemplate restTemplate;

    @Value("${sources.bank-feed.url:http://localhost:8081/api/bankfeed/latest}")
    private String bankFeedUrl;

    @Value("${sources.card-network.url:http://localhost:8081/batch/latest}")
    private String cardNetworkUrl;

    // ── Bank feed poller ──────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${sources.bank-feed.poll-interval-ms:30000}")
    public void pollBankFeed() {
        log.debug("Polling bank feed — url={}", bankFeedUrl);
        poll("bank-feed", bankFeedUrl);
    }

    // ── Card network poller ───────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${sources.card-network.poll-interval-ms:300000}")
    public void pollCardNetwork() {
        log.debug("Polling card network — url={}", cardNetworkUrl);
        poll("card-network", cardNetworkUrl);
    }

    private void poll(String sourceName, String url) {
        try {
            ResponseEntity<List<NormalisedTransaction>> response = restTemplate.exchange(url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});

            if (response.getStatusCode().value() == 204 || response.getBody() == null) {
                log.debug("{} poll returned no content", sourceName);
                return;
            }

            List<NormalisedTransaction> transactions = response.getBody();
            int accepted = 0;
            int duplicate = 0;
            int failed = 0;

            for (NormalisedTransaction transaction: transactions) {
                IngestResult result = ingestorService.ingest(transaction);
                switch (result) {
                    case ACCEPTED  -> accepted++;
                    case DUPLICATE -> duplicate++;
                    case FAILED    -> failed++;
                }
            }

            log.info("{} poll complete — total={} accepted={} duplicate={} failed={}", sourceName, transactions.size(),
                    accepted, duplicate, failed);
        } catch (Exception e) {
            log.error("{} poll failed — url={}", sourceName, url, e);
        }
    }
}
