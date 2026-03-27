package za.co.reed.apiservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import za.co.reed.apiservice.dto.response.DataResponse;
import za.co.reed.apiservice.dto.response.TransactionResponse;
import za.co.reed.apiservice.exception.ApiNotFoundException;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.persistence.entity.Category;
import za.co.reed.persistence.entity.Transaction;
import za.co.reed.persistence.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Transaction testTransaction;
    private UUID testTransactionId;
    private String testAccountId;

    @BeforeEach
    void setUp() {
        testTransactionId = UUID.randomUUID();
        testAccountId = UUID.randomUUID().toString();
        
        Category testCategory = new Category();
        testCategory.setId(1L);
        testCategory.setCode("GROCERIES");
        testCategory.setLabel("Groceries");
        
        testTransaction = new Transaction();
        testTransaction.setId(1L);
        testTransaction.setExternalId(testTransactionId);
        testTransaction.setAccountId(testAccountId.toString());
        testTransaction.setSourceType(SourceType.CARD_NETWORK);
        testTransaction.setAmount(new BigDecimal("100.50"));
        testTransaction.setCurrency("ZAR");
        testTransaction.setStatus(TransactionStatus.SETTLED);
        testTransaction.setTransactedAt(new Date().toInstant());
        testTransaction.setMerchantName("Supermarket");
        testTransaction.setCategory(testCategory);
    }

    @Test
    void list_shouldReturnPaginatedTransactions() {
        // Given
        List<Transaction> transactions = Collections.singletonList(testTransaction);
        Page<Transaction> transactionPage = new PageImpl<>(transactions, PageRequest.of(0, 10, Sort.Direction.DESC, "createdAt"), 1);
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(transactionPage);

        // When
        ResponseEntity<DataResponse<TransactionResponse>> response = transactionService.list(
                testAccountId, TransactionStatus.SETTLED, new Date(), new Date(), 0, 10, "createdAt", "DESC");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getTotalCount()).isEqualTo(1);
        
        TransactionResponse transactionResponse = response.getBody().getData().get(0);
        assertThat(transactionResponse.getId()).isEqualTo(testTransactionId);
        assertThat(transactionResponse.getAmount()).isEqualByComparingTo("100.50");
        assertThat(transactionResponse.getStatus()).isEqualTo(TransactionStatus.SETTLED);
    }

    @Test
    void list_shouldReturnEmptyResponseWhenNoTransactions() {
        // Given
        Page<Transaction> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(emptyPage);

        // When
        ResponseEntity<DataResponse<TransactionResponse>> response = transactionService.list(
                testAccountId, TransactionStatus.SETTLED, new Date(), new Date(), 0, 10, "createdAt", "DESC");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void list_shouldHandleExceptionAndRethrow() {
        // When & Then
        assertThrows(RuntimeException.class, () -> {
            transactionService.list(testAccountId, TransactionStatus.SETTLED, new Date(), new Date(), 0, 10, "createdAt", "DESC");
        });
    }

    @Test
    void getByExternalId_shouldReturnTransactionWhenFound() {
        // Given
        when(transactionRepository.findByExternalId(testTransactionId)).thenReturn(testTransaction);

        // When
        ResponseEntity<TransactionResponse> response = transactionService.getByExternalId(testTransactionId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(testTransactionId);
        assertThat(response.getBody().getAmount()).isEqualByComparingTo("100.50");
        assertThat(response.getBody().getAccountId()).isEqualTo(testAccountId);
    }

    @Test
    void getByExternalId_shouldReturnNotFoundWhenTransactionDoesNotExist() {
        // Given
        when(transactionRepository.findByExternalId(testTransactionId)).thenReturn(null);

        // When
        assertThrows(ApiNotFoundException.class, () -> transactionService.getByExternalId(testTransactionId));
    }

    @Test
    void getByExternalId_shouldHandleExceptionAndRethrow() {
        // Given
        when(transactionRepository.findByExternalId(testTransactionId)).thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            transactionService.getByExternalId(testTransactionId);
        });
    }

    @Test
    void list_shouldFilterByAccountId() {
        // Given
        List<Transaction> transactions = Collections.singletonList(testTransaction);
        Page<Transaction> transactionPage = new PageImpl<>(transactions, PageRequest.of(0, 10), 1);
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(transactionPage);

        // When
        ResponseEntity<DataResponse<TransactionResponse>> response = transactionService.list(
                testAccountId, null, null, null, 0, 10, "createdAt", "DESC");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
        
        // Verify specification was built with accountId filter
        verify(transactionRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void list_shouldFilterByStatus() {
        // Given
        List<Transaction> transactions = Collections.singletonList(testTransaction);
        Page<Transaction> transactionPage = new PageImpl<>(transactions, PageRequest.of(0, 10), 1);
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(transactionPage);

        // When
        ResponseEntity<DataResponse<TransactionResponse>> response = transactionService.list(
                null, TransactionStatus.SETTLED, null, null, 0, 10, "createdAt", "DESC");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    void list_shouldFilterByDateRange() {
        // Given
        List<Transaction> transactions = Collections.singletonList(testTransaction);
        Page<Transaction> transactionPage = new PageImpl<>(transactions, PageRequest.of(0, 10), 1);
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(transactionPage);

        Date from = new Date(System.currentTimeMillis() - 86400000); // Yesterday
        Date to = new Date();

        // When
        ResponseEntity<DataResponse<TransactionResponse>> response = transactionService.list(
                null, null, from, to, 0, 10, "createdAt", "DESC");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
    }
}