package za.co.reed.mocksourceservice.paymentprocessor;

import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.mocksourceservice.adapter.PaymentProcessorAdapter;
import za.co.reed.mocksourceservice.client.IngestorClient;
import za.co.reed.mocksourceservice.dto.PaymentEvent;
import za.co.reed.mocksourceservice.generator.PaymentDataGenerator;
import za.co.reed.mocksourceservice.properties.PaymentProcessorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.reed.mocksourceservice.scheduler.PaymentWebhookEmitter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentWebhookEmitter}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookEmitterTest {

    @Mock
    private PaymentDataGenerator testGenerator;

    @Mock
    private PaymentProcessorAdapter testAdapter;

    @Mock
    private PaymentProcessorProperties testProperties;

    @Mock
    private IngestorClient testIngestorClient;

    private PaymentWebhookEmitter testPaymentWebhookEmitter;

    @BeforeEach
    void setUp() {
        testPaymentWebhookEmitter = new PaymentWebhookEmitter(testGenerator, testAdapter, testProperties, testIngestorClient);
    }

    @Test
    void emit_generatesAndSendsTransactions() {
        // Given
        when(testProperties.getEventsPerEmit()).thenReturn(3);
        when(testProperties.isRetrySimulation()).thenReturn(false);

        List<PaymentEvent> testRawEvents = List.of(
                new PaymentEvent("evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0),
                new PaymentEvent("evt_2", "payment_intent.created", "pi_2", "cus_002", "Merchant2",
                        new BigDecimal("200.00"), Currency.getInstance("USD"), Instant.now().minusSeconds(7200), 0)
        );

        List<NormalisedTransaction> testNormalisedTransactions = List.of(
                new NormalisedTransaction("pi_1", SourceType.PAYMENT_PROCESSOR, "cus_001",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        "Merchant1", null, Instant.now().minusSeconds(3600),
                        TransactionStatus.SETTLED, "{}"),
                new NormalisedTransaction("pi_2", SourceType.PAYMENT_PROCESSOR, "cus_002",
                        new BigDecimal("200.00"), Currency.getInstance("USD"),
                        "Merchant2", null, Instant.now().minusSeconds(7200),
                        TransactionStatus.PENDING, "{}")
        );

        when(testGenerator.generate(3, false)).thenReturn(testRawEvents);
        when(testAdapter.normalise(testRawEvents)).thenReturn(testNormalisedTransactions);

        // When
        testPaymentWebhookEmitter.emit();

        // Then
        verify(testGenerator).generate(3, false);
        verify(testAdapter).normalise(testRawEvents);
        verify(testIngestorClient, times(2)).send(any(NormalisedTransaction.class));
        verify(testIngestorClient).send(testNormalisedTransactions.get(0));
        verify(testIngestorClient).send(testNormalisedTransactions.get(1));
    }

    @Test
    void emit_withRetrySimulation() {
        // Given
        when(testProperties.getEventsPerEmit()).thenReturn(2);
        when(testProperties.isRetrySimulation()).thenReturn(true);

        List<PaymentEvent> testRawEvents = List.of(
                new PaymentEvent("evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0),
                new PaymentEvent("evt_1_retry", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 1)
        );

        List<NormalisedTransaction> testNormalisedTransactions = List.of(
                new NormalisedTransaction("pi_1", SourceType.PAYMENT_PROCESSOR, "cus_001",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        "Merchant1", null, Instant.now().minusSeconds(3600),
                        TransactionStatus.SETTLED, "{}")
        );

        when(testGenerator.generate(2, true)).thenReturn(testRawEvents);
        when(testAdapter.normalise(testRawEvents)).thenReturn(testNormalisedTransactions);

        // When
        testPaymentWebhookEmitter.emit();

        // Then
        verify(testGenerator).generate(2, true);
        verify(testAdapter).normalise(testRawEvents);
        verify(testIngestorClient, times(1)).send(any(NormalisedTransaction.class));
    }

    @Test
    void emit_handlesEmptyBatch() {
        // Given
        when(testProperties.getEventsPerEmit()).thenReturn(0);
        when(testProperties.isRetrySimulation()).thenReturn(false);

        List<PaymentEvent> testRawEvents = List.of();
        List<NormalisedTransaction> testNormalisedTransactions = List.of();

        when(testGenerator.generate(0, false)).thenReturn(testRawEvents);
        when(testAdapter.normalise(testRawEvents)).thenReturn(testNormalisedTransactions);

        // When
        testPaymentWebhookEmitter.emit();

        // Then
        verify(testGenerator).generate(0, false);
        verify(testAdapter).normalise(testRawEvents);
        verify(testIngestorClient, never()).send(any(NormalisedTransaction.class));
    }

    @Test
    void emit_handlesDeduplication() {
        // Given
        when(testProperties.getEventsPerEmit()).thenReturn(3);
        when(testProperties.isRetrySimulation()).thenReturn(false);

        List<PaymentEvent> testRawEvents = List.of(
                new PaymentEvent("evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0),
                new PaymentEvent("evt_2", "payment_intent.created", "pi_2", "cus_002", "Merchant2",
                        new BigDecimal("200.00"), Currency.getInstance("USD"), Instant.now().minusSeconds(7200), 0),
                new PaymentEvent("evt_3", "charge.refunded", "pi_3", "cus_003", "Merchant3",
                        new BigDecimal("300.00"), Currency.getInstance("GBP"), Instant.now().minusSeconds(10800), 0)
        );

        List<NormalisedTransaction> testNormalisedTransactions = List.of(
                new NormalisedTransaction("pi_1", SourceType.PAYMENT_PROCESSOR, "cus_001",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        "Merchant1", null, Instant.now().minusSeconds(3600),
                        TransactionStatus.SETTLED, "{}"),
                new NormalisedTransaction("pi_3", SourceType.PAYMENT_PROCESSOR, "cus_003",
                        new BigDecimal("300.00"), Currency.getInstance("GBP"),
                        "Merchant3", null, Instant.now().minusSeconds(10800),
                        TransactionStatus.REVERSED, "{}")
        );

        when(testGenerator.generate(3, false)).thenReturn(testRawEvents);
        when(testAdapter.normalise(testRawEvents)).thenReturn(testNormalisedTransactions);

        // When
        testPaymentWebhookEmitter.emit();

        // Then
        verify(testIngestorClient, times(2)).send(any(NormalisedTransaction.class));
    }

    @Test
    void emit_handlesIngestorClientException() {
        // Given
        when(testProperties.getEventsPerEmit()).thenReturn(1);
        when(testProperties.isRetrySimulation()).thenReturn(false);

        List<PaymentEvent> testRawEvents = List.of(
                new PaymentEvent("evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0)
        );

        List<NormalisedTransaction> testNormalisedTransactions = List.of(
                new NormalisedTransaction("pi_1", SourceType.PAYMENT_PROCESSOR, "cus_001",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        "Merchant1", null, Instant.now().minusSeconds(3600),
                        TransactionStatus.SETTLED, "{}")
        );

        when(testGenerator.generate(1, false)).thenReturn(testRawEvents);
        when(testAdapter.normalise(testRawEvents)).thenReturn(testNormalisedTransactions);

        doThrow(new RuntimeException("Network error")).when(testIngestorClient).send(any(NormalisedTransaction.class));

        // When
        testPaymentWebhookEmitter.emit();

        // Then
        verify(testGenerator).generate(1, false);
        verify(testAdapter).normalise(testRawEvents);
        verify(testIngestorClient).send(any(NormalisedTransaction.class));
    }

    @Test
    void emit_logsCorrectStatistics() {
        // Given
        when(testProperties.getEventsPerEmit()).thenReturn(5);
        when(testProperties.isRetrySimulation()).thenReturn(false);

        List<PaymentEvent> testRawEvents = List.of(
                new PaymentEvent("evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0),
                new PaymentEvent("evt_2", "payment_intent.created", "pi_2", "cus_002", "Merchant2",
                        new BigDecimal("200.00"), Currency.getInstance("USD"), Instant.now().minusSeconds(7200), 0),
                new PaymentEvent("evt_3", "charge.refunded", "pi_3", "cus_003", "Merchant3",
                        new BigDecimal("300.00"), Currency.getInstance("GBP"), Instant.now().minusSeconds(10800), 0),
                new PaymentEvent("evt_4", "payment_intent.succeeded", "pi_4", "cus_004", "Merchant4",
                        new BigDecimal("400.00"), Currency.getInstance("EUR"), Instant.now().minusSeconds(14400), 0),
                new PaymentEvent("evt_5", "payment_intent.created", "pi_5", "cus_005", "Merchant5",
                        new BigDecimal("500.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(18000), 0)
        );

        List<NormalisedTransaction> testNormalisedTransactions = List.of(
                new NormalisedTransaction("pi_1", SourceType.PAYMENT_PROCESSOR, "cus_001",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        "Merchant1", null, Instant.now().minusSeconds(3600),
                        TransactionStatus.SETTLED, "{}"),
                new NormalisedTransaction("pi_3", SourceType.PAYMENT_PROCESSOR, "cus_003",
                        new BigDecimal("300.00"), Currency.getInstance("GBP"),
                        "Merchant3", null, Instant.now().minusSeconds(10800),
                        TransactionStatus.REVERSED, "{}"),
                new NormalisedTransaction("pi_5", SourceType.PAYMENT_PROCESSOR, "cus_005",
                        new BigDecimal("500.00"), Currency.getInstance("ZAR"),
                        "Merchant5", null, Instant.now().minusSeconds(18000),
                        TransactionStatus.PENDING, "{}")
        );

        when(testGenerator.generate(5, false)).thenReturn(testRawEvents);
        when(testAdapter.normalise(testRawEvents)).thenReturn(testNormalisedTransactions);

        // When
        testPaymentWebhookEmitter.emit();

        // Then
        verify(testGenerator).generate(5, false);
        verify(testAdapter).normalise(testRawEvents);
        verify(testIngestorClient, times(3)).send(any(NormalisedTransaction.class));
    }

    @Test
    void emit_usesPropertiesValues() {
        // Given
        when(testProperties.getEventsPerEmit()).thenReturn(10);
        when(testProperties.isRetrySimulation()).thenReturn(true);

        List<PaymentEvent> testRawEvents = List.of();
        List<NormalisedTransaction> testNormalisedTransactions = List.of();

        when(testGenerator.generate(10, true)).thenReturn(testRawEvents);
        when(testAdapter.normalise(testRawEvents)).thenReturn(testNormalisedTransactions);

        // When
        testPaymentWebhookEmitter.emit();

        // Then
        verify(testGenerator).generate(10, true);
        verify(testAdapter).normalise(testRawEvents);
    }

    @Test
    void emit_withRetrySimulationGeneratesRetryEvents() {
        // Given
        when(testProperties.getEventsPerEmit()).thenReturn(100);
        when(testProperties.isRetrySimulation()).thenReturn(true);

        List<PaymentEvent> testRawEvents = List.of(
                new PaymentEvent("evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0),
                new PaymentEvent("evt_1_retry", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 1)
        );

        List<NormalisedTransaction> testNormalisedTransactions = List.of(
                new NormalisedTransaction("pi_1", SourceType.PAYMENT_PROCESSOR, "cus_001",
                        new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                        "Merchant1", null, Instant.now().minusSeconds(3600),
                        TransactionStatus.SETTLED, "{}")
        );

        when(testGenerator.generate(100, true)).thenReturn(testRawEvents);
        when(testAdapter.normalise(testRawEvents)).thenReturn(testNormalisedTransactions);

        // When
        testPaymentWebhookEmitter.emit();

        // Then
        verify(testGenerator).generate(100, true);
        verify(testAdapter).normalise(testRawEvents);
        verify(testIngestorClient, times(1)).send(any(NormalisedTransaction.class));
    }
}