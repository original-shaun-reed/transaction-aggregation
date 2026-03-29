package za.co.reed.apiservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import za.co.reed.apiservice.controller.advice.ApiControllerAdvice;
import za.co.reed.apiservice.controller.impl.AggregationControllerImpl;
import za.co.reed.apiservice.dto.response.AggregationResponse;
import za.co.reed.apiservice.service.AggregationService;
import za.co.reed.commom.enums.PeriodType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.apiservice.service.ComparisonService;
import za.co.reed.persistence.repository.AggregationRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(value = AggregationControllerImpl.class, includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ApiControllerAdvice.class))
class AggregationControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        AggregationRepository testAggregationRepository;

        @MockBean
        private AggregationService testAggregationService;

        @MockBean
        private ComparisonService testComparisonService;

        private final UUID testAccountId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        @Test
        @WithMockUser
        void summary_shouldReturnAggregationSummary() throws Exception {
                // Given
                AggregationResponse testSummary = AggregationResponse.builder()
                                .categoryCode("GROCERIES")
                                .categoryLabel("Groceries")
                                .totalSpend(new BigDecimal("500.00"))
                                .totalReversed(BigDecimal.ZERO)
                                .transactionCount(10L)
                                .build();

                List<AggregationResponse> testSummaries = Collections.singletonList(testSummary);

                when(testAggregationService.summary(eq(testAccountId.toString()),
                                eq(PeriodType.MONTHLY), any(Date.class), any(Date.class)))
                                .thenReturn(org.springframework.http.ResponseEntity.ok()
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(testSummaries));

                // When & Then
                mockMvc.perform(get("/api/v1/aggregations/{accountId}/summary", testAccountId)
                                .param("periodType", "MONTHLY")
                                .param("from", "2026-03-01")
                                .param("to", "2026-03-31"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$[0].categoryCode").value("GROCERIES"))
                                .andExpect(jsonPath("$[0].categoryLabel").value("Groceries"))
                                .andExpect(jsonPath("$[0].totalSpend").value(500.00))
                                .andExpect(jsonPath("$[0].transactionCount").value(10));
        }

        @Test
        @WithMockUser
        void timeSeries_shouldReturnTimeSeriesData() throws Exception {
                // Given
                AggregationResponse testSummary = AggregationResponse.builder()
                                .categoryCode("DAILY")
                                .categoryLabel("2026-03-01")
                                .totalSpend(new BigDecimal("100.00"))
                                .transactionCount(5L)
                                .build();

                List<AggregationResponse> testTimeSeries = Collections.singletonList(testSummary);

                when(testAggregationService.timeSeries(eq(testAccountId.toString()),
                                eq(PeriodType.DAILY), any(Date.class), any(Date.class)))
                                .thenReturn(org.springframework.http.ResponseEntity.ok()
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(testTimeSeries));

                // When & Then
                mockMvc.perform(get("/api/v1/aggregations/{accountId}/time-series", testAccountId)
                                .param("periodType", "DAILY")
                                .param("from", "2026-03-01")
                                .param("to", "2026-03-07"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$[0].categoryCode").value("DAILY"))
                                .andExpect(jsonPath("$[0].totalSpend").value(100.00));
        }

        @Test
        @WithMockUser
        void topMerchants_shouldReturnTopMerchants() throws Exception {
                // Given
                AggregationResponse testSummary = AggregationResponse.builder()
                                .categoryCode("MERCHANT_123")
                                .categoryLabel("Supermarket")
                                .totalSpend(new BigDecimal("1000.00"))
                                .transactionCount(20L)
                                .build();

                List<AggregationResponse> testTopMerchants = Collections.singletonList(testSummary);

                when(testAggregationService.topMerchants(eq(testAccountId.toString()),
                                eq(TransactionStatus.SETTLED), any(Date.class), any(Date.class), eq(10)))
                                .thenReturn(org.springframework.http.ResponseEntity.ok()
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(testTopMerchants));

                // When & Then
                mockMvc.perform(get("/api/v1/aggregations/{accountId}/top-merchants", testAccountId)
                                .param("status", "SETTLED")
                                .param("from", "2026-03-01")
                                .param("to", "2026-03-31")
                                .param("limit", "10"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$[0].categoryLabel").value("Supermarket"))
                                .andExpect(jsonPath("$[0].totalSpend").value(1000.00));
        }

        @Test
        @WithMockUser
        void byCategory_shouldReturnCategoryBreakdown() throws Exception {
                // Given
                AggregationResponse testSummary = AggregationResponse.builder()
                                .categoryCode("GROCERIES")
                                .categoryLabel("Groceries")
                                .totalSpend(new BigDecimal("500.00"))
                                .transactionCount(10L)
                                .build();

                List<AggregationResponse> testByCategory = Collections.singletonList(testSummary);

                // Mock the service to return the List directly (not ResponseEntity)
                when(testAggregationService.summary(
                                any(String.class),
                                nullable(PeriodType.class),
                                any(Date.class),
                                any(Date.class)))
                                .thenReturn(ResponseEntity.ok()
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(testByCategory));

                // When & Then
                mockMvc.perform(get("/api/v1/aggregations/{accountId}/by-category", testAccountId)
                                .param("from", "2026-03-01")
                                .param("to", "2026-03-31"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$[0].categoryCode").value("GROCERIES"))
                                .andExpect(jsonPath("$[0].totalSpend").value(500.00))
                                .andExpect(jsonPath("$[0].transactionCount").value(10));
        }

        @Test
        @WithMockUser
        void summary_shouldReturn400WhenMissingParameters() throws Exception {
                mockMvc.perform(get("/api/v1/aggregations/{accountId}/summary", testAccountId))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        void compare_shouldReturn400WhenInvalidRequestBody() throws Exception {
                String testInvalidRequestBody = """
                                {
                                    "currentFrom": "invalid-date",
                                    "currentTo": "2026-03-31"
                                }
                                """;

                mockMvc.perform(post("/api/v1/aggregations/{accountId}/compare", testAccountId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(testInvalidRequestBody)
                                .with(csrf()))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        void timeSeries_shouldReturn400WhenInvalidDateRange() throws Exception {
                mockMvc.perform(get("/api/v1/aggregations/{accountId}/time-series", testAccountId)
                                .param("periodType", "DAILY")
                                .param("from", "invalid-date")
                                .param("to", "2026-03-31"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void unauthenticatedRequest_shouldReturn401() throws Exception {
                mockMvc.perform(get("/api/v1/aggregations/{accountId}/summary", testAccountId)
                                .param("periodType", "MONTHLY")
                                .param("from", "2026-03-01")
                                .param("to", "2026-03-31"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        void summary_shouldReturn500WhenServiceThrowsException() throws Exception {
                when(testAggregationService.summary(any(String.class), any(PeriodType.class), any(Date.class),
                                any(Date.class)))
                                .thenThrow(new RuntimeException("Service error"));

                mockMvc.perform(get("/api/v1/aggregations/{accountId}/summary", testAccountId)
                                .param("periodType", "MONTHLY")
                                .param("from", "2026-03-01")
                                .param("to", "2026-03-31"))
                                .andExpect(status().is5xxServerError());
        }

        @Test
        @WithMockUser
        void topMerchants_shouldUseDefaultLimit() throws Exception {
                // Given
                List<AggregationResponse> testEmptyList = Collections.emptyList();
                when(testAggregationService.topMerchants(eq(testAccountId.toString()),
                                eq(TransactionStatus.SETTLED), any(Date.class), any(Date.class), eq(10)))
                                .thenReturn(org.springframework.http.ResponseEntity.ok()
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(testEmptyList));

                // When & Then - Should use default limit of 10 when not specified
                mockMvc.perform(get("/api/v1/aggregations/{accountId}/top-merchants", testAccountId)
                                .param("status", "SETTLED")
                                .param("from", "2026-03-01")
                                .param("to", "2026-03-31"))
                                .andExpect(status().isOk());
        }
}