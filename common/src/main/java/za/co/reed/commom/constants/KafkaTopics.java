package za.co.reed.commom.constants;

/**
 * Central registry of all Kafka topic names used across LedgerFlow modules.
 *
 * Keeping topic names here as constants means:
 *   - A typo in one service can't silently produce to the wrong topic.
 *   - Renaming a topic is a single-file change with compiler-enforced propagation.
 *   - @KafkaListener and KafkaTopicConfig @Bean definitions reference the same value.
 *
 * Usage:
 *   @KafkaListener(topics = KafkaTopics.RAW_TRANSACTIONS)
 *   producer.send(KafkaTopics.CATEGORISED_TRANSACTIONS, key, payload);
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /**
     * All inbound transactions from mock sources, after normalisation.
     * Produced by: ingestor-service
     * Consumed by: processing-service (RawTransactionConsumer)
     */
    public static final String RAW_TRANSACTIONS = "raw-transactions";

    /**
     * Transactions after categorisation and enrichment.
     * Produced by: processing-service (CategorisedTransactionProducer)
     * Consumed by: any downstream analytics consumer (future)
     */
    public static final String CATEGORISED_TRANSACTIONS = "categorised-transactions";

    /**
     * Dead letter queue — messages that failed processing after all retries.
     * Produced by: processing-service retry config
     * Consumed by: processing-service (DeadLetterConsumer) for alerting/manual review
     */
    public static final String RAW_TRANSACTIONS_DLQ = "raw-transactions.dlq";

    /**
     * Partition count for the raw-transactions topic.
     * Higher partition count = more parallelism in the processing-service consumer group.
     * Should match or exceed the number of processing-service instances.
     */
    public static final int RAW_TRANSACTIONS_PARTITIONS = 6;

    /**
     * Partition count for the categorised-transactions topic.
     */
    public static final int CATEGORISED_TRANSACTIONS_PARTITIONS = 6;

    /**
     * Replication factor — set to 1 for local dev (single broker).
     * Override to 3 in production (AWS MSK default).
     */
    public static final short REPLICATION_FACTOR_LOCAL = 1;
    public static final short REPLICATION_FACTOR_PROD  = 3;
}
