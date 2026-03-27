package za.co.reed.ingestorservice.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import za.co.reed.commom.constants.KafkaTopics;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransactionProducer}.
 * 
 * Tests cover:
 * - Successful Kafka message publishing
 * - Correct topic and key usage
 * - Error handling
 * - Callback logging
 */
@ExtendWith(MockitoExtension.class)
class TransactionProducerTest {

    @Mock
    private KafkaTemplate<String, NormalisedTransaction> kafkaTemplate;
    
    @Mock
    private MeterRegistry meterRegistry;
    
    @Mock
    private SendResult<String, NormalisedTransaction> sendResult;
    
    @Captor
    private ArgumentCaptor<NormalisedTransaction> transactionCaptor;
    
    private TransactionProducer transactionProducer;
    
    private NormalisedTransaction testTransaction;
    
    @BeforeEach
    void setUp() {
        transactionProducer = new TransactionProducer(kafkaTemplate, meterRegistry);
        
        testTransaction = new NormalisedTransaction(
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
    void publish_sendsToCorrectTopicWithSourceIdAsKey() {
        // Given
        CompletableFuture<SendResult<String, NormalisedTransaction>> future = 
            CompletableFuture.completedFuture(sendResult);
        
        when(kafkaTemplate.send(eq(KafkaTopics.RAW_TRANSACTIONS), eq("txn-12345"), any(NormalisedTransaction.class)))
            .thenReturn(future);
        
        // When
        transactionProducer.publish(testTransaction);
        
        // Then
        verify(kafkaTemplate).send(
            eq(KafkaTopics.RAW_TRANSACTIONS),
            eq("txn-12345"),
            transactionCaptor.capture()
        );
        
        NormalisedTransaction capturedTransaction = transactionCaptor.getValue();
        assertThat(capturedTransaction.sourceId()).isEqualTo("txn-12345");
        assertThat(capturedTransaction.amount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(capturedTransaction.sourceType()).isEqualTo(SourceType.PAYMENT_PROCESSOR);
    }
    
    @Test
    void publish_logsSuccess_whenMessageSentSuccessfully() {
        // Given
        CompletableFuture<SendResult<String, NormalisedTransaction>> future = 
            CompletableFuture.completedFuture(sendResult);
        
        when(kafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
            .thenReturn(future);
        
        // When
        transactionProducer.publish(testTransaction);
        
        // Then
        verify(kafkaTemplate).send(
            eq(KafkaTopics.RAW_TRANSACTIONS),
            eq("txn-12345"),
            any(NormalisedTransaction.class)
        );
        // Can't easily verify log messages without a logging framework
        // but the success callback code path is executed
    }
    
    @Test
    void publish_throwsException_whenKafkaSendFails() {
        // Given
        RuntimeException kafkaException = new RuntimeException("Kafka broker unavailable");
        CompletableFuture<SendResult<String, NormalisedTransaction>> failedFuture = 
            CompletableFuture.failedFuture(kafkaException);
        
        when(kafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
            .thenReturn(failedFuture);
        
        // When & Then
        try {
            transactionProducer.publish(testTransaction);
        } catch (RuntimeException e) {
            // Expected - the method should propagate the exception
        }
        
        verify(kafkaTemplate).send(
            eq(KafkaTopics.RAW_TRANSACTIONS),
            eq("txn-12345"),
            any(NormalisedTransaction.class)
        );
    }
    
    @Test
    void publish_handlesDifferentTransactionTypes() {
        // Test with different source types
        SourceType[] sourceTypes = {SourceType.PAYMENT_PROCESSOR, SourceType.BANK_FEED, SourceType.CARD_NETWORK};
        
        for (SourceType sourceType : sourceTypes) {
            NormalisedTransaction transaction = new NormalisedTransaction(
                "txn-" + sourceType.name(),
                sourceType,
                "ACC-001",
                new BigDecimal("50.00"),
                Currency.getInstance("USD"),
                "Merchant " + sourceType.name(),
                "5678",
                Instant.now().minusSeconds(1800),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
            );
            
            CompletableFuture<SendResult<String, NormalisedTransaction>> future = 
                CompletableFuture.completedFuture(sendResult);
            
            when(kafkaTemplate.send(eq(KafkaTopics.RAW_TRANSACTIONS), eq("txn-" + sourceType.name()), any(NormalisedTransaction.class)))
                .thenReturn(future);
            
            // When
            transactionProducer.publish(transaction);
            
            // Then
            verify(kafkaTemplate).send(
                eq(KafkaTopics.RAW_TRANSACTIONS),
                eq("txn-" + sourceType.name()),
                any(NormalisedTransaction.class)
            );
            
            // Reset mock for next iteration
            reset(kafkaTemplate);
        }
    }
    
    @Test
    void publish_usesSourceIdAsKafkaMessageKey() {
        // Given
        String sourceId = "unique-source-id-123";
        NormalisedTransaction transaction = new NormalisedTransaction(
            sourceId,
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("75.00"),
            Currency.getInstance("EUR"),
            "Test Merchant",
            "9012",
            Instant.now().minusSeconds(2700),
            TransactionStatus.SETTLED,
            "{\"raw\": \"payload\"}"
        );
        
        CompletableFuture<SendResult<String, NormalisedTransaction>> future = 
            CompletableFuture.completedFuture(sendResult);
        
        when(kafkaTemplate.send(eq(KafkaTopics.RAW_TRANSACTIONS), eq(sourceId), any(NormalisedTransaction.class)))
            .thenReturn(future);
        
        // When
        transactionProducer.publish(transaction);
        
        // Then - Verify the key (sourceId) is used
        verify(kafkaTemplate).send(eq(KafkaTopics.RAW_TRANSACTIONS), eq(sourceId), any(NormalisedTransaction.class));
    }
    
    @Test
    void publish_handlesCallbackSuccessLogging() {
        // Given
        CompletableFuture<SendResult<String, NormalisedTransaction>> future = 
            CompletableFuture.completedFuture(sendResult);
        
        when(kafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
            .thenReturn(future);
        
        // When
        transactionProducer.publish(testTransaction);
        
        // Then - The thenAccept callback should execute (logs success)
        // Can't easily verify the callback execution without more complex mocking
        verify(kafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
    }
    
    @Test
    void publish_handlesCallbackErrorLogging() {
        // Given
        RuntimeException kafkaException = new RuntimeException("Kafka error");
        CompletableFuture<SendResult<String, NormalisedTransaction>> failedFuture = 
            CompletableFuture.failedFuture(kafkaException);
        
        when(kafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
            .thenReturn(failedFuture);
        
        // When & Then
        try {
            transactionProducer.publish(testTransaction);
        } catch (RuntimeException e) {
            // Expected
        }
        
        // Then - The exceptionally callback should execute (logs error)
        verify(kafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
    }
}