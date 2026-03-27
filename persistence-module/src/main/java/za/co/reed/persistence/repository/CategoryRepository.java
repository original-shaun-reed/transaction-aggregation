package za.co.reed.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import za.co.reed.persistence.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {
    Category findByExternalId(UUID externalId);
    Optional<Category> findByCode(String code);
    List<Category> findByMccCodesLike(String mcc);
    List<Category> findAllByKeywordsNotNull();
    List<Category> findAllByParentIsNull();

    /** Fallback "Uncategorised" category — always present from seed data. */
    default Category uncategorised() {
        return findByCode("uncategorised")
                .orElseThrow(() -> new IllegalStateException("Seed category 'uncategorised' not found — check V2 migration"));
    }
}