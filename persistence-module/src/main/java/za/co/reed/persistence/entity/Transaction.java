package za.co.reed.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "transactions",
        // Indexes are already part of the migration script, just adding it here for developers to see which fields are indexed
        indexes = {
                @Index(name = "idx_transactions_account_id", columnList = "account_id"),
                @Index(name = "idx_transactions_transacted_at", columnList = "transacted_at DESC"),
                @Index(name = "idx_transactions_status", columnList = "status"),
                @Index(name = "idx_transactions_category_id", columnList = "category_id"),
                @Index(name = "idx_transactions_external_id", columnList = "external_id"),
                @Index(name = "idx_tx_raw_payload", columnList = "raw_payload"),
                @Index(name = "idx_tx_account_date", columnList = "account_id, transacted_at DESC")
        }
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "external_id", updatable = false, nullable = false, unique = true)
    private UUID externalId;

    @Column(name = "source_id", nullable = false, unique = true, length = 255)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType;

    @Column(name = "account_id", nullable = false, length = 128)
    private String accountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "merchant_name", nullable = false, length = 255)
    private String merchantName;

    @Column(name = "merchant_mcc", length = 4)
    private String merchantMcc;

    @Column(name = "transacted_at", nullable = false)
    private Instant transactedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransactionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onPersist() {
        externalId = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Transaction(NormalisedTransaction source) {
        this.sourceId = source.sourceId();
        this.sourceType = source.sourceType();
        this.accountId = source.accountId();
        this.amount = source.amount();
        this.currency = source.currency().getCurrencyCode();
        this.merchantName = source.merchantName();
        this.merchantMcc = source.merchantMcc();
        this.transactedAt = source.transactedAt();
        this.status = source.status();
        this.rawPayload = source.rawPayload();
    }
}
