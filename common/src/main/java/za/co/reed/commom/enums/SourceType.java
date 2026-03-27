package za.co.reed.commom.enums;

/**
 * Identifies which mock data source produced a transaction.
 *
 * Used as a tag on every NormalisedTransaction and as a Kafka message header
 * so downstream consumers can route or filter by source without deserialising
 * the full payload.
 */
public enum SourceType {

    /** Scheduled pull from a bank Open Banking / OFX-style REST endpoint. */
    BANK_FEED,

    /** Push via webhook from a Stripe-like payment processor. */
    PAYMENT_PROCESSOR,

    /** Batch poll from a Visa/Mastercard-style card network settlement file. */
    CARD_NETWORK
}
