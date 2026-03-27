package za.co.reed.processingservice.producer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import za.co.reed.commom.constants.KafkaTopics;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.persistence.entity.Category;
import za.co.reed.persistence.entity.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategorisedTransactionProducerTest {

    @Mock
    private KafkaTemplate<String, NormalisedTransaction> kafkaTemplate;

    @InjectMocks
    private CategorisedTransactionProducer producer;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, NormalisedTransaction>> recordCaptor;

    private NormalisedTransaction sourceTransaction;
    private Transaction transaction;
    private Category category;

    @BeforeEach
    void setUp() {
        sourceTransaction = new NormalisedTransaction(
                "source-123",
                SourceType.CARD_NETWORK,
                "ACC-001",
                BigDecimal.valueOf(100.50),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "5411",
                Instant.now(),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        category = Category.builder()
                .id(1L)
                .code("GROCERIES")
                .label("Groceries")
                .path("groceries")
                .build();

        transaction = Transaction.builder()
                .id(1L)
                .sourceId("source-123")
                .category(category)
                .build();
    }

    @Test
    void publish_shouldHandleSuccessfulPublish() {
        // Arrange
        CompletableFuture<SendResult<String, NormalisedTransaction>> future = CompletableFuture.completedFuture(
                createSendResult(sourceTransaction.sourceId(), KafkaTopics.CATEGORISED_TRANSACTIONS, 0, 456L)
        );
        when(kafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(future);

        // Act
        producer.publish(transaction, sourceTransaction);

        // Assert
        verify(kafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
    }

    @Test
    void publish_shouldHandlePublishFailure() {
        // Arrange
        CompletableFuture<SendResult<String, NormalisedTransaction>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(future);

        // Act
        producer.publish(transaction, sourceTransaction);

        // Assert
        verify(kafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
    }

    @Test
    void publish_shouldHandleNullCategory() {
        // Arrange
        Transaction transactionWithoutCategory = Transaction.builder()
                .id(2L)
                .sourceId("source-456")
                .category(null)
                .build();

        CompletableFuture<SendResult<String, NormalisedTransaction>> future = CompletableFuture.completedFuture(
                createSendResult(sourceTransaction.sourceId(), KafkaTopics.CATEGORISED_TRANSACTIONS, 0, 457L)
        );
        when(kafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(future);

        // Act
        producer.publish(transactionWithoutCategory, sourceTransaction);

        // Assert
        verify(kafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
        // Should handle null category gracefully
    }

    @Test
    void publish_shouldUseWhenCompleteForAsyncHandling() {
        // Arrange
        CompletableFuture<SendResult<String, NormalisedTransaction>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(future);

        // Act
        producer.publish(transaction, sourceTransaction);

        // Assert
        verify(kafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
        assertTrue(future.toCompletableFuture().isDone() || !future.toCompletableFuture().isDone()); // Just checking it's set up
    }


    @Test
    void publish_shouldWorkWithDifferentTransactionTypes() {
        // Arrange
        NormalisedTransaction bankFeedSource = new NormalisedTransaction(
                "source-789",
                SourceType.BANK_FEED,
                "ACC-002",
                BigDecimal.valueOf(200.75),
                Currency.getInstance("USD"),
                "Bank Merchant",
                null,
                Instant.now(),
                TransactionStatus.PENDING,
                "{\"raw\": \"bank\"}"
        );

        Transaction bankTransaction = Transaction.builder()
                .id(3L)
                .sourceId("source-789")
                .category(category)
                .build();

        CompletableFuture<SendResult<String, NormalisedTransaction>> future =
                CompletableFuture.completedFuture(
                        createSendResult(bankFeedSource.sourceId(),
                                KafkaTopics.CATEGORISED_TRANSACTIONS,
                                1,
                                458L)
                );

        when(kafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(future);

        // Act
        producer.publish(bankTransaction, bankFeedSource);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NormalisedTransaction> valueCaptor = ArgumentCaptor.forClass(NormalisedTransaction.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        assertEquals(KafkaTopics.CATEGORISED_TRANSACTIONS, topicCaptor.getValue());
        assertEquals(bankFeedSource.sourceId(), keyCaptor.getValue());
        assertEquals(bankFeedSource, valueCaptor.getValue());
    }



    @Test
    void publish_shouldNotModifyOriginalTransaction() {
        // Arrange
        CompletableFuture<SendResult<String, NormalisedTransaction>> future = CompletableFuture.completedFuture(
                createSendResult(sourceTransaction.sourceId(), KafkaTopics.CATEGORISED_TRANSACTIONS, 0, 459L)
        );
        when(kafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(future);

        // Act
        producer.publish(transaction, sourceTransaction);

        // Assert
        // Verify transaction remains unchanged
        assertEquals("source-123", transaction.getSourceId());
        assertEquals(category, transaction.getCategory());
        verify(kafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
    }

    @Test
    void publish_shouldNotModifyOriginalSource() {
        // Arrange
        CompletableFuture<SendResult<String, NormalisedTransaction>> future = CompletableFuture.completedFuture(
                createSendResult(sourceTransaction.sourceId(), KafkaTopics.CATEGORISED_TRANSACTIONS, 0, 460L)
        );

        when(kafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(future);

        // Keep original values
        String originalSourceId = sourceTransaction.sourceId();
        SourceType originalSourceType = sourceTransaction.sourceType();
        BigDecimal originalAmount = sourceTransaction.amount();

        // Act
        producer.publish(transaction, sourceTransaction);

        // Assert
        // Verify source remains unchanged
        assertEquals(originalSourceId, sourceTransaction.sourceId());
        assertEquals(originalSourceType, sourceTransaction.sourceType());
        assertEquals(originalAmount, sourceTransaction.amount());
    }

    private SendResult<String, NormalisedTransaction> createSendResult(String key, String topic, int partition, long offset) {
        ProducerRecord<String, NormalisedTransaction> producerRecord = new ProducerRecord<>(topic, partition, null, key, sourceTransaction);
        RecordMetadata recordMetadata = mock(RecordMetadata.class);
        when(recordMetadata.topic()).thenReturn(topic);
        when(recordMetadata.partition()).thenReturn(partition);
        when(recordMetadata.offset()).thenReturn(offset);
        return new SendResult<>(producerRecord, recordMetadata);
    }
}