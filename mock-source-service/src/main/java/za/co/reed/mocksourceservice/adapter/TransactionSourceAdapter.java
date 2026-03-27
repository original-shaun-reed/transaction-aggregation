package za.co.reed.mocksourceservice.adapter;

import za.co.reed.commom.dto.NormalisedTransaction;

import java.util.List;

/**
 * Contract that every mock data source must implement.
 *
 * The ingestor calls normalise() on whatever raw payload the source produces.
 * Validate and deduplicate are called by the adapter itself before returning.
 */
public interface TransactionSourceAdapter {

    /**
     * Convert the raw source payload into a list of NormalisedTransactions.
     * Implementations are responsible for calling validate() and deduplicate()
     * before returning.
     */
    List<NormalisedTransaction> normalise(Object rawPayload);

    /**
     * Validate a normalised transaction — throws IllegalArgumentException
     * if required fields are missing or values are out of range.
     */
    void validate(NormalisedTransaction transaction);

    /**
     * Return true if this transaction has already been seen (duplicate).
     * Implementations use an in-memory set keyed on sourceId.
     */
    boolean isDuplicate(String sourceId);
}
