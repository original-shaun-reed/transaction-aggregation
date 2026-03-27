package za.co.reed.apiservice.service;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import za.co.reed.apiservice.exception.ApiInternalServerErrorException;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.apiservice.dto.response.DataResponse;
import za.co.reed.apiservice.dto.response.TransactionResponse;
import za.co.reed.apiservice.exception.ApiNotFoundException;
import za.co.reed.apiservice.specification.TransactionSpecification;
import za.co.reed.persistence.entity.Transaction;
import za.co.reed.persistence.repository.TransactionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    public final TransactionRepository transactionRepository;

    public ResponseEntity<DataResponse<TransactionResponse>> list(String accountId, TransactionStatus status, Date from,
                                                                  Date to, int page, int pageSize, String order, String sort) {
        try {
            Specification<Transaction> specification = transactionAccountSpecification(accountId, status, from, to);
            DataResponse<TransactionResponse> response = getDataResponse(specification, page, pageSize, order, sort);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error listing transactions: accountId={} status={} from={} to={} pageSize={}", accountId, status, from, to, pageSize);
            throw new ApiInternalServerErrorException("Error listing transactions");
        }
    }

    public ResponseEntity<TransactionResponse> getByExternalId(UUID externalId) {
        Transaction transaction = transactionRepository.findByExternalId(externalId);
        if (transaction == null) {
            throw new ApiNotFoundException("Transaction not found: " + externalId.toString());
        }

        return ResponseEntity.ok(buildTransactionResponse(transaction));
    }

    public ResponseEntity<DataResponse<TransactionResponse>> merchantSearch(String merchantName, Date from, Date to,
                                                                            int page, int pageSize, String order, String sort) {
        try {
            Specification<Transaction> specification = TransactionSpecification.merchantName(merchantName);

            if (Objects.nonNull(from) && Objects.nonNull(to)) {
                Specification<Transaction> dateRangeSpecification = TransactionSpecification.createdAtBetween(from, to);
                specification = specification.and(dateRangeSpecification);
            } else if (Objects.nonNull(from) || Objects.nonNull(to)) {
                if (Objects.nonNull(from)) {
                    Specification<Transaction> fromSpecification = TransactionSpecification.createdAtGreaterThanOrEqualTo(from);
                    specification = specification.and(fromSpecification);
                } else if (Objects.nonNull(to)) {
                    Specification<Transaction> toSpecification = TransactionSpecification.createdAtLessThanOrEqualTo(to);
                    specification = specification.and(toSpecification);
                }
            }

            DataResponse<TransactionResponse> response = getDataResponse(specification, page, pageSize, order, sort);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error searching transactions by merchant: merchantName={} from={} to={} pageSize={}", merchantName,
                    from, to, pageSize);
            throw new ApiInternalServerErrorException("Error searching transactions by merchant");
        }
    }

    public List<Transaction> getTransactions(String accountId, TransactionStatus status, Date from, Date to, int limit) {
        Specification<Transaction> specification = transactionAccountSpecification(accountId, status, from, to);
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.Direction.DESC, "amount");

        Page<Transaction> transactions = transactionRepository.findAll(specification, pageRequest);
        if (!transactions.hasContent()) {
            return List.of();
        }

        return transactions.getContent();
    }

    private Specification<Transaction> transactionAccountSpecification(String accountId, TransactionStatus status,
                                                                       Date from, Date to) {
        Specification<Transaction> specification = null;

        if (accountId != null) {
            specification = TransactionSpecification.accountId(accountId);
        }

        if (status != null) {
            Specification<Transaction> statusSpecification = TransactionSpecification.status(status);
            specification = specification == null ? statusSpecification : specification.and(statusSpecification);
        }

        if (Objects.nonNull(from) && Objects.nonNull(to)) {
            Specification<Transaction> dateRangeSpecification = TransactionSpecification.createdAtBetween(from, to);
            specification = specification == null ? dateRangeSpecification : specification.and(dateRangeSpecification);
        } else if (Objects.nonNull(from) || Objects.nonNull(to)) {
            if (Objects.nonNull(from)) {
                Specification<Transaction> fromSpecification = TransactionSpecification
                        .createdAtGreaterThanOrEqualTo(from);
                specification = specification == null ? fromSpecification : specification.and(fromSpecification);
            } else if (Objects.nonNull(to)) {
                Specification<Transaction> toSpecification = TransactionSpecification
                        .createdAtLessThanOrEqualTo(to);
                specification = specification == null ? toSpecification : specification.and(toSpecification);
            }
        }

        return specification;
    }

    private DataResponse<TransactionResponse> getDataResponse(Specification<Transaction> specification, int page,
                                                              int pageSize, String order, String sort) {
        PageRequest pageRequest = PageRequest.of(page, pageSize, Sort.Direction.fromString(StringUtils.toRootLowerCase(sort)), order);

        Page<Transaction> transactions = transactionRepository.findAll(specification, pageRequest);
        if (!transactions.hasContent()) {
            return new DataResponse<>();
        }

        List<TransactionResponse> responseList = transactions.getContent().stream()
                .filter(Objects::nonNull)
                .map(this::buildTransactionResponse)
                .toList();

        DataResponse response = DataResponse.<TransactionResponse>builder()
                .data(responseList)
                .totalCount(transactions.getTotalElements())
                .totalPages(transactions.getTotalPages())
                .page(transactions.getPageable().getPageNumber())
                .pageSize(transactions.getNumberOfElements())
                .hasMore(transactions.hasNext())
                .build();

        return response;
    }

    private TransactionResponse buildTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getExternalId())
                .sourceId(transaction.getSourceId())
                .sourceType(transaction.getSourceType())
                .accountId(transaction.getAccountId())
                .amount(transaction.getAmount())
                .categoryId(transaction.getCategory().getExternalId())
                .currency(transaction.getCurrency())
                .merchantName(transaction.getMerchantName())
                .merchantMcc(transaction.getMerchantMcc())
                .transactedAt(transaction.getTransactedAt())
                .status(transaction.getStatus())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private String escape(String value) {
        if (value == null) {
            return StringUtils.EMPTY;
        }

        // Escape quotes within quoted fields
        return value.replace("\"", "\"\"");
    }
}
