package za.co.reed.apiservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.persistence.entity.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single financial transaction")
public class TransactionResponse {

    @Schema(description = "Unique identifier of the transaction", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Original identifier from the source system", example = "TXN123456789")
    private String sourceId;

    @Schema(description = "System or integration that produced this transaction", example = "BANK_FEED")
    private SourceType sourceType;

    @Schema(description = "Identifier of the account associated with this transaction", example = "ACC987654321")
    private String accountId;

    @Schema(description = "Monetary amount of the transaction", example = "1250.75")
    private BigDecimal amount;

    @Schema(description = "Currency code in ISO 4217 format", example = "ZAR")
    private String currency;

    @Schema(description = "Name of the merchant or payee", example = "Pick n Pay")
    private String merchantName;

    @Schema(description = "ISO 18245 Merchant Category Code — null if transaction originates from a bank feed or payment processor", example = "5411")
    private String merchantMcc;

    @Schema(description = "Timestamp when the transaction occurred", example = "2026-03-27T14:35:00Z")
    private Instant transactedAt;

    @Schema(description = "Current processing status of the transaction", example = "SETTLED")
    private TransactionStatus status;

    @Schema(description = "Unique identifier of the assigned category", example = "c7a1f2d4-1234-5678-9abc-def012345678")
    private UUID categoryId;

    @Schema(description = "Assigned category code for classification", example = "groceries")
    private String categoryCode;

    @Schema(description = "Human-readable label for the category", example = "Groceries")
    private String categoryLabel;

    @Schema(description = "Timestamp when the transaction record was created in the system", example = "2026-03-27T15:00:00Z")
    private Instant createdAt;

    public static TransactionResponse of(Transaction transaction) {
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
}
