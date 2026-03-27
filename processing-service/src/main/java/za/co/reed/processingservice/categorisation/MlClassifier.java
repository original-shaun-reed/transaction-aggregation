package za.co.reed.processingservice.categorisation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.persistence.entity.Category;
import za.co.reed.persistence.repository.CategoryRepository;
import za.co.reed.processingservice.dto.MlRequest;
import za.co.reed.processingservice.dto.MlResponse;
import java.util.Optional;

/**
 * ML-based categorisation fallback.
 *
 * Called by CategorizationService only when the RulesEngine returns empty.
 * Sends the merchant name and optional MCC to an external model endpoint
 * and maps the response back to a Category.
 *
 * Disabled entirely when categorisation.ml-classifier.enabled=false —
 * in that case CategorizationService falls straight through to "uncategorised".
 *
 * Timeout is set aggressively at 500ms — category inference is not worth
 * slowing down the Kafka consumer thread. On timeout or error, Optional.empty()
 * is returned and the caller uses the uncategorised fallback.
 *
 * For the mock, any 200 response with a "categoryCode" field is accepted.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "categorisation.ml-classifier.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MlClassifier {
    private final RestTemplate restTemplate;
    private final CategoryRepository categoryRepository;
    private final MlClassifierProperties properties;

    /**
     * Call the ML model to classify a transaction.
     *
     * @param transaction the transaction to classify
     * @return matched Category, or empty on timeout, error, or unknown category code
     */
    public Optional<Category> classify(NormalisedTransaction transaction) {
        try {
            MlRequest request = new MlRequest(transaction.merchantName(), transaction.merchantMcc(),
                    transaction.amount().doubleValue(), transaction.currency().getCurrencyCode());

            MlResponse response = restTemplate.postForObject(properties.getEndpoint(), request, MlResponse.class);
            if (response == null || response.categoryCode() == null) {
                log.debug("ML classifier returned null response — sourceId={}", transaction.sourceId());
                return Optional.empty();
            }

            Optional<Category> category = categoryRepository.findByCode(response.categoryCode());
            if (category.isEmpty()) {
                log.warn("ML classifier returned unknown category code '{}' — sourceId={}",
                        response.categoryCode(), transaction.sourceId());
            }

            log.debug("ML classifier result — sourceId={} category={} confidence={}", transaction.sourceId(),
                    response.categoryCode(), response.confidence());

            return category;
        } catch (ResourceAccessException e) {
            // Timeout or connection refused — degrade gracefully
            log.warn("ML classifier timed out or unreachable — sourceId={} endpoint={}",
                    transaction.sourceId(), properties.getEndpoint());
            return Optional.empty();
        } catch (Exception e) {
            log.error("ML classifier call failed — sourceId={}", transaction.sourceId(), e);
            return Optional.empty();
        }
    }
}
