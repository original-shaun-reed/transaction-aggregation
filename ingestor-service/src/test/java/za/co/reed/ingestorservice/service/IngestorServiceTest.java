package za.co.reed.ingestorservice.service;

import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.ingestorservice.enums.IngestResult;
import za.co.reed.ingestorservice.kafka.TransactionProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IngestorService}.
 * 
 * Tests cover:
 * - Successful ingestion flow (validation → deduplication → publish → mark seen)
 * - Duplicate transaction handling
 * - Validation failure handling
 * - Kafka publish failure handling
 * - Logging behavior
 */
@ExtendWith(MockitoExtension.class)
class IngestorServiceTest {

    @Mock
    private NormalisationService testNormalisationService;

    @Mock
    private DeduplicationService testDeduplicationService;

    @Mock
    private TransactionProducer testTransactionProducer;

    private IngestorService testIngestorService;

    private NormalisedTransaction testValidTransaction;

    @BeforeEach
    void setUp() {
        testIngestorService = new IngestorService(testNormalisationService, testDeduplicationService, testTransactionProducer);

        testValidTransaction = new NormalisedTransaction(
                "txn-12345",
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("100.50"),
                java.util.Currency.getInstance("ZAR"),
                "Test Merchant",
                "123456",
                Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );
    }

    @Test
    void ingest_returnsAccepted_whenTransactionIsValidAndNotDuplicate() {
        doNothing().when(testNormalisationService).validate(testValidTransaction);
        when(testDeduplicationService.isDuplicate(testValidTransaction.sourceId())).thenReturn(false);
        doNothing().when(testTransactionProducer).publish(testValidTransaction);

        IngestResult testResult = testIngestorService.ingest(testValidTransaction);

        assertThat(testResult).isEqualTo(IngestResult.ACCEPTED);
        verify(testNormalisationService).validate(testValidTransaction);
        verify(testDeduplicationService).isDuplicate(testValidTransaction.sourceId());
        verify(testTransactionProducer).publish(testValidTransaction);
        verify(testDeduplicationService).markSeen(testValidTransaction.sourceId());
    }

    @Test
    void ingest_returnsDuplicate_whenTransactionIsDuplicate() {
        doNothing().when(testNormalisationService).validate(testValidTransaction);
        when(testDeduplicationService.isDuplicate(testValidTransaction.sourceId())).thenReturn(true);

        IngestResult testResult = testIngestorService.ingest(testValidTransaction);

        assertThat(testResult).isEqualTo(IngestResult.DUPLICATE);
        verify(testNormalisationService).validate(testValidTransaction);
        verify(testDeduplicationService).isDuplicate(testValidTransaction.sourceId());
        verify(testTransactionProducer, never()).publish(any());
        verify(testDeduplicationService, never()).markSeen(any());
    }

    @Test
    void ingest_returnsFailed_whenValidationThrowsIllegalArgumentException() {
        doThrow(new IllegalArgumentException("Invalid transaction"))
                .when(testNormalisationService).validate(testValidTransaction);

        IngestResult testResult = testIngestorService.ingest(testValidTransaction);

        assertThat(testResult).isEqualTo(IngestResult.FAILED);
        verify(testNormalisationService).validate(testValidTransaction);
        verify(testDeduplicationService, never()).isDuplicate(any());
        verify(testTransactionProducer, never()).publish(any());
        verify(testDeduplicationService, never()).markSeen(any());
    }

    @Test
    void ingest_returnsFailed_whenValidationThrowsAnyException() {
        doThrow(new RuntimeException("Unexpected error"))
                .when(testNormalisationService).validate(testValidTransaction);

        IngestResult testResult = testIngestorService.ingest(testValidTransaction);

        assertThat(testResult).isEqualTo(IngestResult.FAILED);
        verify(testNormalisationService).validate(testValidTransaction);
        verify(testDeduplicationService, never()).isDuplicate(any());
        verify(testTransactionProducer, never()).publish(any());
        verify(testDeduplicationService, never()).markSeen(any());
    }

    @Test
    void ingest_returnsFailed_whenKafkaPublishThrowsException() {
        doNothing().when(testNormalisationService).validate(testValidTransaction);
        when(testDeduplicationService.isDuplicate(testValidTransaction.sourceId())).thenReturn(false);
        doThrow(new RuntimeException("Kafka error")).when(testTransactionProducer).publish(testValidTransaction);

        IngestResult testResult = testIngestorService.ingest(testValidTransaction);

        assertThat(testResult).isEqualTo(IngestResult.FAILED);
        verify(testNormalisationService).validate(testValidTransaction);
        verify(testDeduplicationService).isDuplicate(testValidTransaction.sourceId());
        verify(testTransactionProducer).publish(testValidTransaction);
        verify(testDeduplicationService, never()).markSeen(any());
    }

    @Test
    void ingest_doesNotMarkSeen_whenDuplicateDetected() {
        doNothing().when(testNormalisationService).validate(testValidTransaction);
        when(testDeduplicationService.isDuplicate(testValidTransaction.sourceId())).thenReturn(true);

        testIngestorService.ingest(testValidTransaction);

        verify(testDeduplicationService, never()).markSeen(any());
    }

    @Test
    void ingest_marksSeenOnlyAfterSuccessfulPublish() {
        doNothing().when(testNormalisationService).validate(testValidTransaction);
        when(testDeduplicationService.isDuplicate(testValidTransaction.sourceId())).thenReturn(false);
        doNothing().when(testTransactionProducer).publish(testValidTransaction);

        testIngestorService.ingest(testValidTransaction);

        verify(testTransactionProducer).publish(testValidTransaction);
        verify(testDeduplicationService).markSeen(testValidTransaction.sourceId());
    }

    @Test
    void ingest_logsInfoMessage_whenTransactionAccepted() {
        doNothing().when(testNormalisationService).validate(testValidTransaction);
        when(testDeduplicationService.isDuplicate(testValidTransaction.sourceId())).thenReturn(false);
        doNothing().when(testTransactionProducer).publish(testValidTransaction);

        testIngestorService.ingest(testValidTransaction);

        verify(testTransactionProducer).publish(testValidTransaction);
        verify(testDeduplicationService).markSeen(testValidTransaction.sourceId());
    }

    @Test
    void ingest_logsInfoMessage_whenTransactionDuplicate() {
        doNothing().when(testNormalisationService).validate(testValidTransaction);
        when(testDeduplicationService.isDuplicate(testValidTransaction.sourceId())).thenReturn(true);

        testIngestorService.ingest(testValidTransaction);

        verify(testTransactionProducer, never()).publish(any());
        verify(testDeduplicationService, never()).markSeen(any());
    }

    @Test
    void ingest_logsWarning_whenValidationFails() {
        IllegalArgumentException testValidationError = new IllegalArgumentException("Invalid amount");
        doThrow(testValidationError).when(testNormalisationService).validate(testValidTransaction);

        testIngestorService.ingest(testValidTransaction);

        verify(testDeduplicationService, never()).isDuplicate(any());
        verify(testTransactionProducer, never()).publish(any());
    }

    @Test
    void ingest_logsError_whenKafkaPublishFails() {
        doNothing().when(testNormalisationService).validate(testValidTransaction);
        when(testDeduplicationService.isDuplicate(testValidTransaction.sourceId())).thenReturn(false);
        RuntimeException testKafkaError = new RuntimeException("Kafka connection failed");
        doThrow(testKafkaError).when(testTransactionProducer).publish(testValidTransaction);

        testIngestorService.ingest(testValidTransaction);

        verify(testTransactionProducer).publish(testValidTransaction);
        verify(testDeduplicationService, never()).markSeen(any());
    }

    @Test
    void ingest_handlesFutureDatedTransaction() {
        NormalisedTransaction testFutureTransaction = new NormalisedTransaction(
                "txn-future",
                SourceType.CARD_NETWORK,
                "ACC-002",
                new BigDecimal("50.00"),
                java.util.Currency.getInstance("ZAR"),
                "Future Merchant",
                "654321",
                Instant.now().plusSeconds(3600 * 24),
                TransactionStatus.SETTLED,
                "{\"raw\": \"future\"}"
        );

        doThrow(new IllegalArgumentException("transactedAt is in the future"))
                .when(testNormalisationService).validate(testFutureTransaction);

        IngestResult testResult = testIngestorService.ingest(testFutureTransaction);

        assertThat(testResult).isEqualTo(IngestResult.FAILED);
        verify(testNormalisationService).validate(testFutureTransaction);
    }

    @Test
    void ingest_handlesLargeAmountTransaction() {
        NormalisedTransaction testLargeTransaction = new NormalisedTransaction(
                "txn-large",
                SourceType.CARD_NETWORK,
                "ACC-003",
                new BigDecimal("200000.00"),
                java.util.Currency.getInstance("ZAR"),
                "Big Merchant",
                "999999",
                Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED,
                "{\"raw\": \"large\"}"
        );

        doThrow(new IllegalArgumentException("amount exceeds maximum allowed"))
                .when(testNormalisationService).validate(testLargeTransaction);

        IngestResult testResult = testIngestorService.ingest(testLargeTransaction);

        assertThat(testResult).isEqualTo(IngestResult.FAILED);
        verify(testNormalisationService).validate(testLargeTransaction);
    }
}