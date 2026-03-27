package za.co.reed.commom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import za.co.reed.commom.constants.KafkaTopics;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * Canonical transaction record produced by every source adapter and
 * published to the {@link KafkaTopics#RAW_TRANSACTIONS} Kafka topic.
 *
 * This is the single shared contract between:
 *   - mock-sources     (producer)
 *   - ingestor-service (validates and publishes)
 *   - processing-service (consumes and categorises)
 *
 * Design decisions:
 *   - Java 21 record: immutable, no boilerplate, structural equality built in.
 *   - BigDecimal for amount: never use double/float for money.
 *   - Currency (ISO 4217) not String: compiler-enforced valid currency codes.
 *   - rawPayload stored verbatim: enables full replay without going back to source.
 *   - merchantMcc is nullable: bank feed and payment processor don't provide it;
 *     the categorisation engine infers it from merchant name as a fallback.
 */
public record NormalisedTransaction(

        /**
         * Original ID from the source system.
         * Used as the Kafka message key and for Redis-based deduplication.
         * Format varies by source:
         *   Bank feed:          "BF-{uuid}"
         *   Payment processor:  "evt_{stripeEventId}"
         *   Card network:       "CN-{uuid}"
         */
        @NotBlank
        @JsonProperty("source_id")
        String sourceId,

        /** Which source produced this record. */
        @NotNull
        @JsonProperty("source_type")
        SourceType sourceType,

        /**
         * Account identifier scoped to the source:
         *   Bank feed:          account number (e.g. "ACC-0001")
         *   Payment processor:  Stripe customer ID (e.g. "cus_001")
         *   Card network:       PAN last four digits (e.g. "4242")
         */
        @NotBlank
        @JsonProperty("account_id")
        String accountId,

        /**
         * Transaction amount in major currency units (not cents/pence).
         * Always positive — direction is conveyed by {@link TransactionStatus}.
         * REVERSED transactions retain their original positive amount.
         */
        @NotNull
        @Positive
        @JsonProperty("amount")
        BigDecimal amount,

        /** ISO 4217 currency. */
        @NotNull
        @JsonProperty("currency")
        Currency currency,

        /** Human-readable merchant name as received from the source. */
        @NotBlank
        @JsonProperty("merchant_name")
        String merchantName,

        /**
         * ISO 18245 Merchant Category Code.
         * Populated directly by the card network source.
         * Null for bank feed and payment processor — the categorisation
         * engine infers the category from merchantName in those cases.
         */
        @JsonProperty("merchant_mcc")
        String merchantMcc,

        /** When the transaction occurred at the merchant (not settlement time). */
        @NotNull
        @JsonProperty("transacted_at")
        Instant transactedAt,

        /** Current lifecycle status of the transaction. */
        @NotNull
        @JsonProperty("status")
        TransactionStatus status,

        /**
         * Full original payload from the source, serialised as JSON.
         * Stored verbatim in the transactions table for audit and replay.
         * Never used for business logic — always derive from the typed fields above.
         */
        @JsonProperty("raw_payload")
        String rawPayload

) {
    /**
     * Compact constructor — validates invariants at construction time
     * so an invalid NormalisedTransaction can never exist in the system.
     */
    public NormalisedTransaction {
        if (sourceId == null || sourceId.isBlank())
            throw new IllegalArgumentException("sourceId must not be blank");
        if (sourceType == null)
            throw new IllegalArgumentException("sourceType must not be null");
        if (accountId == null || accountId.isBlank())
            throw new IllegalArgumentException("accountId must not be blank");
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("amount must be positive, got: " + amount);
        if (currency == null)
            throw new IllegalArgumentException("currency must not be null");
        if (merchantName == null || merchantName.isBlank())
            throw new IllegalArgumentException("merchantName must not be blank");
        if (transactedAt == null)
            throw new IllegalArgumentException("transactedAt must not be null");
        if (status == null)
            throw new IllegalArgumentException("status must not be null");
    }
}
