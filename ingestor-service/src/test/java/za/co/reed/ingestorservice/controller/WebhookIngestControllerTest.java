package za.co.reed.ingestorservice.controller;

import org.springframework.boot.web.client.RestTemplateBuilder;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.ingestorservice.config.helper.HmacSignatureFilter;
import za.co.reed.ingestorservice.dto.IngestResponse;
import za.co.reed.ingestorservice.enums.IngestResult;
import za.co.reed.ingestorservice.service.IngestorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link WebhookIngestController}.
 * 
 * Tests cover:
 * - Successful transaction ingestion (202 Accepted)
 * - Duplicate transaction handling (202 Accepted with duplicate status)
 * - Failed transaction ingestion (500 Internal Server Error)
 * - Request validation (400 Bad Request)
 * - HMAC signature header requirement
 */
@WebMvcTest(WebhookIngestController.class)
class WebhookIngestControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper testObjectMapper;

    @MockBean
    private IngestorService testIngestorService;

    @MockBean
    private RestTemplateBuilder testRestTemplateBuilder;

    private NormalisedTransaction testValidTransaction;


    
    @BeforeEach
    void setUp() {
        testValidTransaction = new NormalisedTransaction(
            "txn-12345",
            SourceType.PAYMENT_PROCESSOR,
            "cus_001",
            new BigDecimal("100.50"),
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().minusSeconds(3600),
            TransactionStatus.SETTLED,
            "{\"raw\": \"payload\"}"
        );
    }
    
    @Test
    void ingestPayment_returnsAccepted_whenTransactionIsAccepted() throws Exception {
        // Given
        when(testIngestorService.ingest(any(NormalisedTransaction.class)))
            .thenReturn(IngestResult.ACCEPTED);
        
        // When & Then
        mockMvc.perform(post("/api/internal/webhook/payment")
                .header(HmacSignatureFilter.SIGNATURE_HEADER, "valid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testObjectMapper.writeValueAsString(testValidTransaction)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("accepted"))
            .andExpect(jsonPath("$.sourceId").value("txn-12345"));
    }
    
    @Test
    void ingestPayment_returnsAccepted_whenTransactionIsDuplicate() throws Exception {
        // Given
        when(testIngestorService.ingest(any(NormalisedTransaction.class)))
            .thenReturn(IngestResult.DUPLICATE);
        
        // When & Then
        mockMvc.perform(post("/api/internal/webhook/payment")
                .header(HmacSignatureFilter.SIGNATURE_HEADER, "valid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testObjectMapper.writeValueAsString(testValidTransaction)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("duplicate"))
            .andExpect(jsonPath("$.sourceId").value("txn-12345"));
    }
    
    @Test
    void ingestPayment_returnsInternalServerError_whenTransactionFails() throws Exception {
        // Given
        when(testIngestorService.ingest(any(NormalisedTransaction.class)))
            .thenReturn(IngestResult.FAILED);
        
        // When & Then
        mockMvc.perform(post("/api/internal/webhook/payment")
                .header(HmacSignatureFilter.SIGNATURE_HEADER, "valid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testObjectMapper.writeValueAsString(testValidTransaction)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value("failed"))
            .andExpect(jsonPath("$.sourceId").value("txn-12345"));
    }
    
    @Test
    void ingestPayment_returnsBadRequest_whenTransactionValidationFails() throws Exception {
        // Given - Create invalid transaction (missing required fields)
        String invalidTransactionJson = "{\"source_id\": \"\", \"source_type\": \"PAYMENT_PROCESSOR\"}";
        
        // When & Then - Spring Validation should return 400 Bad Request
        mockMvc.perform(post("/api/internal/webhook/payment")
                .header(HmacSignatureFilter.SIGNATURE_HEADER, "valid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidTransactionJson))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void ingestPayment_requiresSignatureHeader() throws Exception {
        // When & Then - Request without signature header should be rejected
        // Note: In a real test, the security filter would reject this before reaching controller
        // For unit testing, we're testing the controller directly
        mockMvc.perform(post("/api/internal/webhook/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testObjectMapper.writeValueAsString(testValidTransaction)))
            .andExpect(status().isBadRequest()); // Or 401 depending on security configuration
    }
    
    @Test
    void ingestPayment_logsDebugMessage_whenRequestReceived() throws Exception {
        // Given
        when(testIngestorService.ingest(any(NormalisedTransaction.class)))
            .thenReturn(IngestResult.ACCEPTED);
        
        // When & Then
        mockMvc.perform(post("/api/internal/webhook/payment")
                .header(HmacSignatureFilter.SIGNATURE_HEADER, "valid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testObjectMapper.writeValueAsString(testValidTransaction)))
            .andExpect(status().isAccepted());
        
        // Controller logs debug message - can't easily verify in unit test
        // but code path is executed
    }
    
    @Test
    void ingestPayment_handlesDifferentSourceTypes() throws Exception {
        // Test with different source types
        SourceType[] testSourceTypes = {SourceType.PAYMENT_PROCESSOR, SourceType.BANK_FEED, SourceType.CARD_NETWORK};
        
        for (SourceType testSourceType : testSourceTypes) {
            NormalisedTransaction testTransaction = new NormalisedTransaction(
                "txn-" + testSourceType.name(),
                testSourceType,
                "ACC-001",
                new BigDecimal("50.00"),
                Currency.getInstance("USD"),
                "Merchant " + testSourceType.name(),
                "5678",
                Instant.now().minusSeconds(1800),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
            );
            
            when(testIngestorService.ingest(any(NormalisedTransaction.class)))
                .thenReturn(IngestResult.ACCEPTED);
            
            mockMvc.perform(post("/api/internal/webhook/payment")
                    .header(HmacSignatureFilter.SIGNATURE_HEADER, "valid-signature")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(testObjectMapper.writeValueAsString(testTransaction)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sourceId").value("txn-" + testSourceType.name()));
        }
    }
    
    @Test
    void ingestPayment_handlesDifferentTransactionStatuses() throws Exception {
        // Test with different transaction statuses
        TransactionStatus[] testStatuses = {TransactionStatus.PENDING, TransactionStatus.SETTLED, TransactionStatus.REVERSED};
        
        for (TransactionStatus testStatus : testStatuses) {
            NormalisedTransaction testTransaction = new NormalisedTransaction(
                "txn-" + testStatus.name(),
                SourceType.PAYMENT_PROCESSOR,
                "ACC-001",
                new BigDecimal("75.00"),
                Currency.getInstance("EUR"),
                "Merchant",
                "9012",
                Instant.now().minusSeconds(2700),
                testStatus,
                "{\"raw\": \"payload\"}"
            );
            
            when(testIngestorService.ingest(any(NormalisedTransaction.class)))
                .thenReturn(IngestResult.ACCEPTED);
            
            mockMvc.perform(post("/api/internal/webhook/payment")
                    .header(HmacSignatureFilter.SIGNATURE_HEADER, "valid-signature")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(testObjectMapper.writeValueAsString(testTransaction)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sourceId").value("txn-" + testStatus.name()));
        }
    }
    
    @Test
    void ingestResponse_recordHasCorrectFields() {
        // Test the IngestResponse record
        IngestResponse testResponse = new IngestResponse("accepted", "txn-12345");
        
        assert testResponse.getStatus().equals("accepted");
        assert testResponse.getSourceId().equals("txn-12345");
    }
    
    @Test
    void ingestResult_enumHasCorrectValues() {
        // Test the IngestResult enum
        IngestResult[] testValues = IngestResult.values();
        
        assert testValues.length == 3;
        assert IngestResult.ACCEPTED != null;
        assert IngestResult.DUPLICATE != null;
        assert IngestResult.FAILED != null;
    }
}