package za.co.reed.apiservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import za.co.reed.apiservice.controller.advice.ApiControllerAdvice;
import za.co.reed.apiservice.controller.impl.TransactionControllerImpl;
import za.co.reed.apiservice.dto.response.DataResponse;
import za.co.reed.apiservice.dto.response.TransactionResponse;
import za.co.reed.apiservice.exception.ApiInternalServerErrorException;
import za.co.reed.apiservice.service.TransactionService;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = TransactionControllerImpl.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ApiControllerAdvice.class)
)
class TransactionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private TransactionService testTransactionService;

  private final UUID testTransactionId = UUID.fromString("323e4567-e89b-12d3-a456-426614174002");
  private final UUID testAccountId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
  private final UUID testCategoryId = UUID.fromString("423e4567-e89b-12d3-a456-426614174003");

  @Test
  @WithMockUser
  void list_shouldReturnPaginatedTransactions() throws Exception {
    // Given
    TransactionResponse testTransactionResponse = TransactionResponse.builder()
        .id(testTransactionId)
        .sourceId("source-123")
        .sourceType(SourceType.BANK_FEED)
        .accountId(testAccountId.toString())
        .amount(new BigDecimal("150.75"))
        .currency("ZAR")
        .merchantName("Supermarket")
        .merchantMcc("5411")
        .transactedAt(Instant.parse("2026-03-15T10:30:00Z"))
        .status(TransactionStatus.SETTLED)
        .categoryId(testCategoryId)
        .categoryCode("GROCERIES")
        .categoryLabel("Groceries")
        .createdAt(Instant.parse("2026-03-15T10:35:00Z"))
        .build();

    DataResponse<TransactionResponse> mockResponse = DataResponse.<TransactionResponse>builder()
        .data(Collections.singletonList(testTransactionResponse))
        .totalCount(1L)
        .page(0)
        .pageSize(20)
        .hasMore(false)
        .build();

    when(testTransactionService.list(
        anyString(), any(TransactionStatus.class), any(Date.class), any(Date.class),
        anyInt(), anyInt(), anyString(), anyString()))
        .thenReturn(ResponseEntity.ok(mockResponse));

    // When & Then
    mockMvc.perform(get("/api/v1/transactions")
        .param("accountId", testAccountId.toString())
        .param("status", "SETTLED")
        .param("from", "2026-03-01T00:00:00Z")
        .param("to", "2026-03-31T23:59:59Z")
        .param("page", "0")
        .param("pageSize", "20")
        .param("order", "transactedAt")
        .param("sort", "desc"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(jsonPath("$.data[0].id").value(testTransactionId.toString()))
        .andExpect(jsonPath("$.data[0].accountId").value(testAccountId.toString()))
        .andExpect(jsonPath("$.data[0].amount").value(150.75))
        .andExpect(jsonPath("$.data[0].merchantName").value("Supermarket"))
        .andExpect(jsonPath("$.data[0].status").value("SETTLED"))
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.pageSize").value(20))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @WithMockUser
  void list_shouldUseDefaultParameters() throws Exception {
    // Given
    DataResponse<TransactionResponse> mockResponse = DataResponse.<TransactionResponse>builder()
        .data(Collections.emptyList())
        .totalCount(0L)
        .page(0)
        .pageSize(20)
        .hasMore(false)
        .build();

    when(testTransactionService.list(
        isNull(), isNull(), isNull(), isNull(),
        eq(0), eq(20), eq("createdAt"), eq("asc")))
        .thenReturn(ResponseEntity.ok(mockResponse));

    // When & Then - No parameters should use defaults
    mockMvc.perform(get("/api/v1/transactions"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  void getById_shouldReturnTransactionWhenFound() throws Exception {
    // Given
    TransactionResponse testTransactionResponse = TransactionResponse.builder()
        .id(testTransactionId)
        .sourceId("source-123")
        .sourceType(SourceType.BANK_FEED)
        .accountId(testAccountId.toString())
        .amount(new BigDecimal("150.75"))
        .currency("ZAR")
        .merchantName("Supermarket")
        .merchantMcc("5411")
        .transactedAt(Instant.parse("2026-03-15T10:30:00Z"))
        .status(TransactionStatus.SETTLED)
        .categoryId(testCategoryId)
        .categoryCode("GROCERIES")
        .categoryLabel("Groceries")
        .createdAt(Instant.parse("2026-03-15T10:35:00Z"))
        .build();

    when(testTransactionService.getByExternalId(testTransactionId))
        .thenReturn(new ResponseEntity<>(testTransactionResponse, HttpStatus.OK));

    // When & Then
    mockMvc.perform(get("/api/v1/transactions/{id}", testTransactionId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(testTransactionId.toString()))
        .andExpect(jsonPath("$.accountId").value(testAccountId.toString()))
        .andExpect(jsonPath("$.amount").value(150.75))
        .andExpect(jsonPath("$.merchantName").value("Supermarket"))
        .andExpect(jsonPath("$.status").value("SETTLED"));
  }

  @Test
  @WithMockUser
  void getById_shouldReturn404WhenNotFound() throws Exception {
    // Given
    when(testTransactionService.getByExternalId(testTransactionId))
        .thenReturn(new ResponseEntity<>(HttpStatus.NOT_FOUND));

    // When & Then
    mockMvc.perform(get("/api/v1/transactions/{id}", testTransactionId))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser
  void merchantSearch_shouldReturnSearchResults() throws Exception {
    // Given
    TransactionResponse testTransactionResponse = TransactionResponse.builder()
        .id(testTransactionId)
        .sourceId("source-123")
        .sourceType(SourceType.BANK_FEED)
        .accountId(testAccountId.toString())
        .amount(new BigDecimal("150.75"))
        .currency("ZAR")
        .merchantName("Supermarket")
        .merchantMcc("5411")
        .transactedAt(Instant.parse("2026-03-15T10:30:00Z"))
        .status(TransactionStatus.SETTLED)
        .categoryId(testCategoryId)
        .categoryCode("GROCERIES")
        .categoryLabel("Groceries")
        .createdAt(Instant.parse("2026-03-15T10:35:00Z"))
        .build();

    DataResponse<TransactionResponse> mockResponse = DataResponse.<TransactionResponse>builder()
        .data(Collections.singletonList(testTransactionResponse))
        .totalCount(1L)
        .page(0)
        .pageSize(20)
        .hasMore(false)
        .build();

    when(testTransactionService.merchantSearch(
        anyString(), any(Date.class), any(Date.class),
        anyInt(), anyInt(), anyString(), anyString()))
        .thenReturn(ResponseEntity.ok(mockResponse));

    // When & Then
    mockMvc.perform(get("/api/v1/transactions/search")
        .param("merchantName", "Supermarket")
        .param("from", "2026-03-01T00:00:00Z")
        .param("to", "2026-03-31T23:59:59Z")
        .param("page", "0")
        .param("pageSize", "20")
        .param("order", "transactedAt")
        .param("sort", "desc"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data[0].merchantName").value("Supermarket"))
        .andExpect(jsonPath("$.data[0].amount").value(150.75))
        .andExpect(jsonPath("$.totalCount").value(1));
  }

  @Test
  @WithMockUser
  void merchantSearch_shouldReturn400WhenMerchantNameMissing() throws Exception {
    // When & Then - Missing required merchantName parameter
    mockMvc.perform(get("/api/v1/transactions/search"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unauthenticatedRequest_shouldReturn401() throws Exception {
    mockMvc.perform(get("/api/v1/transactions"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void getById_shouldReturn500WhenServiceThrowsException() throws Exception {
    // Given
    when(testTransactionService.getByExternalId(testTransactionId))
        .thenThrow(new RuntimeException("Service error"));

    // When & Then
    mockMvc.perform(get("/api/v1/transactions/{id}", testTransactionId))
        .andExpect(status().is5xxServerError());
  }

  @Test
  @WithMockUser
  void list_shouldReturn400WhenInvalidPageSize() throws Exception {
    // When & Then - Page size exceeds max (1000)
    mockMvc.perform(get("/api/v1/transactions")
        .param("pageSize", "1001"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser
  void list_shouldReturn400WhenInvalidPage() throws Exception {
    // When & Then - Page exceeds max (100)
    mockMvc.perform(get("/api/v1/transactions")
        .param("page", "101"))
        .andExpect(status().isBadRequest());
  }
}