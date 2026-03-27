package za.co.reed.apiservice.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.apiservice.builder.CacheKeyBuilder;
import za.co.reed.commom.enums.PeriodType;
import za.co.reed.apiservice.config.properties.CacheConfigProperties;
import za.co.reed.apiservice.dto.response.AggregationResponse;
import za.co.reed.apiservice.exception.ApiInternalServerErrorException;
import za.co.reed.apiservice.specification.AggregationSpecification;
import za.co.reed.persistence.entity.Aggregation;
import za.co.reed.persistence.entity.Transaction;
import za.co.reed.persistence.repository.AggregationRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregationService {
    private final AggregationRepository aggregationRepository;
    private final RedisCacheService cacheService;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final CacheConfigProperties cacheConfigProperties;
    private final TransactionService transactionService;

    public ResponseEntity<List<AggregationResponse>> summary(String accountId, PeriodType periodType, Date from, Date to) {
        try {
            String cacheKey = cacheKeyBuilder.getKey("aggregationSummary", accountId, periodType, from, to);

            Optional<List<AggregationResponse>> aggregation = cacheService.get(cacheKey, new TypeReference<>() {});
            if (aggregation.isPresent()) {
                log.info("Cache hit for getKey summary — accountId={} periodType={} from={} to={}", accountId,
                        periodType, from, to);
                return ResponseEntity.ok(aggregation.get());
            }

            log.info("Cache miss for getKey summary — accountId={} periodType={} from={} to={}", accountId, periodType,
                    from, to);
            List<AggregationResponse> summary = categorySummary(accountId, periodType, from, to);
            cacheService.put(cacheKey, summary, Duration.ofSeconds(cacheConfigProperties.getSummary()));
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error fetching getKey summary — accountId={} periodType={} from={} to={}", accountId, periodType,
                    from, to, e);
            throw new ApiInternalServerErrorException("Error retrieving summary for account: " + accountId);
        }
    }

    public ResponseEntity<List<AggregationResponse>> timeSeries(String accountId, PeriodType periodType, Date from, Date to) {
        try {
            String cacheKey = cacheKeyBuilder.getKey("timeSeries", accountId, periodType, from, to);

            Optional<List<AggregationResponse>> aggregation = cacheService.get(cacheKey, new TypeReference<>() {});
            if (aggregation.isPresent()) {
                log.info("Cache hit for getKey time series — accountId={} periodType={} from={} to={}", accountId, periodType, from, to);
                return ResponseEntity.ok(aggregation.get());
            }

            log.info("Cache miss for getKey time series — accountId={} periodType={} from={} to={}", accountId,
                    periodType, from, to);
            List<AggregationResponse> summaries = timeSeriesSummary(accountId, periodType, from, to);
            cacheService.put(cacheKey, summaries, Duration.ofSeconds(cacheConfigProperties.getTimeSeries()));
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            log.error("Error fetching time series — accountId={} periodType={} from={} to={}", accountId, periodType, from, to, e);
            throw new ApiInternalServerErrorException("Error retrieving time series for: " + accountId);
        }
    }

    public ResponseEntity<List<AggregationResponse>> topMerchants(String accountId, TransactionStatus status, Date from,
                                                                  Date to, int limit) {
        try {
            String cacheKey = cacheKeyBuilder.getKey("topMerchants", accountId, status, from, to, limit);

            Optional<List<AggregationResponse>> aggregation = cacheService.get(cacheKey, new TypeReference<>() {});
            if (aggregation.isPresent()) {
                log.info("Cache hit for top merchants — accountId={} status={} from={} to={} limit={}", accountId, status, from, to, limit);
                return ResponseEntity.ok(aggregation.get());
            }

            log.info("Cache miss for top merchants — accountId={} status={} from={} to={} limit={}", accountId, status,
                    from, to, limit);
            List<AggregationResponse> summaries = topMerchantsSummary(accountId, status, from, to, limit);
            cacheService.put(cacheKey, summaries, Duration.ofSeconds(cacheConfigProperties.getTopMerchants()));
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            log.error("Error fetching top merchants — accountId={} status={} from={} to={} limit={}", accountId, status,
                    from, to, limit, e);
            throw new ApiInternalServerErrorException("Error retrieving top merchants for: " + accountId);
        }
    }

    public List<AggregationResponse> categorySummary(String accountId, PeriodType periodType, Date from, Date to) {
        try {
            Specification<Aggregation> specification = getSpecification(accountId, periodType, from, to);
            List<Aggregation> aggregations = aggregationRepository.findAll(specification);
            return aggregate(aggregations);
        } catch (Exception e) {
            log.error("Error fetching getKey summary — accountId={} periodType={} from={} to={}", accountId, periodType,
                    from, to, e);
            throw e;
        }
    }

    public List<AggregationResponse> timeSeriesSummary(String accountId, PeriodType periodType, Date from, Date to) {
        try {
            Specification<Aggregation> specification = getSpecification(accountId, periodType, from, to);
            List<Aggregation> aggregations = aggregationRepository.findAll(specification);
            return timeSeriesAggregationSummaries(aggregations);
        } catch (Exception e) {
            log.error("Error fetching getKey time series — accountId={} periodType={} from={} to={}", accountId,
                    periodType, from, to, e);
            throw e;
        }
    }

    public List<AggregationResponse> topMerchantsSummary(String accountId, TransactionStatus status, Date from, Date to,
            int limit) {
        try {
            List<Transaction> transactions = transactionService.getTransactions(accountId, status, from, to, limit);
            return topMerchantAggregationSummaries(transactions);
        } catch (Exception e) {
            log.error("Error fetching top merchants — accountId={} status={} from={} to={} limit={}", accountId, status,
                    from, to, limit, e);
            throw e;
        }
    }

    private Specification<Aggregation> getSpecification(String accountExternalId, PeriodType periodType, Date from,
            Date to) {
        Specification<Aggregation> specification = null;

        if (accountExternalId != null) {
            specification = AggregationSpecification.accountExternalId(accountExternalId);
        }

        if (periodType != null) {
            Specification<Aggregation> periodTypeSpecification = AggregationSpecification.periodType(periodType);
            specification = specification == null ? periodTypeSpecification
                    : specification.and(periodTypeSpecification);
        }

        if (Objects.nonNull(from) && Objects.nonNull(to)) {
            Specification<Aggregation> dateRangeSpecification = AggregationSpecification.periodDateBetween(from, to);
            specification = specification == null ? dateRangeSpecification : specification.and(dateRangeSpecification);
        } else if (Objects.nonNull(from) || Objects.nonNull(to)) {
            if (Objects.nonNull(from)) {
                Specification<Aggregation> fromSpecification = AggregationSpecification
                        .periodDateGreaterThanOrEqualTo(from);
                specification = specification == null ? fromSpecification : specification.and(fromSpecification);
            } else if (Objects.nonNull(to)) {
                Specification<Aggregation> toSpecification = AggregationSpecification.periodDateLessThanOrEqualTo(to);
                specification = specification == null ? toSpecification : specification.and(toSpecification);
            }
        }

        return specification;
    }

    private List<AggregationResponse> aggregate(List<Aggregation> aggregations) {
        return aggregations.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCategory().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                group -> {
                                    Aggregation first = group.get(0);
                                    return buildAggregationSummary(group, first);
                                })))
                .values().stream()
                .sorted(Comparator.comparing(AggregationResponse::getTotalSpend).reversed())
                .toList();
    }

    private List<AggregationResponse> timeSeriesAggregationSummaries(List<Aggregation> aggregations) {
        Map<LocalDate, List<Aggregation>> grouped = new HashMap<>();

        // Step 1: Group by periodDate
        for (Aggregation agg : aggregations) {
            LocalDate key = agg.getPeriodDate();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(agg);
        }

        // Build summaries for each group
        List<AggregationResponse> responses = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Aggregation>> entry : grouped.entrySet()) {
            List<Aggregation> aggregationList = entry.getValue();
            AggregationResponse response = buildTimeSeriesAggregationSummary(aggregationList, new Date());
            responses.add(response);
        }

        // Sort by period
        responses.sort(Comparator.comparing(AggregationResponse::getPeriod));

        return responses;
    }

    private List<AggregationResponse> topMerchantAggregationSummaries(List<Transaction> transactions) {
        // Group transactions by merchantName|merchantMcc
        Map<String, List<Transaction>> grouped = new HashMap<>();

        for (Transaction t : transactions) {
            if (t.getMerchantName() != null && t.getMerchantMcc() != null) {
                String key = t.getMerchantName() + "|" + t.getMerchantMcc();
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
            }
        }

        // Build summaries for each group
        List<AggregationResponse> responses = new ArrayList<>();
        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            List<Transaction> transactionList = entry.getValue();
            AggregationResponse response = topMerchantAggregationSummary(transactionList);
            responses.add(response);
        }

        // Sort by totalSpend descending
        responses.sort(Comparator.comparing(AggregationResponse::getTotalSpend).reversed());
        return responses;
    }

    /**
     * Builds an AggregationResponse summary from a list of Aggregation objects.
     *
     * @param aggregationList the list of Aggregation entries for a given
     *                        category/period
     * @param aggregation     a representative Aggregation used to extract category
     *                        metadata
     * @return an AggregationResponse containing totals and category info
     */
    private AggregationResponse buildAggregationSummary(List<Aggregation> aggregationList, Aggregation aggregation) {
        // Calculate the total spend by summing all non-null spend values
        BigDecimal totalSpend = aggregationList.stream()
                .map(Aggregation::getTotalSpend)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count total transactions by summing transactionCount values (treat null as 0)
        Long transactionCount = aggregationList.stream()
                .mapToLong(a -> a.getTransactionCount() != null ? a.getTransactionCount() : 0L)
                .sum();

        // Calculate the total reversed amount by summing all non-null reversed values
        BigDecimal totalReversed = aggregationList.stream()
                .map(Aggregation::getTotalReversed)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build and return the response object with category info and computed totals
        return AggregationResponse.builder()
                .categoryCode(aggregation.getCategory().getCode())
                .categoryLabel(aggregation.getCategory().getLabel())
                .totalSpend(totalSpend)
                .transactionCount(transactionCount)
                .totalReversed(totalReversed)
                .build();
    }

    /**
     * Builds a time-series aggregation summary for a given period.
     *
     * @param aggregations the list of Aggregation entries for the period
     * @param period       the LocalDate/Date representing the time period
     * @return an AggregationResponse containing totals for the period
     */
    private AggregationResponse buildTimeSeriesAggregationSummary(List<Aggregation> aggregations, Date period) {
        // Calculate the total spend by summing all non-null spend values
        BigDecimal totalSpend = aggregations.stream()
                .map(Aggregation::getTotalSpend)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count total transactions by summing transactionCount values (treat null as 0)
        Long transactionCount = aggregations.stream()
                .mapToLong(a -> a.getTransactionCount() != null ? a.getTransactionCount() : 0L)
                .sum();

        // Build and return the response object with period info and computed totals
        return AggregationResponse.builder()
                .period(period) // the time period this summary represents
                .totalSpend(totalSpend) // aggregated spend for the period
                .transactionCount(transactionCount) // aggregated transaction count
                .build();
    }

    /**
     * Builds a merchant-level aggregation summary from a list of transactions.
     *
     * @param transactions the list of transactions belonging to the same merchant
     * @return an AggregationResponse containing merchant info and totals
     */
    private AggregationResponse topMerchantAggregationSummary(List<Transaction> transactions) {
        // Calculate the total spend by summing all non-null transaction amounts
        BigDecimal totalSpend = transactions.stream()
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count total transactions (simply the size of the list)
        Long transactionCount = Long.valueOf(transactions.size());

        // Build and return the response object with merchant info and computed totals
        // Extract merchant name and MCC from the first transaction
        String[] merchantInfo = transactions.get(0).getMerchantName().split("\\|");

        String merchantName = merchantInfo[0];
        String merchantMcc = null;
        if (merchantInfo.length > 1) {
            merchantMcc = merchantInfo[1];
        }

        return AggregationResponse.builder()
                .categoryLabel(merchantName) // merchant name
                .categoryCode(StringUtils.isBlank(merchantMcc) ? StringUtils.EMPTY : merchantMcc) // merchant MCC code
                .totalSpend(totalSpend) // aggregated spend for this merchant
                .transactionCount(transactionCount) // number of transactions
                .build();
    }
}
