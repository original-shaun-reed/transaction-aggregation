package za.co.reed.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "categories",
        // Indexes are already part of the migration script, just adding it here for developers to see which fields are indexed
        indexes = {
                @Index(name = "idx_categories_parent_id", columnList = "parent_id"),
                @Index(name = "idx_categories_label", columnList = "label"),
                @Index(name = "idx_categories_path", columnList = "path"),
                @Index(name = "idx_categories_mcc", columnList = "mcc_codes")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_categories_external_id", columnNames = "external_id"),
                @UniqueConstraint(name = "uq_categories_code", columnNames = "code")
        }
)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "external_id", updatable = false, nullable = false, unique = true)
    @Builder.Default
    private UUID externalId = UUID.randomUUID();

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "path", nullable = false, length = 1024)
    private String path;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    /** ISO 18245 MCC codes that map to this category, comma-separated. */
    @Column(name = "mcc_codes", length = 1024)
    private String mccCodes;

    /** Keywords used by RulesEngine for merchant name matching. */
    @Column(name = "keywords", length = 1024)
    private String keywords;

    @PrePersist
    void onPersist() {
        externalId = UUID.randomUUID();
    }
}
