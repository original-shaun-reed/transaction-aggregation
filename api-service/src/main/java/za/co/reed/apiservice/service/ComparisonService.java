package za.co.reed.apiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import za.co.reed.apiservice.builder.CacheKeyBuilder;
import za.co.reed.apiservice.config.properties.CacheConfigProperties;
import za.co.reed.apiservice.dto.request.ComparisonRequest;
import za.co.reed.apiservice.dto.response.AggregationResponse;
import za.co.reed.apiservice.dto.response.compare.*;
import za.co.reed.apiservice.enums.Direction;
import za.co.reed.commom.enums.PeriodType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {
    private final AggregationService aggregationService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final RedisCacheService cacheService;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final CacheConfigProperties cacheProperties;

    public ResponseEntity<ComparisonResponse> periodOverPeriodComparison(ComparisonRequest request) {
        String cacheKey = cacheKeyBuilder.getKey("periodOverPeriodComparison", request.hashCode());
        Optional<ComparisonResponse> cachedResponse = cacheService.get(cacheKey, ComparisonResponse.class);

        if (cachedResponse.isPresent()) {
            log.info("Cache hit for period-over-period comparison — request={}", request);
            return ResponseEntity.ok(cachedResponse.get());
        }

        ComparisonResponse response = getComparisonResponse(request);
        cacheService.put(cacheKey, response, Duration.ofSeconds(cacheProperties.getCompare()));
        return ResponseEntity.ok(response);
    }

    private ComparisonResponse getComparisonResponse(ComparisonRequest request) {

        // Using CompletableFuture to fetch current and previous period summaries in parallel
        CompletableFuture<List<AggregationResponse>> currentFuture = CompletableFuture.supplyAsync(() ->
                aggregationService.categorySummary(request.getAccountId(), request.getPeriodType(),
                        request.getCurrentFrom(), request.getCurrentTo())
        );

        CompletableFuture<List<AggregationResponse>> previousFuture = CompletableFuture.supplyAsync(() ->
                aggregationService.categorySummary(request.getAccountId(), request.getPeriodType(),
                        request.resolvedPreviousFrom(), request.resolvedPreviousTo())
        );

        CompletableFuture.allOf(currentFuture, previousFuture).join();

        List<AggregationResponse> currentRows  = currentFuture.join();
        List<AggregationResponse> previousRows = previousFuture.join();

        // Build period summaries
        BigDecimal currentTotal = sum(currentRows);
        BigDecimal previousTotal = sum(previousRows);
        Long currentCount = count(currentRows);
        Long previousCount = count(previousRows);


        PeriodSummary current = new PeriodSummary(request.getCurrentFrom(),  request.getCurrentTo(),  currentTotal, currentCount);
        PeriodSummary previous = new PeriodSummary(request.resolvedPreviousFrom(), request.resolvedPreviousTo(), previousTotal, previousCount);
        Delta delta = computeDelta(currentTotal, previousTotal, currentCount, previousCount);
        List<CategoryBreakdown> categoryBreakdowns = getCategoryBreakdowns(currentRows, previousRows, currentTotal);

        List<Anomaly> anomalies = new ArrayList<>();
        if (request.isIncludeAnomalies()) {
            anomalies.addAll(anomalyDetectionService.detectAnomalies(categoryBreakdowns));
        }

        List<TimeSeriesPoint> timeSeries = buildTimeSeries(request.getCurrentFrom(), request.getCurrentTo(),
                request.resolvedPreviousFrom(), request.resolvedPreviousTo(), request.getAccountId(),
                request.getPeriodType());


        return ComparisonResponse.builder()
                .current(current)
                .previous(previous)
                .delta(delta)
                .byCategory(categoryBreakdowns)
                .timeSeries(timeSeries)
                .anomalies(anomalies)
                .meta(new Meta(false, cacheProperties.getCompare(), new Date()))
                .build();
    }

    private List<CategoryBreakdown> getCategoryBreakdowns(List<AggregationResponse> currentSummaries,
                                                          List<AggregationResponse> previousSummaries,
                                                          BigDecimal currentTotal) {
        Map<String, AggregationResponse> preAggregationSummaryMap = previousSummaries.stream()
                .collect(Collectors.toMap(AggregationResponse::getCategoryCode, r -> r));

        return currentSummaries.stream()
                .map((AggregationResponse current) -> buildCategoryBreakdown(preAggregationSummaryMap, current, currentTotal))
                .sorted(Comparator.comparing(CategoryBreakdown::getCurrentSpend).reversed())
                .toList();
    }

    private CategoryBreakdown buildCategoryBreakdown(Map<String, AggregationResponse> preAggregationSummaryMap,
                                                     AggregationResponse current, BigDecimal currentTotal) {
        AggregationResponse previous = preAggregationSummaryMap.getOrDefault(current.getCategoryCode(), null);
        BigDecimal previousSpend = previous != null ? previous.getTotalSpend() : BigDecimal.ZERO;
        BigDecimal deltaPercentage = deltaPercentage(previousSpend, current.getTotalSpend());

        return CategoryBreakdown.builder()
                .categoryId(current.getCategoryCode())
                .label(current.getCategoryLabel())
                .currentSpend(current.getTotalSpend())
                .previousSpend(previousSpend)
                .deltaPct(deltaPercentage)
                .shareOfTotalCurrent(sharePercentage(current.getTotalSpend(), currentTotal))
                .build();
    }

    private List<TimeSeriesPoint> buildTimeSeries(Date currentFrom, Date currentTo, Date previousFrom, Date previousTo,
                                                  String accountId, PeriodType periodType) {

        List<AggregationResponse> currentSeries = aggregationService.timeSeriesSummary(accountId, periodType, currentFrom, currentTo);
        List<AggregationResponse> previousSeries = aggregationService.timeSeriesSummary(accountId, periodType, previousFrom, previousTo);

        Map<Date, BigDecimal> previousSeriesMap = previousSeries.stream()
                .collect(Collectors.toMap((AggregationResponse summary) ->
                        summary.getPeriod(), summary -> summary.getTotalSpend()));

        return currentSeries.stream()
                .map(row ->
                        new TimeSeriesPoint(
                        row.getPeriod(),
                        row.getTotalSpend(),
                        previousSeriesMap.getOrDefault(row.getPeriod(), BigDecimal.ZERO)
                ))
                .toList();
    }


    private BigDecimal sum(List<AggregationResponse> summaries) {
        return summaries.stream()
                .map(AggregationResponse::getTotalSpend)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Long count(List<AggregationResponse> summaries) {
        return summaries.stream()
                .mapToLong(AggregationResponse::getTransactionCount)
                .sum();
    }

    private Direction direction(BigDecimal absolute) {
        if (absolute.compareTo(BigDecimal.ZERO) > 0) {
            return Direction.INCREASE;
        }

        if (absolute.compareTo(BigDecimal.ZERO) < 0) {
            return Direction.DECREASE;
        }

        return Direction.FLAT;
    }

    private Delta computeDelta(BigDecimal current, BigDecimal previous, Long currentCount, Long previousCount) {
        BigDecimal absolute = current.subtract(previous);
        BigDecimal spendPercentage = spendPercentage(absolute, previous);
        Direction direction = direction(absolute);

        return Delta.builder()
                .absoluteSpend(absolute.setScale(2, RoundingMode.HALF_UP))
                .spendPercentage(spendPercentage)
                .countAbsolute(currentCount - previousCount)
                .direction(direction)
                .build();
    }

    private BigDecimal spendPercentage(BigDecimal absolute, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return absolute.divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)
                        .setScale(2, RoundingMode.HALF_UP));
    }

    /*
        Delta Percentage Formula:

        Formula:
            (totalSpend - previousSpend) / previousSpend * 100

        Steps:
        1. Subtract previousSpend from totalSpend → difference
        2. Divide difference by previousSpend → relative change
        3. Multiply by 100 → convert to percentage
        4. Round to 2 decimal places → final display value

        Example:
            totalSpend = 1200
            previousSpend = 1000
            Result = ((1200 - 1000) / 1000) * 100 = 20.00%
    */
    private BigDecimal deltaPercentage(BigDecimal previousSpend, BigDecimal totalSpend) {
        if (previousSpend.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalSpend.subtract(previousSpend)
                .divide(previousSpend, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sharePercentage(BigDecimal totalSpend, BigDecimal currentTotal) {
        if (currentTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalSpend.divide(currentTotal, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
