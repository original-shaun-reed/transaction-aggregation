package za.co.reed.apiservice.specification;

import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.persistence.entity.Transaction;

public class TransactionSpecification {
    public static Specification<Transaction> accountId(String accountId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("accountId"), accountId);
    }

    public static Specification<Transaction> status(TransactionStatus status) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Transaction> createdAtBetween(Date from, Date to) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.between(root.get("createdAt"), from, to);
    }

    public static Specification<Transaction> createdAtGreaterThanOrEqualTo(Date transactionAt) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), transactionAt);
    }

    public static Specification<Transaction> createdAtLessThanOrEqualTo(Date transactionAt) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), transactionAt);
    }

    public static Specification<Transaction> merchantName(String merchantName) {
        return merchantNameEquals(merchantName)
                .or(merchantNameStartsWith(merchantName))
                .or(merchantNameContains(merchantName))
                .or(merchantNameEndsWith(merchantName));
    }

    private static Specification<Transaction> merchantNameEquals(String merchantName) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(criteriaBuilder.lower(root.get("merchantName")), StringUtils.toRootLowerCase(merchantName));
    }

    private static Specification<Transaction> merchantNameStartsWith(String merchantName) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("merchantName")), StringUtils.toRootLowerCase(merchantName) + "%");
    }

    private static Specification<Transaction> merchantNameContains(String merchantName) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("merchantName")), "%" + StringUtils.toRootLowerCase(merchantName) + "%");
    }

    private static Specification<Transaction> merchantNameEndsWith(String merchantName) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("merchantName")), "%" + StringUtils.toRootLowerCase(merchantName));
    }
}
