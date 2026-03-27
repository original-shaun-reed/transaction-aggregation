package za.co.reed.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.reed.commom.enums.PeriodType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "aggregations",
        // Indexes are already part of the migration script, just adding it here for developers to see which fields are indexed
        indexes = {
                @Index(name = "idx_aggregations_account_period", columnList = "account_id, period_date DESC"),
                @Index(name = "idx_aggregations_category_period", columnList = "category_id, period_date DESC"),
                @Index(name = "idx_aggregations_period_type_date", columnList = "period_type, period_date DESC"),
                @Index(name = "idx_aggregations_account_type_period", columnList = "account_id, period_type, period_date DESC"),
                @Index(name = "idx_aggregations_account_category_type", columnList = "account_id, category_id, period_type")
        }
)
public class Aggregation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "account_id", nullable = false, length = 128)
    private String accountId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 8)
    private PeriodType periodType;

    /** Total spend for SETTLED transactions in this period/category/account. */
    @Column(name = "total_spend", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalSpend = BigDecimal.ZERO;

    /** Count of SETTLED transactions contributing to total_spend. */
    @Column(name = "transaction_count", nullable = false)
    @Builder.Default
    private Long transactionCount = 0L;

    /** Total value of REVERSED transactions — tracked separately. */
    @Column(name = "total_reversed", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalReversed = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = Instant.now();
    }
}