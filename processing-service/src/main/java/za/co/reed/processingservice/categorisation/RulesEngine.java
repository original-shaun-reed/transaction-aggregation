package za.co.reed.processingservice.categorisation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.persistence.entity.Category;
import za.co.reed.persistence.repository.CategoryRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic rule-based categorisation engine.
 *
 * Applied BEFORE the ML classifier — if a rule matches, the ML call is skipped.
 * Rules are evaluated in priority order:
 *
 *   1. MCC code match  — highest confidence, direct ISO 18245 lookup
 *   2. Keyword match   — merchant name contains a category keyword
 *
 * Results are cached with @Cacheable to avoid repeated DB lookups for
 * the same MCC or merchant name patterns within a JVM session.
 *
 * Returns Optional.empty() if no rule matches, signalling CategorizationService
 * to fall back to the ML classifier.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RulesEngine {

    private final CategoryRepository categoryRepository;

    /**
     * Attempt to categorise a transaction using deterministic rules.
     *
     * @param transaction the transaction to categorise
     * @return the matched Category, or empty if no rule matched
     */
    public Optional<Category> categorise(NormalisedTransaction transaction) {

        // Rule 1: MCC code match (card network records only)
        if (StringUtils.hasText(transaction.merchantMcc())) {
            Optional<Category> byMcc = matchByMcc(transaction.merchantMcc());
            if (byMcc.isPresent()) {
                log.debug("MCC rule matched — sourceId={} mcc={} category={}", transaction.sourceId(),
                        transaction.merchantMcc(), byMcc.get().getCode());

                return byMcc;
            }
        }

        // Rule 2: Keyword match on merchant name
        Optional<Category> byKeyword = matchByKeyword(transaction.merchantName());
        if (byKeyword.isPresent()) {
            log.debug("Keyword rule matched — sourceId={} merchant={} category={}", transaction.sourceId(),
                    transaction.merchantName(), byKeyword.get().getCode());
            return byKeyword;
        }

        log.debug("No rule matched — sourceId={} mcc={} merchant={}", transaction.sourceId(), transaction.merchantMcc(), transaction.merchantName());
        return Optional.empty();
    }

    /*
    * Method below are public for caching
    * */

    @Cacheable(value = "categoryByMcc", key = "#mcc")
    public Optional<Category> matchByMcc(String mcc) {
        List<Category> matches = categoryRepository.findByMccCodesLike(mcc);

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        if (matches.size() > 1) {
            log.warn("Multiple categories matched MCC {} — using first: {}", mcc, matches.get(0).getCode());
        }

        return Optional.of(matches.get(0));
    }

    @Cacheable(value = "categoryByKeyword", key = "#merchantName.toLowerCase()")
    public Optional<Category> matchByKeyword(String merchantName) {
         if (!StringUtils.hasText(merchantName)) {
            return Optional.empty();
        }

        String lowerMerchant = merchantName.toLowerCase();
        List<Category> withKeywords = categoryRepository.findAllByKeywordsNotNull();

        return withKeywords.stream()
                .filter(category -> hasMatchingKeyword(category, lowerMerchant))
                .findFirst();
    }

    private boolean hasMatchingKeyword(Category category, String lowerMerchant) {
        String keywords = category.getKeywords();
        if (!StringUtils.hasText(keywords)) {
            return false;
        }

        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(kw -> lowerMerchant.contains(kw));
    }
}
