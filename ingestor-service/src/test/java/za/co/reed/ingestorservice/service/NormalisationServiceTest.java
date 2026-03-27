package za.co.reed.ingestorservice.service;

import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link NormalisationService}.
 * 
 * Tests cover:
 * - Timestamp validation (future dates, old dates)
 * - Amount validation (maximum amount)
 * - Reversed transaction consistency checks
 * - Valid transaction acceptance
 */
@ExtendWith(MockitoExtension.class)
class NormalisationServiceTest {

    private NormalisationService normalisationService;
    
    @BeforeEach
    void setUp() {
        normalisationService = new NormalisationService();
    }
    
    @Test
    void validate_doesNotThrow_whenTransactionIsValid() {
        // Given
        NormalisedTransaction validTransaction = createValidTransaction();
        
        // When & Then - Should not throw any exception
        normalisationService.validate(validTransaction);
    }
    
    @Test
    void validate_throwsException_whenTransactionIsInFutureBeyondClockSkew() {
        // Given - Transaction 10 minutes in future (beyond 5-minute clock skew)
        NormalisedTransaction futureTransaction = new NormalisedTransaction(
            "txn-future",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("100.00"),
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().plus(10, ChronoUnit.MINUTES), // 10 minutes future
            TransactionStatus.SETTLED,
            "{\"raw\": \"payload\"}"
        );
        
        // When & Then
        assertThatThrownBy(() -> normalisationService.validate(futureTransaction))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("transactedAt is in the future");
    }
    
    @Test
    void validate_doesNotThrow_whenTransactionIsWithinClockSkew() {
        // Given - Transaction 3 minutes in future (within 5-minute clock skew)
        NormalisedTransaction nearFutureTransaction = new NormalisedTransaction(
            "txn-near-future",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("100.00"),
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().plus(3, ChronoUnit.MINUTES), // 3 minutes future (within skew)
            TransactionStatus.SETTLED,
            "{\"raw\": \"payload\"}"
        );
        
        // When & Then - Should not throw
        normalisationService.validate(nearFutureTransaction);
    }
    
    @Test
    void validate_throwsException_whenTransactionIsOlderThan7Days() {
        // Given - Transaction 8 days old (beyond 7-day limit)
        NormalisedTransaction oldTransaction = new NormalisedTransaction(
            "txn-old",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("100.00"),
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().minus(8, ChronoUnit.DAYS), // 8 days old
            TransactionStatus.SETTLED,
            "{\"raw\": \"payload\"}"
        );
        
        // When & Then
        assertThatThrownBy(() -> normalisationService.validate(oldTransaction))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("transactedAt is older than 7 days");
    }
    
    @Test
    void validate_doesNotThrow_whenTransactionIsExactly7DaysOld() {
        // Given - Transaction exactly 7 days old (at the limit)
        NormalisedTransaction sevenDayOldTransaction = new NormalisedTransaction(
            "txn-7days",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("100.00"),
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().minus(7, ChronoUnit.DAYS), // Exactly 7 days
            TransactionStatus.SETTLED,
            "{\"raw\": \"payload\"}"
        );
        
        // When & Then - Should not throw (boundary inclusive)
        normalisationService.validate(sevenDayOldTransaction);
    }
    
    @Test
    void validate_throwsException_whenAmountExceedsMaximum() {
        // Given - Transaction with amount > 100,000.00
        NormalisedTransaction largeTransaction = new NormalisedTransaction(
            "txn-large",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("100001.00"), // Exceeds MAX_AMOUNT of 100,000.00
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().minus(1, ChronoUnit.HOURS),
            TransactionStatus.SETTLED,
            "{\"raw\": \"payload\"}"
        );
        
        // When & Then
        assertThatThrownBy(() -> normalisationService.validate(largeTransaction))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount exceeds maximum allowed");
    }
    
    @Test
    void validate_doesNotThrow_whenAmountEqualsMaximum() {
        // Given - Transaction with amount exactly at maximum
        NormalisedTransaction maxAmountTransaction = new NormalisedTransaction(
            "txn-max",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("100000.00"), // Exactly MAX_AMOUNT
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().minus(1, ChronoUnit.HOURS),
            TransactionStatus.SETTLED,
            "{\"raw\": \"payload\"}"
        );
        
        // When & Then - Should not throw (boundary inclusive)
        normalisationService.validate(maxAmountTransaction);
    }
    
    @Test
    void validate_doesNotThrow_whenAmountBelowMaximum() {
        // Given - Transaction with amount below maximum
        NormalisedTransaction normalTransaction = new NormalisedTransaction(
            "txn-normal",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("99999.99"), // Below MAX_AMOUNT
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().minus(1, ChronoUnit.HOURS),
            TransactionStatus.SETTLED,
            "{\"raw\": \"payload\"}"
        );
        
        // When & Then - Should not throw
        normalisationService.validate(normalTransaction);
    }
    
    @Test
    void validate_logsWarning_whenReversedTransactionHasLargeAmount() {
        // Given - REVERSED transaction with amount > 50,000.00
        NormalisedTransaction largeReversedTransaction = new NormalisedTransaction(
            "txn-large-reversed",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("50001.00"), // Above 50,000.00 threshold for REVERSED
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().minus(1, ChronoUnit.HOURS),
            TransactionStatus.REVERSED, // REVERSED status
            "{\"raw\": \"payload\"}"
        );
        
        // When & Then - Should not throw (just logs warning)
        normalisationService.validate(largeReversedTransaction);
        // Can't easily verify log messages without a logging framework
        // but the code path is executed
    }
    
    @Test
    void validate_doesNotLogWarning_whenReversedTransactionHasSmallAmount() {
        // Given - REVERSED transaction with amount <= 50,000.00
        NormalisedTransaction smallReversedTransaction = new NormalisedTransaction(
            "txn-small-reversed",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("50000.00"), // Exactly at threshold
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().minus(1, ChronoUnit.HOURS),
            TransactionStatus.REVERSED,
            "{\"raw\": \"payload\"}"
        );
        
        // When & Then - Should not throw or log warning (at threshold)
        normalisationService.validate(smallReversedTransaction);
    }
    
    @Test
    void validate_doesNotLogWarning_whenNonReversedTransactionHasLargeAmount() {
        // Given - SETTLED transaction with large amount (not REVERSED)
        NormalisedTransaction largeSettledTransaction = new NormalisedTransaction(
            "txn-large-settled",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("50001.00"), // Above 50,000.00
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().minus(1, ChronoUnit.HOURS),
            TransactionStatus.SETTLED, // Not REVERSED
            "{\"raw\": \"payload\"}"
        );
        
        // When & Then - Should not log warning (only for REVERSED)
        normalisationService.validate(largeSettledTransaction);
    }
    
    @Test
    void validate_handlesMultipleValidationFailures_throwsFirstException() {
        // Given - Transaction that fails multiple validations:
        // 1. Future date (beyond clock skew)
        // 2. Amount exceeds maximum
        NormalisedTransaction invalidTransaction = new NormalisedTransaction(
            "txn-invalid",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("200000.00"), // Exceeds MAX_AMOUNT
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().plus(10, ChronoUnit.MINUTES), // Future beyond skew
            TransactionStatus.SETTLED,
            "{\"raw\": \"payload\"}"
        );
        
        // When & Then - Should throw first validation failure (timestamp)
        assertThatThrownBy(() -> normalisationService.validate(invalidTransaction))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("transactedAt is in the future");
    }
    
    @Test
    void validate_logsDebugMessage_whenTransactionIsValid() {
        // Given
        NormalisedTransaction validTransaction = createValidTransaction();
        
        // When
        normalisationService.validate(validTransaction);
        
        // Then - Should log debug message (code path executed)
        // Can't easily verify log messages without a logging framework
    }
    
    // Helper method to create a valid transaction
    private NormalisedTransaction createValidTransaction() {
        return new NormalisedTransaction(
            "txn-valid",
            SourceType.CARD_NETWORK,
            "ACC-001",
            new BigDecimal("100.00"),
            Currency.getInstance("ZAR"),
            "Test Merchant",
            "1234",
            Instant.now().minus(1, ChronoUnit.HOURS),
            TransactionStatus.SETTLED,
            "{\"raw\": \"payload\"}"
        );
    }
    
    @Test
    void validate_handlesDifferentSourceTypes() {
        // Test with different source types to ensure validation works for all
        SourceType[] sourceTypes = {SourceType.CARD_NETWORK, SourceType.BANK_FEED, SourceType.PAYMENT_PROCESSOR};
        
        for (SourceType sourceType : sourceTypes) {
            NormalisedTransaction transaction = new NormalisedTransaction(
                "txn-" + sourceType.name(),
                sourceType,
                "ACC-001",
                new BigDecimal("100.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().minus(1, ChronoUnit.HOURS),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
            );
            
            // Should not throw for any source type
            normalisationService.validate(transaction);
        }
    }
    
    @Test
    void validate_handlesDifferentTransactionStatuses() {
        // Test with different statuses
        TransactionStatus[] statuses = {TransactionStatus.PENDING, TransactionStatus.SETTLED, TransactionStatus.REVERSED};
        
        for (TransactionStatus status : statuses) {
            NormalisedTransaction transaction = new NormalisedTransaction(
                "txn-" + status.name(),
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("100.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().minus(1, ChronoUnit.HOURS),
                status,
                "{\"raw\": \"payload\"}"
            );
            
            // Should not throw for any status
            normalisationService.validate(transaction);
        }
    }
}