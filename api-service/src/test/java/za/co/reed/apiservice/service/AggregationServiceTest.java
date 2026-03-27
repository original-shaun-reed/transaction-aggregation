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
    private AggregationRepository aggregationRepository;

    @Mock
    private RedisCacheService cacheService;

    @Mock
    private CacheKeyBuilder cacheKeyBuilder;

    @Mock
    private CacheConfigProperties cacheConfigProperties;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private AggregationService aggregationService;

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
        String cacheKey = "aggregationSummary:acc-123:DAILY:" + testFromDate.getTime() + ":"
                + testToDate.getTime();

        when(cacheKeyBuilder.getKey("aggregationSummary", testAccountId, testPeriodType, testFromDate,
                testToDate))
                .thenReturn(cacheKey);
        when(cacheService.get(cacheKey, new TypeReference<List<AggregationResponse>>() {
        }))
                .thenThrow(new RuntimeException("Cache error"));

        // When & Then
        assertThrows(ApiInternalServerErrorException.class,
                () -> aggregationService.summary(testAccountId, testPeriodType, testFromDate,
                        testToDate));
    }

    @Test
    void timeSeries_shouldThrowApiInternalServerErrorExceptionOnError() {
        // Given
        String cacheKey = "timeSeries:acc-123:DAILY:" + testFromDate.getTime() + ":" + testToDate.getTime();

        when(cacheKeyBuilder.getKey("timeSeries", testAccountId, testPeriodType, testFromDate, testToDate))
                .thenReturn(cacheKey);
        when(cacheService.get(cacheKey, new TypeReference<List<AggregationResponse>>() {}))
                .thenThrow(new RuntimeException("Cache error"));

        // When & Then
        assertThrows(ApiInternalServerErrorException.class, () -> aggregationService.timeSeries(testAccountId, testPeriodType, testFromDate,
                        testToDate));
    }

    @Test
    void topMerchants_shouldThrowApiInternalServerErrorExceptionOnError() {
        // Given
        String cacheKey = "topMerchants:acc-123:SETTLED:" + testFromDate.getTime() + ":" + testToDate.getTime()
                + ":10";

        when(cacheKeyBuilder.getKey("topMerchants", testAccountId, testStatus, testFromDate, testToDate,
                testLimit))
                .thenReturn(cacheKey);
        when(cacheService.get(cacheKey, new TypeReference<List<AggregationResponse>>() {
        }))
                .thenThrow(new RuntimeException("Cache error"));

        // When & Then
        assertThrows(ApiInternalServerErrorException.class,
                () -> aggregationService.topMerchants(testAccountId, testStatus, testFromDate,
                        testToDate, testLimit));
    }

    @Test
    void categorySummary_shouldReturnAggregatedData() {
        // Given
        when(aggregationRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.singletonList(testAggregation));

        // When
        List<AggregationResponse> result = aggregationService.categorySummary(
                testAccountId, testPeriodType, testFromDate, testToDate);

        // Then
        assertThat(result).hasSize(1);
        AggregationResponse response = result.get(0);
        assertThat(response.getCategoryCode()).isEqualTo("GROCERIES");
        assertThat(response.getCategoryLabel()).isEqualTo("Groceries");
        assertThat(response.getTotalSpend()).isEqualTo(new BigDecimal("150.75"));
        assertThat(response.getTransactionCount()).isEqualTo(5L);
        assertThat(response.getTotalReversed()).isEqualTo(new BigDecimal("10.50"));
    }

    @Test
    void categorySummary_shouldThrowExceptionOnError() {
        // Given
        when(aggregationRepository.findAll(any(Specification.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(RuntimeException.class,
                () -> aggregationService.categorySummary(testAccountId, testPeriodType, testFromDate,
                        testToDate));
    }

    @Test
    void timeSeriesSummary_shouldReturnTimeSeriesData() {
        // Given
        when(aggregationRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.singletonList(testAggregation));

        // When
        List<AggregationResponse> result = aggregationService.timeSeriesSummary(
                testAccountId, testPeriodType, testFromDate, testToDate);

        // Then
        assertThat(result).hasSize(1);
        AggregationResponse response = result.get(0);
        assertThat(response.getTotalSpend()).isEqualTo(new BigDecimal("150.75"));
        assertThat(response.getTransactionCount()).isEqualTo(5L);
        assertThat(response.getPeriod()).isNotNull();
    }

    @Test
    void timeSeriesSummary_shouldThrowExceptionOnError() {
        // Given
        when(aggregationRepository.findAll(any(Specification.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(RuntimeException.class,
                () -> aggregationService.timeSeriesSummary(testAccountId, testPeriodType, testFromDate,
                        testToDate));
    }

    @Test
    void topMerchantsSummary_shouldThrowExceptionOnError() {
        // Given
        when(transactionService.getTransactions(testAccountId, testStatus, testFromDate, testToDate, testLimit))
                .thenThrow(new RuntimeException("Service error"));

        // When & Then
        assertThrows(RuntimeException.class,
                () -> aggregationService.topMerchantsSummary(testAccountId, testStatus, testFromDate,
                        testToDate, testLimit));
    }

    @Test
    void categorySummary_shouldReturnEmptyListWhenNoData() {
        // Given
        when(aggregationRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.emptyList());

        // When
        List<AggregationResponse> result = aggregationService.categorySummary(
                testAccountId, testPeriodType, testFromDate, testToDate);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void timeSeriesSummary_shouldReturnEmptyListWhenNoData() {
        // Given
        when(aggregationRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.emptyList());

        // When
        List<AggregationResponse> result = aggregationService.timeSeriesSummary(
                testAccountId, testPeriodType, testFromDate, testToDate);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void topMerchantsSummary_shouldHandleTransactionsWithoutMerchantInfo() {
        // Given
        Transaction transactionWithoutMerchant = new Transaction();
        transactionWithoutMerchant.setId(2L);
        transactionWithoutMerchant.setAccountId(testAccountId);
        transactionWithoutMerchant.setAmount(new BigDecimal("50.00"));
        transactionWithoutMerchant.setStatus(testStatus);
        // No merchant name or MCC

        when(transactionService.getTransactions(testAccountId, testStatus, testFromDate, testToDate, testLimit))
                .thenReturn(Collections.singletonList(transactionWithoutMerchant));

        // When
        List<AggregationResponse> result = aggregationService.topMerchantsSummary(
                testAccountId, testStatus, testFromDate, testToDate, testLimit);

        // Then
        assertThat(result).isEmpty(); // Should be filtered out
    }

    @Test
    void topMerchantsSummary_shouldHandleMultipleTransactionsSameMerchant() {
        // Given
        Transaction transaction1 = new Transaction();
        transaction1.setId(1L);
        transaction1.setAccountId(testAccountId);
        transaction1.setAmount(new BigDecimal("50.00"));
        transaction1.setMerchantName("Same Merchant|5411");
        transaction1.setMerchantMcc("5411");
        transaction1.setStatus(testStatus);

        Transaction transaction2 = new Transaction();
        transaction2.setId(2L);
        transaction2.setAccountId(testAccountId);
        transaction2.setAmount(new BigDecimal("75.00"));
        transaction2.setMerchantName("Same Merchant|5411");
        transaction2.setMerchantMcc("5411");
        transaction2.setStatus(testStatus);

        List<Transaction> transactions = Arrays.asList(transaction1, transaction2);
        when(transactionService.getTransactions(testAccountId, testStatus, testFromDate, testToDate, testLimit))
                .thenReturn(transactions);

        // When
        List<AggregationResponse> result = aggregationService.topMerchantsSummary(
                testAccountId, testStatus, testFromDate, testToDate, testLimit);

        // Then
        assertThat(result).hasSize(1);
        AggregationResponse response = result.get(0);
        assertThat(response.getTotalSpend()).isEqualTo(new BigDecimal("125.00")); // 50 + 75
        assertThat(response.getTransactionCount()).isEqualTo(2L);
    }
}