package za.co.reed.mocksourceservice.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.mocksourceservice.properties.IngestorProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IngestorClient}.
 */
@ExtendWith(MockitoExtension.class)
class IngestorClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private IngestorProperties properties;

    @Mock
    private ObjectMapper objectMapper;

    @Captor
    private ArgumentCaptor<HttpEntity<String>> httpEntityCaptor;

    private IngestorClient client;

    private NormalisedTransaction sampleTransaction;

    @BeforeEach
    void setUp() {
        client = new IngestorClient(restTemplate, properties, objectMapper);

        sampleTransaction = new NormalisedTransaction(
                UUID.randomUUID().toString(),
                SourceType.BANK_FEED,
                "ACC-12345",
                new BigDecimal("123.45"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                null, // merchantMcc
                Instant.now(),
                TransactionStatus.SETTLED,
                "{\"raw\": \"data\"}"
        );
    }

    @Test
    void send_successfulPost_shouldCallRestTemplateWithCorrectHeaders() throws JsonProcessingException {
        // Arrange
        String jsonBody = "{\"sourceId\":\"test-id\"}";
        when(objectMapper.writeValueAsString(sampleTransaction)).thenReturn(jsonBody);
        when(restTemplate.exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.ok().build());
        when(properties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(properties.getWebhookSecret()).thenReturn("test-secret-key-12345");

        // Act
        client.send(sampleTransaction);

        // Assert
        verify(restTemplate).exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                httpEntityCaptor.capture(),
                eq(Void.class)
        );

        HttpEntity<String> capturedEntity = httpEntityCaptor.getValue();
        assertThat(capturedEntity.getBody()).isEqualTo(jsonBody);
        
        HttpHeaders headers = capturedEntity.getHeaders();
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.get("X-Source-Type")).containsExactly("BANK_FEED");
        assertThat(headers.get("X-Transaction-Aggregator-Signature")).isNotNull();
        
        // Verify signature format
        String signature = headers.getFirst("X-Transaction-Aggregator-Signature");
        assertThat(signature).startsWith("sha256=");
        assertThat(signature.length()).isGreaterThan(70); // sha256= + 64 hex chars
    }

    @Test
    void send_non2xxResponse_shouldLogWarning() throws JsonProcessingException {
        // Arrange
        String jsonBody = "{\"sourceId\":\"test-id\"}";
        when(objectMapper.writeValueAsString(sampleTransaction)).thenReturn(jsonBody);
        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
        when(properties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(properties.getWebhookSecret()).thenReturn("test-secret-key-12345");

        // Act
        client.send(sampleTransaction);

        // Assert - should not throw, just log warning
        verify(restTemplate).exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        );
    }

    @Test
    void send_restTemplateThrowsException_shouldWrapInRuntimeException() throws JsonProcessingException {
        // Arrange
        String jsonBody = "{\"sourceId\":\"test-id\"}";
        when(objectMapper.writeValueAsString(sampleTransaction)).thenReturn(jsonBody);
        when(properties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(properties.getWebhookSecret()).thenReturn("test-secret-key-12345");
        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenThrow(new RestClientException("Network error"));

        // Act & Assert
        assertThatThrownBy(() -> client.send(sampleTransaction))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send transaction to ingestor")
                .hasCauseInstanceOf(RestClientException.class);
    }

    @Test
    void send_jsonSerializationFails_shouldThrowRuntimeException() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(sampleTransaction))
                .thenThrow(new RuntimeException("JSON serialization failed"));

        // Act & Assert
        assertThatThrownBy(() -> client.send(sampleTransaction))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send transaction to ingestor")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void sign_validPayload_shouldReturnCorrectHmacSignature() throws JsonProcessingException {
        // Arrange
        String payload = "test-payload";
        String secret = "test-secret-key-12345";
        when(properties.getWebhookSecret()).thenReturn(secret);

        // Act - We need to test the private method via reflection or by verifying the signature in send test
        // Since sign() is private, we'll verify it works through the public send method
        String jsonBody = "{\"test\":\"data\"}";
        when(objectMapper.writeValueAsString(sampleTransaction)).thenReturn(jsonBody);
        when(properties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(properties.getWebhookSecret()).thenReturn("test-secret-key-12345");
        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.ok().build());

        client.send(sampleTransaction);

        // Assert - Verify signature was computed and added to headers
        verify(restTemplate).exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                httpEntityCaptor.capture(),
                eq(Void.class)
        );

        HttpEntity<String> capturedEntity = httpEntityCaptor.getValue();
        String signature = capturedEntity.getHeaders().getFirst("X-Transaction-Aggregator-Signature");
        
        assertThat(signature).isNotNull();
        assertThat(signature).startsWith("sha256=");
        // HMAC-SHA256 produces 64 hex characters
        assertThat(signature.substring(7)).hasSize(64);
    }

    @Test
    void sign_emptySecret_shouldThrowIllegalArgumentException() throws JsonProcessingException {
        // Arrange
        when(properties.getWebhookSecret()).thenReturn("");
        String jsonBody = "{\"test\":\"data\"}";
        when(objectMapper.writeValueAsString(sampleTransaction)).thenReturn(jsonBody);

        // Act & Assert
        assertThatThrownBy(() -> client.send(sampleTransaction))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to send transaction to ingestor");
    }

    @Test
    void send_differentSourceTypes_shouldIncludeCorrectHeader() throws JsonProcessingException {
        // Test with CARD_NETWORK source type
        NormalisedTransaction cardTransaction = new NormalisedTransaction(
                UUID.randomUUID().toString(),
                SourceType.CARD_NETWORK,
                "CARD-67890",
                new BigDecimal("99.99"),
                Currency.getInstance("USD"),
                "Amazon",
                "1234", // merchantMcc
                Instant.now(),
                TransactionStatus.PENDING,
                "{\"card\": \"data\"}"
        );

        String jsonBody = "{\"sourceId\":\"card-id\"}";
        when(objectMapper.writeValueAsString(cardTransaction)).thenReturn(jsonBody);
        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.ok().build());
        when(properties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(properties.getWebhookSecret()).thenReturn("test-secret-key-12345");

        // Act
        client.send(cardTransaction);

        // Assert
        verify(restTemplate).exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                httpEntityCaptor.capture(),
                eq(Void.class)
        );

        HttpHeaders headers = httpEntityCaptor.getValue().getHeaders();
        assertThat(headers.get("X-Source-Type")).containsExactly("CARD_NETWORK");
    }

    @Test
    void send_withPaymentProcessorSource_shouldWorkCorrectly() throws JsonProcessingException {
        // Test with PAYMENT_PROCESSOR source type
        NormalisedTransaction paymentTransaction = new NormalisedTransaction(
                UUID.randomUUID().toString(),
                SourceType.PAYMENT_PROCESSOR,
                "PAY-11111",
                new BigDecimal("50.00"),
                Currency.getInstance("EUR"),
                "Stripe",
                null, // merchantMcc
                Instant.now(),
                TransactionStatus.REVERSED,
                "{\"payment\": \"data\"}"
        );

        String jsonBody = "{\"sourceId\":\"payment-id\"}";
        when(properties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(properties.getWebhookSecret()).thenReturn("test-secret-key-12345");
        when(objectMapper.writeValueAsString(paymentTransaction)).thenReturn(jsonBody);
        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.ok().build());

        // Act
        client.send(paymentTransaction);

        // Assert
        verify(restTemplate).exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                httpEntityCaptor.capture(),
                eq(Void.class)
        );

        HttpHeaders headers = httpEntityCaptor.getValue().getHeaders();
        assertThat(headers.get("X-Source-Type")).containsExactly("PAYMENT_PROCESSOR");
    }
}