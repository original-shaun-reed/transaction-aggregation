package za.co.reed.mocksourceservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * Represents a raw bank feed record — mimics a simplified OFX / Open Banking JSON entry.
 * This is the unprocessed payload before normalisation.
 *
 * Key fields:
 * <ul>
 *   <li><b>transactionId</b> — Unique identifier for the transaction in the source feed</li>
 *   <li><b>accountId</b> — Identifier of the account associated with the transaction</li>
 *   <li><b>merchantName</b> — Merchant or payee name as provided by the source</li>
 *   <li><b>amount</b> — Transaction amount in the source currency</li>
 *   <li><b>currency</b> — ISO 4217 currency code</li>
 *   <li><b>transactedAt</b> — Timestamp when the transaction occurred</li>
 *   <li><b>status</b> — Raw lifecycle state from the source: SETTLED | PENDING | REVERSED</li>
 * </ul>
 */
public record BankFeedRecord(
        String transactionId,
        String accountId,
        String merchantName,
        BigDecimal amount,
        Currency currency,
        Instant transactedAt,
        String status
) {}
