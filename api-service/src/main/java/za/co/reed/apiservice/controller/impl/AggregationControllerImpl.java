package za.co.reed.apiservice.controller.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import za.co.reed.apiservice.controller.AggregationController;
import za.co.reed.apiservice.dto.request.ComparisonRequest;
import za.co.reed.apiservice.dto.response.AggregationResponse;
import za.co.reed.apiservice.dto.response.compare.ComparisonResponse;
import za.co.reed.apiservice.service.AggregationService;
import za.co.reed.apiservice.service.ComparisonService;
import za.co.reed.commom.enums.PeriodType;
import za.co.reed.commom.enums.TransactionStatus;

import java.util.Date;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AggregationControllerImpl implements AggregationController {
    private final AggregationService aggregationService;
    private final ComparisonService comparisonService;

    @Override
    public ResponseEntity<List<AggregationResponse>> summary(String accountId, PeriodType periodType, Date from,
            Date to) {
        return aggregationService.summary(accountId, periodType, from, to);
    }

    @Override
    public ResponseEntity<ComparisonResponse> compare(String accountId, ComparisonRequest request) {
        return comparisonService.periodOverPeriodComparison(request);
    }

    @Override
    public ResponseEntity<List<AggregationResponse>> timeSeries(String accountId, PeriodType periodType, Date from,
            Date to) {
        return aggregationService.timeSeries(accountId, periodType, from, to);
    }

    @Override
    public ResponseEntity<List<AggregationResponse>> topMerchants(String accountId, TransactionStatus status, Date from,
            Date to, int limit) {
        return aggregationService.topMerchants(accountId, status, from, to, limit);
    }

    @Override
    public ResponseEntity<List<AggregationResponse>> byCategory(String accountId, Date from, Date to) {
        return aggregationService.summary(accountId, null, from, to);
    }
}
