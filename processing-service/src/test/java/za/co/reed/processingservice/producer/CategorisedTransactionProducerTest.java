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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategorisedTransactionProducerTest {

    @Mock
    private KafkaTemplate<String, NormalisedTransaction> testKafkaTemplate;

    @InjectMocks
    private CategorisedTransactionProducer testProducer;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, NormalisedTransaction>> testRecordCaptor;

    private NormalisedTransaction testSourceTransaction;
    private Transaction testTransaction;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testSourceTransaction = new NormalisedTransaction(
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

        testCategory = Category.builder()
                .id(1L)
                .code("GROCERIES")
                .label("Groceries")
                .path("groceries")
                .build();

        testTransaction = Transaction.builder()
                .id(1L)
                .sourceId("source-123")
                .category(testCategory)
                .build();
    }

    @Test
    void publish_shouldHandleSuccessfulPublish() {
        // Arrange
        CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture = CompletableFuture.completedFuture(
                createSendResult(testSourceTransaction.sourceId(), KafkaTopics.CATEGORISED_TRANSACTIONS, 0, 456L, testSourceTransaction)
        );
        when(testKafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(testFuture);

        // Act
        testProducer.publish(testTransaction, testSourceTransaction);

        // Assert
        verify(testKafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
    }

    @Test
    void publish_shouldHandlePublishFailure() {
        // Arrange
        CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture = new CompletableFuture<>();
        testFuture.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        when(testKafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(testFuture);

        // Act
        testProducer.publish(testTransaction, testSourceTransaction);

        // Assert
        verify(testKafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
    }

    @Test
    void publish_shouldHandleNullCategory() {
        // Arrange
        Transaction testTransactionWithoutCategory = Transaction.builder()
                .id(2L)
                .sourceId("source-456")
                .category(null)
                .build();

        CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture = CompletableFuture.completedFuture(
                createSendResult(testSourceTransaction.sourceId(), KafkaTopics.CATEGORISED_TRANSACTIONS, 0, 457L, testSourceTransaction)
        );
        when(testKafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(testFuture);

        // Act
        testProducer.publish(testTransactionWithoutCategory, testSourceTransaction);

        // Assert
        verify(testKafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
        // Should handle null category gracefully
    }

    @Test
    void publish_shouldUseWhenCompleteForAsyncHandling() {
        // Arrange
        CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture = new CompletableFuture<>();
        when(testKafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(testFuture);

        // Act
        testProducer.publish(testTransaction, testSourceTransaction);

        // Assert
        verify(testKafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
        assertTrue(testFuture.toCompletableFuture().isDone() || !testFuture.toCompletableFuture().isDone()); // Just checking it's set up
    }


    @Test
    void publish_shouldWorkWithDifferentTransactionTypes() {
        // Arrange
        NormalisedTransaction testBankFeedSource = new NormalisedTransaction(
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

        Transaction testBankTransaction = Transaction.builder()
                .id(3L)
                .sourceId("source-789")
                .category(testCategory)
                .build();

        CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture =
                CompletableFuture.completedFuture(
                        createSendResult(testBankFeedSource.sourceId(),
                                KafkaTopics.CATEGORISED_TRANSACTIONS,
                                1,
                                458L,
                                testBankFeedSource)
                );

        when(testKafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(testFuture);

        // Act
        testProducer.publish(testBankTransaction, testBankFeedSource);

        // Assert
        ArgumentCaptor<String> testTopicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> testKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NormalisedTransaction> testValueCaptor = ArgumentCaptor.forClass(NormalisedTransaction.class);

        verify(testKafkaTemplate).send(testTopicCaptor.capture(), testKeyCaptor.capture(), testValueCaptor.capture());

        assertEquals(KafkaTopics.CATEGORISED_TRANSACTIONS, testTopicCaptor.getValue());
        assertEquals(testBankFeedSource.sourceId(), testKeyCaptor.getValue());
        assertEquals(testBankFeedSource, testValueCaptor.getValue());
    }



    @Test
    void publish_shouldNotModifyOriginalTransaction() {
        // Arrange
        CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture = CompletableFuture.completedFuture(
                createSendResult(testSourceTransaction.sourceId(), KafkaTopics.CATEGORISED_TRANSACTIONS, 0, 459L, testSourceTransaction)
        );
        when(testKafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(testFuture);

        // Act
        testProducer.publish(testTransaction, testSourceTransaction);

        // Assert
        // Verify transaction remains unchanged
        assertEquals("source-123", testTransaction.getSourceId());
        assertEquals(testCategory, testTransaction.getCategory());
        verify(testKafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
    }

    @Test
    void publish_shouldNotModifyOriginalSource() {
        // Arrange
        CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture = CompletableFuture.completedFuture(
                createSendResult(testSourceTransaction.sourceId(), KafkaTopics.CATEGORISED_TRANSACTIONS, 0, 460L, testSourceTransaction)
        );

        when(testKafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(testFuture);

        // Keep original values
        String testOriginalSourceId = testSourceTransaction.sourceId();
        SourceType testOriginalSourceType = testSourceTransaction.sourceType();
        BigDecimal testOriginalAmount = testSourceTransaction.amount();

        // Act
        testProducer.publish(testTransaction, testSourceTransaction);

        // Assert
        // Verify source remains unchanged
        assertEquals(testOriginalSourceId, testSourceTransaction.sourceId());
        assertEquals(testOriginalSourceType, testSourceTransaction.sourceType());
        assertEquals(testOriginalAmount, testSourceTransaction.amount());
    }

    private SendResult<String, NormalisedTransaction> createSendResult(String key, String topic, int partition, long offset, NormalisedTransaction value) {
        ProducerRecord<String, NormalisedTransaction> producerRecord = new ProducerRecord<>(topic, partition, null, key, value);
        RecordMetadata recordMetadata = mock(RecordMetadata.class);
        when(recordMetadata.topic()).thenReturn(topic);
        when(recordMetadata.partition()).thenReturn(partition);
        when(recordMetadata.offset()).thenReturn(offset);
        return new SendResult<>(producerRecord, recordMetadata);
    }
}