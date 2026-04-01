package za.co.reed.processingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.processingservice.categorisation.MlClassifier;
import za.co.reed.processingservice.categorisation.RulesEngine;
import za.co.reed.persistence.entity.Category;
import za.co.reed.persistence.entity.Transaction;
import za.co.reed.persistence.repository.CategoryRepository;

import java.util.Optional;

/**
 * Orchestrates the three-tier categorisation strategy for each transaction:
 *
 *   Tier 1 — Rules engine (MCC + keyword matching)
 *             Fast, deterministic, no external calls.
 *             Handles the majority of card network records.
 *
 *   Tier 2 — ML classifier (optional, external REST call)
 *             Called only when the rules engine returns empty.
 *             Disabled in local dev via categorisation.ml-classifier.enabled=false.
 *
 *   Tier 3 — Uncategorised fallback
 *             Always succeeds. No transaction is ever left without a category.
 *
 * The result is set on the Transaction entity directly.
 * Persistence is handled by AggregationWorker after this method returns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategorisationService {

    private final RulesEngine rulesEngine;
    private final Optional<MlClassifier> mlClassifier;  // Optional — may not be in context
    private final CategoryRepository categoryRepository;

    /**
     * Categorise a transaction and set its Category field.
     *
     * @param transaction the persisted Transaction entity to categorise
     * @param source      the original NormalisedTransaction with merchant details
     */
    public void categorise(Transaction transaction, NormalisedTransaction source) {

        // Tier 1: Rules engine
        Optional<Category> category = rulesEngine.categorise(source);

        // Tier 2: ML fallback (if enabled and rules didn't match)
        if (category.isEmpty() && mlClassifier.isPresent()) {
            category = mlClassifier.get().classify(source);
        }

        // Tier 3: Uncategorised fallback — always succeeds
        Category resolved = category.orElseGet(() -> {
            log.debug("Using uncategorised fallback — sourceId={}", source.sourceId());
            return categoryRepository.uncategorised();
        });

        transaction.setCategory(resolved);

        log.info("Transaction categorised — sourceId={} category={} tier={}",
                source.sourceId(), resolved.getCode(), determineTier(category));
    }

    public Category retreiveCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
    }

    private String determineTier(Optional<Category> rulesResult) {
        if (rulesResult.isPresent()) {
            return "rules";
        }

        if (mlClassifier.isPresent()) {
            return "ml";
        }

        return "fallback";
    }
}
