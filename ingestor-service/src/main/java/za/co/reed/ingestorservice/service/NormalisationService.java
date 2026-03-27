package za.co.reed.ingestorservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Business-level validation applied to every inbound NormalisedTransaction
 * before it enters the deduplication and Kafka publish pipeline.
 *
 * The NormalisedTransaction compact constructor already enforces structural
 * invariants (non-null fields, positive amounts). This service adds rules
 * that require business context to evaluate:
 *
 *   - No future-dated transactions (allow 5-minute clock skew)
 *   - No transactions older than 7 days (stale data guard)
 *   - Amount ceiling per transaction (configurable fraud guard)
 *   - REVERSED transactions must not have suspiciously large amounts
 *
 * Throws IllegalArgumentException on validation failure — IngestorService
 * catches this and returns IngestResult.FAILED without publishing to Kafka.
 */
@Slf4j
@Service
public class NormalisationService {

    /** Allow up to 5 minutes of clock skew for "future" transactions. */
    private static final long CLOCK_SKEW_MINUTES = 5;

    /** Transactions older than 7 days are considered stale and rejected. */
    private static final long MAX_AGE_DAYS = 7;

    /** Hard ceiling per transaction — flag anything above this for review. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000.00");


    // Just an extra layer for validation
    public void validate(NormalisedTransaction transaction) {
        validateTimestamp(transaction);
        validateAmount(transaction);
        validateReversedConsistency(transaction);

        log.debug("Transaction validated — sourceId={}", transaction.sourceId());
    }

    private void validateTimestamp(NormalisedTransaction transaction) {
        Instant now = Instant.now();
        Instant allowedFuture = now.plus(CLOCK_SKEW_MINUTES, ChronoUnit.MINUTES);
        Instant oldestAllowed = now.minus(MAX_AGE_DAYS, ChronoUnit.DAYS);

        if (transaction.transactedAt().isAfter(allowedFuture)) {
            throw new IllegalArgumentException("transactedAt is in the future beyond clock skew tolerance: " + transaction.transactedAt());
        }

        if (transaction.transactedAt().isBefore(oldestAllowed)) {
            throw new IllegalArgumentException("transactedAt is older than " + MAX_AGE_DAYS + " days: " + transaction.transactedAt());
        }
    }

    private void validateAmount(NormalisedTransaction transaction) {
        if (transaction.amount().compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("amount exceeds maximum allowed: " + transaction.amount() + " > " + MAX_AMOUNT);
        }
    }

    private void validateReversedConsistency(NormalisedTransaction transaction) {
        // A REVERSED transaction with a suspiciously large amount warrants
        // a warning — it's not rejected, but logged for monitoring
        if (transaction.status() == TransactionStatus.REVERSED && transaction.amount().compareTo(new BigDecimal("50000.00")) > 0) {
            log.warn("Large REVERSED transaction — sourceId={} amount={} currency={}", transaction.sourceId(), transaction.amount(),
                    transaction.currency());
        }
    }
}
