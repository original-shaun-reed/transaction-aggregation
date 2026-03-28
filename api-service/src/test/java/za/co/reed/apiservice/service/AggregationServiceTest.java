package za.co.reed.apiservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import za.co.reed.apiservice.builder.CacheKeyBuilder;
import za.co.reed.apiservice.config.properties.CacheConfigProperties;
import za.co.reed.apiservice.dto.response.AggregationResponse;
import za.co.reed.apiservice.exception.ApiInternalServerErrorException;
import za.co.reed.commom.enums.PeriodType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.persistence.entity.Aggregation;
import za.co.reed.persistence.entity.Category;
import za.co.reed.persistence.entity.Transaction;
import za.co.reed.persistence.repository.AggregationRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    @Mock
    private AggregationRepository testAggregationRepository;

    @Mock
    private RedisCacheService testCacheService;

    @Mock
    private CacheKeyBuilder testCacheKeyBuilder;

    @Mock
    private CacheConfigProperties testCacheConfigProperties;

    @Mock
    private TransactionService testTransactionService;

    @InjectMocks
    private AggregationService testAggregationService;

    private String testAccountId;
    private PeriodType testPeriodType;
    private Date testFromDate;
    private Date testToDate;
    private TransactionStatus testStatus;
    private int testLimit;
    private Aggregation testAggregation;
    private Category testCategory;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        testAccountId = "acc-123";
        testPeriodType = PeriodType.DAILY;
        testFromDate = new Date(System.currentTimeMillis() - 86400000L); // yesterday
        testToDate = new Date();
        testStatus = TransactionStatus.SETTLED;
        testLimit = 10;

        testCategory = new Category();
        testCategory.setId(1L);
        testCategory.setCode("GROCERIES");
        testCategory.setLabel("Groceries");

        testAggregation = Aggregation.builder()
                .id(1L)
                .accountId(testAccountId)
                .periodType(testPeriodType)
                .periodDate(LocalDate.now())
                .category(testCategory)
                .totalSpend(new BigDecimal("150.75"))
                .transactionCount(5L)
                .totalReversed(new BigDecimal("10.50"))
                .build();

        testTransaction = new Transaction();
        testTransaction.setId(1L);
        testTransaction.setAccountId(testAccountId);
        testTransaction.setAmount(new BigDecimal("99.99"));
        testTransaction.setMerchantName("Test Merchant");
        testTransaction.setMerchantMcc("5411");
        testTransaction.setStatus(testStatus);
    }

    @Test
    void summary_shouldThrowApiInternalServerErrorExceptionOnError() {
        // Given
        String testCacheKey = "aggregationSummary:acc-123:DAILY:" + testFromDate.getTime() + ":"
                + testToDate.getTime();

        when(testCacheKeyBuilder.getKey("aggregationSummary", testAccountId, testPeriodType, testFromDate,
                testToDate))
                .thenReturn(testCacheKey);
        when(testCacheService.get(testCacheKey, new TypeReference<List<AggregationResponse>>() {
        }))
                .thenThrow(new RuntimeException("Cache error"));

        // When & Then
        assertThrows(ApiInternalServerErrorException.class,
                () -> testAggregationService.summary(testAccountId, testPeriodType, testFromDate,
                        testToDate));
    }

    @Test
    void timeSeries_shouldThrowApiInternalServerErrorExceptionOnError() {
        // Given
        String testCacheKey = "timeSeries:acc-123:DAILY:" + testFromDate.getTime() + ":" + testToDate.getTime();

        when(testCacheKeyBuilder.getKey("timeSeries", testAccountId, testPeriodType, testFromDate, testToDate))
                .thenReturn(testCacheKey);
        when(testCacheService.get(testCacheKey, new TypeReference<List<AggregationResponse>>() {}))
                .thenThrow(new RuntimeException("Cache error"));

        // When & Then
        assertThrows(ApiInternalServerErrorException.class, () -> testAggregationService.timeSeries(testAccountId, testPeriodType, testFromDate,
                        testToDate));
    }

    @Test
    void topMerchants_shouldThrowApiInternalServerErrorExceptionOnError() {
        // Given
        String testCacheKey = "topMerchants:acc-123:SETTLED:" + testFromDate.getTime() + ":" + testToDate.getTime()
                + ":10";

        when(testCacheKeyBuilder.getKey("topMerchants", testAccountId, testStatus, testFromDate, testToDate,
                testLimit))
                .thenReturn(testCacheKey);
        when(testCacheService.get(testCacheKey, new TypeReference<List<AggregationResponse>>() {
        }))
                .thenThrow(new RuntimeException("Cache error"));

        // When & Then
        assertThrows(ApiInternalServerErrorException.class,
                () -> testAggregationService.topMerchants(testAccountId, testStatus, testFromDate,
                        testToDate, testLimit));
    }

    @Test
    void categorySummary_shouldReturnAggregatedData() {
        // Given
        when(testAggregationRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.singletonList(testAggregation));

        // When
        List<AggregationResponse> testResult = testAggregationService.categorySummary(
                testAccountId, testPeriodType, testFromDate, testToDate);

        // Then
        assertThat(testResult).hasSize(1);
        AggregationResponse testResponse = testResult.get(0);
        assertThat(testResponse.getCategoryCode()).isEqualTo("GROCERIES");
        assertThat(testResponse.getCategoryLabel()).isEqualTo("Groceries");
        assertThat(testResponse.getTotalSpend()).isEqualTo(new BigDecimal("150.75"));
        assertThat(testResponse.getTransactionCount()).isEqualTo(5L);
        assertThat(testResponse.getTotalReversed()).isEqualTo(new BigDecimal("10.50"));
    }

    @Test
    void categorySummary_shouldThrowExceptionOnError() {
        // Given
        when(testAggregationRepository.findAll(any(Specification.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(RuntimeException.class,
                () -> testAggregationService.categorySummary(testAccountId, testPeriodType, testFromDate,
                        testToDate));
    }

    @Test
    void timeSeriesSummary_shouldReturnTimeSeriesData() {
        // Given
        when(testAggregationRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.singletonList(testAggregation));

        // When
        List<AggregationResponse> testResult = testAggregationService.timeSeriesSummary(
                testAccountId, testPeriodType, testFromDate, testToDate);

        // Then
        assertThat(testResult).hasSize(1);
        AggregationResponse testResponse = testResult.get(0);
        assertThat(testResponse.getTotalSpend()).isEqualTo(new BigDecimal("150.75"));
        assertThat(testResponse.getTransactionCount()).isEqualTo(5L);
        assertThat(testResponse.getPeriod()).isNotNull();
    }

    @Test
    void timeSeriesSummary_shouldThrowExceptionOnError() {
        // Given
        when(testAggregationRepository.findAll(any(Specification.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(RuntimeException.class,
                () -> testAggregationService.timeSeriesSummary(testAccountId, testPeriodType, testFromDate,
                        testToDate));
    }

    @Test
    void topMerchantsSummary_shouldThrowExceptionOnError() {
        // Given
        when(testTransactionService.getTransactions(testAccountId, testStatus, testFromDate, testToDate, testLimit))
                .thenThrow(new RuntimeException("Service error"));

        // When & Then
        assertThrows(RuntimeException.class,
                () -> testAggregationService.topMerchantsSummary(testAccountId, testStatus, testFromDate,
                        testToDate, testLimit));
    }

    @Test
    void categorySummary_shouldReturnEmptyListWhenNoData() {
        // Given
        when(testAggregationRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.emptyList());

        // When
        List<AggregationResponse> testResult = testAggregationService.categorySummary(
                testAccountId, testPeriodType, testFromDate, testToDate);

        // Then
        assertThat(testResult).isEmpty();
    }

    @Test
    void timeSeriesSummary_shouldReturnEmptyListWhenNoData() {
        // Given
        when(testAggregationRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.emptyList());

        // When
        List<AggregationResponse> testResult = testAggregationService.timeSeriesSummary(
                testAccountId, testPeriodType, testFromDate, testToDate);

        // Then
        assertThat(testResult).isEmpty();
    }

    @Test
    void topMerchantsSummary_shouldHandleTransactionsWithoutMerchantInfo() {
        // Given
        Transaction testTransactionWithoutMerchant = new Transaction();
        testTransactionWithoutMerchant.setId(2L);
        testTransactionWithoutMerchant.setAccountId(testAccountId);
        testTransactionWithoutMerchant.setAmount(new BigDecimal("50.00"));
        testTransactionWithoutMerchant.setStatus(testStatus);
        // No merchant name or MCC

        when(testTransactionService.getTransactions(testAccountId, testStatus, testFromDate, testToDate, testLimit))
                .thenReturn(Collections.singletonList(testTransactionWithoutMerchant));

        // When
        List<AggregationResponse> testResult = testAggregationService.topMerchantsSummary(
                testAccountId, testStatus, testFromDate, testToDate, testLimit);

        // Then
        assertThat(testResult).isEmpty(); // Should be filtered out
    }

    @Test
    void topMerchantsSummary_shouldHandleMultipleTransactionsSameMerchant() {
        // Given
        Transaction testTransaction1 = new Transaction();
        testTransaction1.setId(1L);
        testTransaction1.setAccountId(testAccountId);
        testTransaction1.setAmount(new BigDecimal("50.00"));
        testTransaction1.setMerchantName("Same Merchant|5411");
        testTransaction1.setMerchantMcc("5411");
        testTransaction1.setStatus(testStatus);

        Transaction testTransaction2 = new Transaction();
        testTransaction2.setId(2L);
        testTransaction2.setAccountId(testAccountId);
        testTransaction2.setAmount(new BigDecimal("75.00"));
        testTransaction2.setMerchantName("Same Merchant|5411");
        testTransaction2.setMerchantMcc("5411");
        testTransaction2.setStatus(testStatus);

        List<Transaction> testTransactions = Arrays.asList(testTransaction1, testTransaction2);
        when(testTransactionService.getTransactions(testAccountId, testStatus, testFromDate, testToDate, testLimit))
                .thenReturn(testTransactions);

        // When
        List<AggregationResponse> testResult = testAggregationService.topMerchantsSummary(
                testAccountId, testStatus, testFromDate, testToDate, testLimit);

        // Then
        assertThat(testResult).hasSize(1);
        AggregationResponse testResponse = testResult.get(0);
        assertThat(testResponse.getTotalSpend()).isEqualTo(new BigDecimal("125.00")); // 50 + 75
        assertThat(testResponse.getTransactionCount()).isEqualTo(2L);
    }
}