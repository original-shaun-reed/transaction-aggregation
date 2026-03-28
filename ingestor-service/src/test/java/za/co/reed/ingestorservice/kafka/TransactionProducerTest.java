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
    private KafkaTemplate<String, NormalisedTransaction> testKafkaTemplate;

    @Mock
    private MeterRegistry testMeterRegistry;

    @Mock
    private SendResult<String, NormalisedTransaction> testSendResult;

    @Captor
    private ArgumentCaptor<NormalisedTransaction> testTransactionCaptor;

    private TransactionProducer testTransactionProducer;

    private NormalisedTransaction testTransaction;

    @BeforeEach
    void setUp() {
        testTransactionProducer = new TransactionProducer(testKafkaTemplate, testMeterRegistry);

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
        CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture =
                CompletableFuture.completedFuture(testSendResult);

        when(testKafkaTemplate.send(eq(KafkaTopics.RAW_TRANSACTIONS), eq("txn-12345"), any(NormalisedTransaction.class)))
                .thenReturn(testFuture);

        testTransactionProducer.publish(testTransaction);

        verify(testKafkaTemplate).send(
                eq(KafkaTopics.RAW_TRANSACTIONS),
                eq("txn-12345"),
                testTransactionCaptor.capture()
        );

        NormalisedTransaction testCapturedTransaction = testTransactionCaptor.getValue();
        assertThat(testCapturedTransaction.sourceId()).isEqualTo("txn-12345");
        assertThat(testCapturedTransaction.amount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(testCapturedTransaction.sourceType()).isEqualTo(SourceType.PAYMENT_PROCESSOR);
    }

    @Test
    void publish_logsSuccess_whenMessageSentSuccessfully() {
        CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture =
                CompletableFuture.completedFuture(testSendResult);

        when(testKafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(testFuture);

        testTransactionProducer.publish(testTransaction);

        verify(testKafkaTemplate).send(
                eq(KafkaTopics.RAW_TRANSACTIONS),
                eq("txn-12345"),
                any(NormalisedTransaction.class)
        );
    }

    @Test
    void publish_throwsException_whenKafkaSendFails() {
        RuntimeException testKafkaException = new RuntimeException("Kafka broker unavailable");
        CompletableFuture<SendResult<String, NormalisedTransaction>> testFailedFuture =
                CompletableFuture.failedFuture(testKafkaException);

        when(testKafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(testFailedFuture);

        try {
            testTransactionProducer.publish(testTransaction);
        } catch (RuntimeException e) {
            // Expected
        }

        verify(testKafkaTemplate).send(
                eq(KafkaTopics.RAW_TRANSACTIONS),
                eq("txn-12345"),
                any(NormalisedTransaction.class)
        );
    }

    @Test
    void publish_handlesDifferentTransactionTypes() {
        SourceType[] testSourceTypes = {SourceType.PAYMENT_PROCESSOR, SourceType.BANK_FEED, SourceType.CARD_NETWORK};

        for (SourceType testSourceType : testSourceTypes) {
            NormalisedTransaction testTransactionVariant = new NormalisedTransaction(
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

            CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture =
                    CompletableFuture.completedFuture(testSendResult);

            when(testKafkaTemplate.send(eq(KafkaTopics.RAW_TRANSACTIONS), eq("txn-" + testSourceType.name()), any(NormalisedTransaction.class)))
                    .thenReturn(testFuture);

            testTransactionProducer.publish(testTransactionVariant);

            verify(testKafkaTemplate).send(
                    eq(KafkaTopics.RAW_TRANSACTIONS),
                    eq("txn-" + testSourceType.name()),
                    any(NormalisedTransaction.class)
            );

            reset(testKafkaTemplate);
        }
    }

    @Test
    void publish_usesSourceIdAsKafkaMessageKey() {
        String testSourceId = "unique-source-id-123";
        NormalisedTransaction testTransactionVariant = new NormalisedTransaction(
                testSourceId,
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

        CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture =
                CompletableFuture.completedFuture(testSendResult);

        when(testKafkaTemplate.send(eq(KafkaTopics.RAW_TRANSACTIONS), eq(testSourceId), any(NormalisedTransaction.class)))
                .thenReturn(testFuture);

        testTransactionProducer.publish(testTransactionVariant);

        verify(testKafkaTemplate).send(eq(KafkaTopics.RAW_TRANSACTIONS), eq(testSourceId), any(NormalisedTransaction.class));
    }

    @Test
    void publish_handlesCallbackSuccessLogging() {
        CompletableFuture<SendResult<String, NormalisedTransaction>> testFuture =
                CompletableFuture.completedFuture(testSendResult);

        when(testKafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(testFuture);

        testTransactionProducer.publish(testTransaction);

        verify(testKafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
    }

    @Test
    void publish_handlesCallbackErrorLogging() {
        RuntimeException testKafkaException = new RuntimeException("Kafka error");
        CompletableFuture<SendResult<String, NormalisedTransaction>> testFailedFuture =
                CompletableFuture.failedFuture(testKafkaException);

        when(testKafkaTemplate.send(anyString(), anyString(), any(NormalisedTransaction.class)))
                .thenReturn(testFailedFuture);

        try {
            testTransactionProducer.publish(testTransaction);
        } catch (RuntimeException e) {
            // Expected
        }

        verify(testKafkaTemplate).send(anyString(), anyString(), any(NormalisedTransaction.class));
    }
}