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
    private RestTemplate testRestTemplate;

    @Mock
    private IngestorProperties testProperties;

    @Mock
    private ObjectMapper testObjectMapper;

    @Captor
    private ArgumentCaptor<HttpEntity<String>> testHttpEntityCaptor;

    private IngestorClient testClient;

    private NormalisedTransaction testSampleTransaction;

    @BeforeEach
    void setUp() {
        testClient = new IngestorClient(testRestTemplate, testProperties, testObjectMapper);

        testSampleTransaction = new NormalisedTransaction(
                UUID.randomUUID().toString(),
                SourceType.BANK_FEED,
                "ACC-12345",
                new BigDecimal("123.45"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                null,
                Instant.now(),
                TransactionStatus.SETTLED,
                "{\"raw\": \"data\"}"
        );
    }

    @Test
    void send_successfulPost_shouldCallRestTemplateWithCorrectHeaders() throws JsonProcessingException {
        // Arrange
        String testJsonBody = "{\"sourceId\":\"test-id\"}";
        when(testObjectMapper.writeValueAsString(testSampleTransaction)).thenReturn(testJsonBody);
        when(testRestTemplate.exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.ok().build());
        when(testProperties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(testProperties.getWebhookSecret()).thenReturn("test-secret-key-12345");

        // Act
        testClient.send(testSampleTransaction);

        // Assert
        verify(testRestTemplate).exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                testHttpEntityCaptor.capture(),
                eq(Void.class)
        );

        HttpEntity<String> testCapturedEntity = testHttpEntityCaptor.getValue();
        assertThat(testCapturedEntity.getBody()).isEqualTo(testJsonBody);

        HttpHeaders testHeaders = testCapturedEntity.getHeaders();
        assertThat(testHeaders.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(testHeaders.get("X-Source-Type")).containsExactly("BANK_FEED");
        assertThat(testHeaders.get("X-Transaction-Aggregator-Signature")).isNotNull();

        String testSignature = testHeaders.getFirst("X-Transaction-Aggregator-Signature");
        assertThat(testSignature).startsWith("sha256=");
        assertThat(testSignature.length()).isGreaterThan(70);
    }

    @Test
    void send_non2xxResponse_shouldLogWarning() throws JsonProcessingException {
        // Arrange
        String testJsonBody = "{\"sourceId\":\"test-id\"}";
        when(testObjectMapper.writeValueAsString(testSampleTransaction)).thenReturn(testJsonBody);
        when(testRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
        when(testProperties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(testProperties.getWebhookSecret()).thenReturn("test-secret-key-12345");

        // Act
        testClient.send(testSampleTransaction);

        // Assert
        verify(testRestTemplate).exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        );
    }

    @Test
    void send_restTemplateThrowsException_shouldWrapInRuntimeException() throws JsonProcessingException {
        // Arrange
        String testJsonBody = "{\"sourceId\":\"test-id\"}";
        when(testObjectMapper.writeValueAsString(testSampleTransaction)).thenReturn(testJsonBody);
        when(testProperties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(testProperties.getWebhookSecret()).thenReturn("test-secret-key-12345");
        when(testRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenThrow(new RestClientException("Network error"));

        // Act & Assert
        assertThatThrownBy(() -> testClient.send(testSampleTransaction))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send transaction to ingestor")
                .hasCauseInstanceOf(RestClientException.class);
    }

    @Test
    void send_jsonSerializationFails_shouldThrowRuntimeException() throws JsonProcessingException {
        // Arrange
        when(testObjectMapper.writeValueAsString(testSampleTransaction))
                .thenThrow(new RuntimeException("JSON serialization failed"));

        // Act & Assert
        assertThatThrownBy(() -> testClient.send(testSampleTransaction))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send transaction to ingestor")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void sign_validPayload_shouldReturnCorrectHmacSignature() throws JsonProcessingException {
        // Arrange
        String testJsonBody = "{\"test\":\"data\"}";
        when(testProperties.getWebhookSecret()).thenReturn("test-secret-key-12345");
        when(testObjectMapper.writeValueAsString(testSampleTransaction)).thenReturn(testJsonBody);
        when(testProperties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(testRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.ok().build());

        // Act
        testClient.send(testSampleTransaction);

        // Assert
        verify(testRestTemplate).exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                testHttpEntityCaptor.capture(),
                eq(Void.class)
        );

        String testSignature = testHttpEntityCaptor.getValue().getHeaders().getFirst("X-Transaction-Aggregator-Signature");
        assertThat(testSignature).isNotNull();
        assertThat(testSignature).startsWith("sha256=");
        assertThat(testSignature.substring(7)).hasSize(64);
    }

    @Test
    void sign_emptySecret_shouldThrowIllegalArgumentException() throws JsonProcessingException {
        // Arrange
        when(testProperties.getWebhookSecret()).thenReturn("");
        String testJsonBody = "{\"test\":\"data\"}";
        when(testObjectMapper.writeValueAsString(testSampleTransaction)).thenReturn(testJsonBody);

        // Act & Assert
        assertThatThrownBy(() -> testClient.send(testSampleTransaction))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to send transaction to ingestor");
    }

    @Test
    void send_differentSourceTypes_shouldIncludeCorrectHeader() throws JsonProcessingException {
        // Given
        NormalisedTransaction testCardTransaction = new NormalisedTransaction(
                UUID.randomUUID().toString(),
                SourceType.CARD_NETWORK,
                "CARD-67890",
                new BigDecimal("99.99"),
                Currency.getInstance("USD"),
                "Amazon",
                "1234",
                Instant.now(),
                TransactionStatus.PENDING,
                "{\"card\": \"data\"}"
        );

        String testJsonBody = "{\"sourceId\":\"card-id\"}";
        when(testObjectMapper.writeValueAsString(testCardTransaction)).thenReturn(testJsonBody);
        when(testRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.ok().build());
        when(testProperties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(testProperties.getWebhookSecret()).thenReturn("test-secret-key-12345");

        // Act
        testClient.send(testCardTransaction);

        // Assert
        verify(testRestTemplate).exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                testHttpEntityCaptor.capture(),
                eq(Void.class)
        );

        HttpHeaders testHeaders = testHttpEntityCaptor.getValue().getHeaders();
        assertThat(testHeaders.get("X-Source-Type")).containsExactly("CARD_NETWORK");
    }

    @Test
    void send_withPaymentProcessorSource_shouldWorkCorrectly() throws JsonProcessingException {
        // Given
        NormalisedTransaction testPaymentTransaction = new NormalisedTransaction(
                UUID.randomUUID().toString(),
                SourceType.PAYMENT_PROCESSOR,
                "PAY-11111",
                new BigDecimal("50.00"),
                Currency.getInstance("EUR"),
                "Stripe",
                null,
                Instant.now(),
                TransactionStatus.REVERSED,
                "{\"payment\": \"data\"}"
        );

        String testJsonBody = "{\"sourceId\":\"payment-id\"}";
        when(testProperties.getWebhookUrl()).thenReturn("http://localhost:8080/ingest");
        when(testProperties.getWebhookSecret()).thenReturn("test-secret-key-12345");
        when(testObjectMapper.writeValueAsString(testPaymentTransaction)).thenReturn(testJsonBody);
        when(testRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.ok().build());

        // Act
        testClient.send(testPaymentTransaction);

        // Assert
        verify(testRestTemplate).exchange(
                eq("http://localhost:8080/ingest"),
                eq(HttpMethod.POST),
                testHttpEntityCaptor.capture(),
                eq(Void.class)
        );

        HttpHeaders testHeaders = testHttpEntityCaptor.getValue().getHeaders();
        assertThat(testHeaders.get("X-Source-Type")).containsExactly("PAYMENT_PROCESSOR");
    }
}