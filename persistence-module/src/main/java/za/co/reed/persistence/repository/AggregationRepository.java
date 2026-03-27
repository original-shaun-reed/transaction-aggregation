package za.co.reed.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import za.co.reed.commom.enums.PeriodType;
import za.co.reed.persistence.entity.Aggregation;
import za.co.reed.persistence.entity.Category;
import java.time.LocalDate;

@Repository
public interface AggregationRepository extends JpaRepository<Aggregation, Long>, JpaSpecificationExecutor<Aggregation> {
    Aggregation findByAccountIdAndCategoryAndPeriodTypeAndPeriodDate(String accountId, Category category, PeriodType periodType, LocalDate periodDate);
}