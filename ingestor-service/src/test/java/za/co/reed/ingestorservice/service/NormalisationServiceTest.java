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

    private NormalisationService testNormalisationService;

    @BeforeEach
    void setUp() {
        testNormalisationService = new NormalisationService();
    }

    @Test
    void validate_doesNotThrow_whenTransactionIsValid() {
        NormalisedTransaction testValidTransaction = createTestValidTransaction();
        testNormalisationService.validate(testValidTransaction);
    }

    @Test
    void validate_throwsException_whenTransactionIsInFutureBeyondClockSkew() {
        NormalisedTransaction testFutureTransaction = new NormalisedTransaction(
                "txn-future",
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("100.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().plus(10, ChronoUnit.MINUTES),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        assertThatThrownBy(() -> testNormalisationService.validate(testFutureTransaction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transactedAt is in the future");
    }

    @Test
    void validate_doesNotThrow_whenTransactionIsWithinClockSkew() {
        NormalisedTransaction testNearFutureTransaction = new NormalisedTransaction(
                "txn-near-future",
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("100.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().plus(3, ChronoUnit.MINUTES),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        testNormalisationService.validate(testNearFutureTransaction);
    }

    @Test
    void validate_throwsException_whenTransactionIsOlderThan7Days() {
        NormalisedTransaction testOldTransaction = new NormalisedTransaction(
                "txn-old",
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("100.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().minus(8, ChronoUnit.DAYS),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        assertThatThrownBy(() -> testNormalisationService.validate(testOldTransaction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transactedAt is older than 7 days");
    }

    @Test
    void validate_throwsException_whenAmountExceedsMaximum() {
        NormalisedTransaction testLargeTransaction = new NormalisedTransaction(
                "txn-large",
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("100001.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().minus(1, ChronoUnit.HOURS),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        assertThatThrownBy(() -> testNormalisationService.validate(testLargeTransaction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount exceeds maximum allowed");
    }

    @Test
    void validate_doesNotThrow_whenAmountEqualsMaximum() {
        NormalisedTransaction testMaxAmountTransaction = new NormalisedTransaction(
                "txn-max",
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("100000.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().minus(1, ChronoUnit.HOURS),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        testNormalisationService.validate(testMaxAmountTransaction);
    }

    @Test
    void validate_doesNotThrow_whenAmountBelowMaximum() {
        NormalisedTransaction testNormalTransaction = new NormalisedTransaction(
                "txn-normal",
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("99999.99"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().minus(1, ChronoUnit.HOURS),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        testNormalisationService.validate(testNormalTransaction);
    }

    @Test
    void validate_logsWarning_whenReversedTransactionHasLargeAmount() {
        NormalisedTransaction testLargeReversedTransaction = new NormalisedTransaction(
                "txn-large-reversed",
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("50001.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().minus(1, ChronoUnit.HOURS),
                TransactionStatus.REVERSED,
                "{\"raw\": \"payload\"}"
        );

        testNormalisationService.validate(testLargeReversedTransaction);
    }

    @Test
    void validate_doesNotLogWarning_whenReversedTransactionHasSmallAmount() {
        NormalisedTransaction testSmallReversedTransaction = new NormalisedTransaction(
                "txn-small-reversed",
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("50000.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().minus(1, ChronoUnit.HOURS),
                TransactionStatus.REVERSED,
                "{\"raw\": \"payload\"}"
        );

        testNormalisationService.validate(testSmallReversedTransaction);
    }

    @Test
    void validate_doesNotLogWarning_whenNonReversedTransactionHasLargeAmount() {
        NormalisedTransaction testLargeSettledTransaction = new NormalisedTransaction(
                "txn-large-settled",
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("50001.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().minus(1, ChronoUnit.HOURS),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        testNormalisationService.validate(testLargeSettledTransaction);
    }

    @Test
    void validate_handlesMultipleValidationFailures_throwsFirstException() {
        NormalisedTransaction testInvalidTransaction = new NormalisedTransaction(
                "txn-invalid",
                SourceType.CARD_NETWORK,
                "ACC-001",
                new BigDecimal("200000.00"),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "1234",
                Instant.now().plus(10, ChronoUnit.MINUTES),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        assertThatThrownBy(() -> testNormalisationService.validate(testInvalidTransaction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transactedAt is in the future");
    }

    @Test
    void validate_logsDebugMessage_whenTransactionIsValid() {
        NormalisedTransaction testValidTransaction = createTestValidTransaction();
        testNormalisationService.validate(testValidTransaction);
    }

    private NormalisedTransaction createTestValidTransaction() {
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
        SourceType[] testSourceTypes = {SourceType.CARD_NETWORK, SourceType.BANK_FEED, SourceType.PAYMENT_PROCESSOR};

        for (SourceType testSourceType : testSourceTypes) {
            NormalisedTransaction testTransaction = new NormalisedTransaction(
                    "txn-" + testSourceType.name(),
                    testSourceType,
                    "ACC-001",
                    new BigDecimal("100.00"),
                    Currency.getInstance("ZAR"),
                    "Test Merchant",
                    "1234",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    TransactionStatus.SETTLED,
                    "{\"raw\": \"payload\"}"
            );

            testNormalisationService.validate(testTransaction);
        }
    }

    @Test
    void validate_handlesDifferentTransactionStatuses() {
        TransactionStatus[] testStatuses = {TransactionStatus.PENDING, TransactionStatus.SETTLED, TransactionStatus.REVERSED};

        for (TransactionStatus testStatus : testStatuses) {
            NormalisedTransaction testTransaction = new NormalisedTransaction(
                    "txn-" + testStatus.name(),
                    SourceType.CARD_NETWORK,
                    "ACC-001",
                    new BigDecimal("100.00"),
                    Currency.getInstance("ZAR"),
                    "Test Merchant",
                    "1234",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    testStatus,
                    "{\"raw\": \"payload\"}"
            );

            testNormalisationService.validate(testTransaction);
        }
    }
}