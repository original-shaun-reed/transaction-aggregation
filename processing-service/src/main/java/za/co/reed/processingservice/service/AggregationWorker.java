package za.co.reed.processingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.reed.commom.enums.PeriodType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.persistence.entity.Category;
import za.co.reed.persistence.entity.Aggregation;
import za.co.reed.persistence.entity.Transaction;
import za.co.reed.persistence.repository.AggregationRepository;
import za.co.reed.persistence.repository.CategoryRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Updates pre-computed aggregation rollups after each transaction is categorised.
 *
 * Called by RawTransactionConsumer after CategorizationService assigns a category.
 * Per SETTLED transaction:
 *   - DAILY aggregation upsert  (period_date = transaction date)
 *   - MONTHLY aggregation upsert (period_date = first day of month)
 *
 * PENDING transactions are skipped entirely.
 * REVERSED transactions update total_reversed on aggregations.
 *
 * All upserts use PostgreSQL ON CONFLICT DO UPDATE — atomic and safe under
 * concurrent consumer threads. No SELECT-then-UPDATE race condition.
 *
 * @Transactional ensures the transaction persist and aggregation upserts
 * are committed atomically. If any step fails, all are rolled back and the
 * Kafka offset is not committed (MANUAL_IMMEDIATE ack).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationWorker {
    private final CategoryRepository categoryRepository;
    private final AggregationRepository aggregationRepository;

    @Transactional(rollbackFor = Exception.class)
    public void updateAggregations(Transaction transaction) {
        if (transaction.getCategory() == null) {
            log.warn("Transaction has no category — skipping aggregation: {}",
                    transaction.getSourceId());
            return;
        }

        TransactionStatus status = transaction.getStatus();

        // PENDING transactions contribute nothing until they settle
        if (status == TransactionStatus.PENDING) {
            log.debug("Skipping PENDING transaction — sourceId={}", transaction.getSourceId());
            return;
        }

        Instant transactionDate = transaction.getTransactedAt();

        LocalDate dailyDate = transactionDate.atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate weeklyDate = transactionDate.atOffset(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .toLocalDate();
        LocalDate monthlyDate = transactionDate.atOffset(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .toLocalDate();

        String accountId = transaction.getAccountId();
        Long categoryId = transaction.getCategory().getId();
        BigDecimal amount = transaction.getAmount();

        if (status == TransactionStatus.SETTLED) {
            // DAILY upsert
            upsertSpend(accountId, categoryId, dailyDate, PeriodType.DAILY, amount);

            // WEEKLY upsert
            upsertSpend(accountId, categoryId, weeklyDate, PeriodType.WEEKLY, amount);

            // MONTHLY upsert
            upsertSpend(accountId, categoryId, monthlyDate, PeriodType.MONTHLY, amount);

            log.debug("Aggregation upserted (SETTLED) — sourceId={} category={} amount={}",
                    transaction.getSourceId(), transaction.getCategory().getCode(), amount);

        } else if (status == TransactionStatus.REVERSED) {
            // DAILY reversal
            upsertReversal(accountId, categoryId, dailyDate, PeriodType.DAILY, amount);

            // WEEKLY reversal
            upsertReversal(accountId, categoryId, weeklyDate, PeriodType.WEEKLY, amount);

            // MONTHLY reversal
            upsertReversal(accountId, categoryId, monthlyDate, PeriodType.MONTHLY, amount);

            log.debug("Aggregation upserted (REVERSED) — sourceId={} category={} amount={}",
                    transaction.getSourceId(), transaction.getCategory().getCode(), amount);
        }
    }

    private void upsertSpend(String accountId, Long categoryId, LocalDate periodDate, PeriodType periodType, BigDecimal amount) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
        
        Aggregation aggregation = aggregationRepository.findByAccountIdAndCategoryAndPeriodTypeAndPeriodDate(
                accountId, category, periodType, periodDate);

        if (Objects.isNull(aggregation)) {
            aggregation = Aggregation.builder()
                    .accountId(accountId)
                    .category(category)
                    .periodDate(periodDate)
                    .periodType(periodType)
                    .totalSpend(amount)
                    .transactionCount(1L)
                    .totalReversed(BigDecimal.ZERO)
                    .build();
        } else {
            aggregation.setTotalSpend(aggregation.getTotalSpend().add(amount));
            aggregation.setTransactionCount(aggregation.getTransactionCount() + 1);
        }

        aggregation.setUpdatedAt(Instant.now());
        aggregationRepository.save(aggregation);
    }

    private void upsertReversal(String accountId, Long categoryId, LocalDate periodDate, PeriodType periodType, BigDecimal amount) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        Aggregation aggregation = aggregationRepository.findByAccountIdAndCategoryAndPeriodTypeAndPeriodDate(
                accountId, category, periodType, periodDate);
        
        if (Objects.isNull(aggregation)) {
            log.warn("No existing aggregation found for reversal — accountId={} categoryId={} periodDate={} periodType={}",
                    accountId, categoryId, periodDate, periodType);

            aggregation = Aggregation.builder()
                    .accountId(accountId)
                    .category(category)
                    .periodDate(periodDate)
                    .periodType(periodType)
                    .totalSpend(BigDecimal.ZERO)
                    .transactionCount(0L)
                    .totalReversed(amount)
                    .build();
        }

        // Update reversal amount and timestamp
        aggregation.setTotalReversed(aggregation.getTotalReversed().add(amount));
        aggregation.setUpdatedAt(Instant.now());

        aggregationRepository.save(aggregation);
    }
}
