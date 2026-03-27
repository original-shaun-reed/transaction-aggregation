package za.co.reed.apiservice.specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.commom.enums.PeriodType;
import za.co.reed.persistence.entity.Aggregation;
import za.co.reed.persistence.entity.Category;

import java.util.Date;

public class AggregationSpecification {

    public static Specification<Aggregation> periodType(PeriodType periodType) {
        return (root, query, cb) -> cb.equal(root.get("periodType"), periodType);
    }

    public Specification<Aggregation> transactionStatus(TransactionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Aggregation> periodDateBetween(Date from, Date to) {
        return periodDateGreaterThanOrEqualTo(from).and(periodDateLessThanOrEqualTo(to));
    }

    public static Specification<Aggregation> periodDateGreaterThanOrEqualTo(Date from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("periodDate"), from);
    }

    public static Specification<Aggregation> periodDateLessThanOrEqualTo(Date to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("periodDate"), to);
    }

    public static Specification<Aggregation> accountExternalId(String accountId) {
        return accountIdEquals(accountId).or(accountIsNull());
    }

    private static Join<Aggregation, Category> joinCategory(Root<Aggregation> root) {
        return root.join("category");
    }

    public static Specification<Aggregation> accountIdEquals(String accountId) {
        return (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
    }

    private static Specification<Aggregation> accountIsNull() {
        return (root, query, cb) -> cb.isNull(root.get("accountId"));
    }
}