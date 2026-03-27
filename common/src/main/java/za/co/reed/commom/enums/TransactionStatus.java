package za.co.reed.commom.enums;

/**
 * Lifecycle status of a financial transaction.
 *
 * Maps across all three source types:
 *
 *   Bank feed:           SETTLED | PENDING | REVERSED  (raw string from source)
 *   Payment processor:   payment_intent.succeeded → SETTLED
 *                        payment_intent.created   → PENDING
 *                        charge.refunded          → REVERSED
 *   Card network:        CLEARED → SETTLED
 *                        AUTH    → PENDING
 *                        REVERSED → REVERSED
 */
public enum TransactionStatus {

    /**
     * Transaction is authorised but not yet cleared/captured.
     * May still be cancelled or expire.
     */
    PENDING,

    /**
     * Transaction is fully cleared and settled.
     * This is the terminal positive state — safe to include in aggregations.
     */
    SETTLED,

    /**
     * Transaction has been reversed, refunded, or voided.
     * Should be excluded from spend totals but retained for audit.
     */
    REVERSED
}
