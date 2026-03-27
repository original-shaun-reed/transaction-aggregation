package za.co.reed.apiservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single financial transaction")
public class TransactionResponse {

        @Schema(description = "ID of the transaction")
        private UUID id;

        @Schema(description = "Original ID from the source system")
        private String sourceId;

        @Schema(description = "Which data source produced this transaction")
        private SourceType sourceType;

        private String accountId;
        private BigDecimal amount;
        private String currency;
        private String merchantName;

        @Schema(description = "ISO 18245 Merchant Category Code — null for bank feed / payment processor")
        private String merchantMcc;

        private Instant transactedAt;

        private TransactionStatus status;

        private UUID categoryId;

        @Schema(description = "Assigned category code — e.g. 'groceries', 'restaurants'")
        private String categoryCode;

        @Schema(description = "Human-readable category label")
        private String categoryLabel;

        private Instant createdAt;
}
