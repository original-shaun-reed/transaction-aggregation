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
    private PaymentDataGenerator generator;
    
    @Mock
    private PaymentProcessorAdapter adapter;
    
    @Mock
    private PaymentProcessorProperties properties;
    
    @Mock
    private IngestorClient ingestorClient;
    
    private PaymentWebhookEmitter paymentWebhookEmitter;
    
    @BeforeEach
    void setUp() {
        paymentWebhookEmitter = new PaymentWebhookEmitter(generator, adapter, properties, ingestorClient);
    }
    
    @Test
    void emit_generatesAndSendsTransactions() {
        // Given
        when(properties.getEventsPerEmit()).thenReturn(3);
        when(properties.isRetrySimulation()).thenReturn(false);
        
        List<PaymentEvent> rawEvents = List.of(
            new PaymentEvent("evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0),
            new PaymentEvent("evt_2", "payment_intent.created", "pi_2", "cus_002", "Merchant2",
                new BigDecimal("200.00"), Currency.getInstance("USD"), Instant.now().minusSeconds(7200), 0)
        );
        
        List<NormalisedTransaction> normalisedTransactions = List.of(
            new NormalisedTransaction("pi_1", SourceType.PAYMENT_PROCESSOR, "cus_001",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                "Merchant1", null, Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED, "{}"),
            new NormalisedTransaction("pi_2", SourceType.PAYMENT_PROCESSOR, "cus_002",
                new BigDecimal("200.00"), Currency.getInstance("USD"),
                "Merchant2", null, Instant.now().minusSeconds(7200),
                TransactionStatus.PENDING, "{}")
        );
        
        when(generator.generate(3, false)).thenReturn(rawEvents);
        when(adapter.normalise(rawEvents)).thenReturn(normalisedTransactions);
        
        // When
        paymentWebhookEmitter.emit();
        
        // Then
        verify(generator).generate(3, false);
        verify(adapter).normalise(rawEvents);
        verify(ingestorClient, times(2)).send(any(NormalisedTransaction.class));
        verify(ingestorClient).send(normalisedTransactions.get(0));
        verify(ingestorClient).send(normalisedTransactions.get(1));
    }
    
    @Test
    void emit_withRetrySimulation() {
        // Given
        when(properties.getEventsPerEmit()).thenReturn(2);
        when(properties.isRetrySimulation()).thenReturn(true);
        
        List<PaymentEvent> rawEvents = List.of(
            new PaymentEvent("evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0),
            new PaymentEvent("evt_1_retry", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 1)
        );
        
        List<NormalisedTransaction> normalisedTransactions = List.of(
            new NormalisedTransaction("pi_1", SourceType.PAYMENT_PROCESSOR, "cus_001",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                "Merchant1", null, Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED, "{}")
        );
        
        when(generator.generate(2, true)).thenReturn(rawEvents);
        when(adapter.normalise(rawEvents)).thenReturn(normalisedTransactions);
        
        // When
        paymentWebhookEmitter.emit();
        
        // Then
        verify(generator).generate(2, true);
        verify(adapter).normalise(rawEvents);
        verify(ingestorClient, times(1)).send(any(NormalisedTransaction.class));
    }
    
    @Test
    void emit_handlesEmptyBatch() {
        // Given
        when(properties.getEventsPerEmit()).thenReturn(0);
        when(properties.isRetrySimulation()).thenReturn(false);
        
        List<PaymentEvent> rawEvents = List.of();
        List<NormalisedTransaction> normalisedTransactions = List.of();
        
        when(generator.generate(0, false)).thenReturn(rawEvents);
        when(adapter.normalise(rawEvents)).thenReturn(normalisedTransactions);
        
        // When
        paymentWebhookEmitter.emit();
        
        // Then
        verify(generator).generate(0, false);
        verify(adapter).normalise(rawEvents);
        verify(ingestorClient, never()).send(any(NormalisedTransaction.class));
    }
    
    @Test
    void emit_handlesDeduplication() {
        // Given
        when(properties.getEventsPerEmit()).thenReturn(3);
        when(properties.isRetrySimulation()).thenReturn(false);
        
        List<PaymentEvent> rawEvents = List.of(
            new PaymentEvent("evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0),
            new PaymentEvent("evt_2", "payment_intent.created", "pi_2", "cus_002", "Merchant2",
                new BigDecimal("200.00"), Currency.getInstance("USD"), Instant.now().minusSeconds(7200), 0),
            new PaymentEvent("evt_3", "charge.refunded", "pi_3", "cus_003", "Merchant3",
                new BigDecimal("300.00"), Currency.getInstance("GBP"), Instant.now().minusSeconds(10800), 0)
        );
        
        // Simulate adapter filtering out one duplicate
        List<NormalisedTransaction> normalisedTransactions = List.of(
            new NormalisedTransaction("pi_1", SourceType.PAYMENT_PROCESSOR, "cus_001",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                "Merchant1", null, Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED, "{}"),
            new NormalisedTransaction("pi_3", SourceType.PAYMENT_PROCESSOR, "cus_003",
                new BigDecimal("300.00"), Currency.getInstance("GBP"),
                "Merchant3", null, Instant.now().minusSeconds(10800),
                TransactionStatus.REVERSED, "{}")
        );
        
        when(generator.generate(3, false)).thenReturn(rawEvents);
        when(adapter.normalise(rawEvents)).thenReturn(normalisedTransactions);
        
        // When
        paymentWebhookEmitter.emit();
        
        // Then - Should only send the two non-duplicate transactions
        verify(ingestorClient, times(2)).send(any(NormalisedTransaction.class));
    }
    
    @Test
    void emit_handlesIngestorClientException() {
        // Given
        when(properties.getEventsPerEmit()).thenReturn(1);
        when(properties.isRetrySimulation()).thenReturn(false);
        
        List<PaymentEvent> rawEvents = List.of(
            new PaymentEvent("evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0)
        );
        
        List<NormalisedTransaction> normalisedTransactions = List.of(
            new NormalisedTransaction("pi_1", SourceType.PAYMENT_PROCESSOR, "cus_001",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                "Merchant1", null, Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED, "{}")
        );
        
        when(generator.generate(1, false)).thenReturn(rawEvents);
        when(adapter.normalise(rawEvents)).thenReturn(normalisedTransactions);
        
        // Simulate ingestor client throwing exception
        doThrow(new RuntimeException("Network error")).when(ingestorClient).send(any(NormalisedTransaction.class));
        
        // When - Should not throw exception
        paymentWebhookEmitter.emit();
        
        // Then - Exception should be caught and logged, but emit should complete
        verify(generator).generate(1, false);
        verify(adapter).normalise(rawEvents);
        verify(ingestorClient).send(any(NormalisedTransaction.class));
    }
    
    @Test
    void emit_logsCorrectStatistics() {
        // Given
        when(properties.getEventsPerEmit()).thenReturn(5);
        when(properties.isRetrySimulation()).thenReturn(false);
        
        // Generate 5 raw events, but adapter filters 2 as duplicates
        List<PaymentEvent> rawEvents = List.of(
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
        
        // Adapter returns 3 transactions (filtering 2 duplicates)
        List<NormalisedTransaction> normalisedTransactions = List.of(
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
        
        when(generator.generate(5, false)).thenReturn(rawEvents);
        when(adapter.normalise(rawEvents)).thenReturn(normalisedTransactions);
        
        // When
        paymentWebhookEmitter.emit();
        
        // Then - Should log statistics: 3 transactions (5 raw, 2 deduped out)
        verify(generator).generate(5, false);
        verify(adapter).normalise(rawEvents);
        verify(ingestorClient, times(3)).send(any(NormalisedTransaction.class));
    }
    
    @Test
    void emit_usesPropertiesValues() {
        // Given
        when(properties.getEventsPerEmit()).thenReturn(10);
        when(properties.isRetrySimulation()).thenReturn(true);
        
        List<PaymentEvent> rawEvents = List.of();
        List<NormalisedTransaction> normalisedTransactions = List.of();
        
        when(generator.generate(10, true)).thenReturn(rawEvents);
        when(adapter.normalise(rawEvents)).thenReturn(normalisedTransactions);
        
        // When
        paymentWebhookEmitter.emit();
        
        // Then
        verify(generator).generate(10, true);
        verify(adapter).normalise(rawEvents);
    }
    
    @Test
    void emit_withRetrySimulationGeneratesRetryEvents() {
        // Given
        when(properties.getEventsPerEmit()).thenReturn(100);
        when(properties.isRetrySimulation()).thenReturn(true);
        
        // Mock generator to return some events with retryAttempt = 1
        List<PaymentEvent> rawEvents = List.of(
            new PaymentEvent("evt_1", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 0),
            new PaymentEvent("evt_1_retry", "payment_intent.succeeded", "pi_1", "cus_001", "Merchant1",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"), Instant.now().minusSeconds(3600), 1)
        );
        
        List<NormalisedTransaction> normalisedTransactions = List.of(
            new NormalisedTransaction("pi_1", SourceType.PAYMENT_PROCESSOR, "cus_001",
                new BigDecimal("100.00"), Currency.getInstance("ZAR"),
                "Merchant1", null, Instant.now().minusSeconds(3600),
                TransactionStatus.SETTLED, "{}")
        );
        
        when(generator.generate(100, true)).thenReturn(rawEvents);
        when(adapter.normalise(rawEvents)).thenReturn(normalisedTransactions);
        
        // When
        paymentWebhookEmitter.emit();
        
        // Then
        verify(generator).generate(100, true);
        verify(adapter).normalise(rawEvents);
        // Adapter should deduplicate the retry event
        verify(ingestorClient, times(1)).send(any(NormalisedTransaction.class));
    }
}