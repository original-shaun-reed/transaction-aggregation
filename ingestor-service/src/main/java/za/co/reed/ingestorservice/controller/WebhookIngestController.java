package za.co.reed.ingestorservice.controller;

import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.ingestorservice.config.helper.HmacSignatureFilter;
import za.co.reed.ingestorservice.dto.IngestResponse;
import za.co.reed.ingestorservice.enums.IngestResult;
import za.co.reed.ingestorservice.service.IngestorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives NormalisedTransactions pushed by mock-sources via HTTP.
 *
 * Two source types call this endpoint:
 *   - Payment processor (push model) — fires directly on each event
 *   - Bank feed + card network — the ingestor's SchedulerConfig polls
 *     those sources and calls IngestorService directly, bypassing this controller
 *
 * Security:
 *   Every inbound request must carry a valid HMAC-SHA256 signature in the
 *   X-Transaction-Aggregator-Signature header. IngestorSecurityConfig validates it
 *   before this method is called. Requests with invalid/missing signatures
 *   are rejected with 401 before reaching this controller.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/webhook")
@RequiredArgsConstructor
public class WebhookIngestController {

    private final IngestorService ingestorService;

    /**
     * POST /api/internal/webhook/payment
     *
     * Accepts a single NormalisedTransaction per call.
     * Returns:
     *   202 Accepted  — transaction accepted for processing (may still be deduped)
     *   400 Bad Request — payload failed validation
     *   401 Unauthorized — HMAC signature missing or invalid
     *   500 Internal Server Error — Kafka publish failure
     */
    @PostMapping("/payment")
    public ResponseEntity<IngestResponse> ingestPayment(@RequestHeader(HmacSignatureFilter.SIGNATURE_HEADER) String signature,
                                                        @Valid @RequestBody NormalisedTransaction transaction) {
        log.debug("Webhook received — sourceId={} sourceType={}", transaction.sourceId(), transaction.sourceType());

        IngestResult result = ingestorService.ingest(transaction);

        return switch (result) {
            case ACCEPTED  -> ResponseEntity.accepted()
                    .body(new IngestResponse("accepted", transaction.sourceId()));
            case DUPLICATE -> ResponseEntity.accepted()
                    .body(new IngestResponse("duplicate", transaction.sourceId()));
            case FAILED    -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new IngestResponse("failed", transaction.sourceId()));
        };
    }
}
