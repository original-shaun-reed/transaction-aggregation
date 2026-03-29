package za.co.reed.apiservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response object representing aggregated transaction data grouped by category, merchant, and time period")
public class AggregationResponse {

    @Schema(description = "Category code for this aggregation bucket", example = "groceries")
    private String categoryCode;

    @Schema(description = "Human-readable category label for this aggregation bucket", example = "Groceries")
    private String categoryLabel;

    @Schema(description = "Merchant name for this aggregation bucket", example = "Pick n Pay")
    private String merchantName;

    @Schema(description = "ISO 18245 Merchant Category Code for this aggregation bucket", example = "5411")
    private String merchantMcc;

    @Schema(description = "Date representing the time bucket (e.g., daily or monthly granularity)", example = "2026-03-01")
    private Date period;

    @Schema(description = "Total spend across all transactions in this bucket", example = "1250.75")
    private BigDecimal totalSpend;

    @Schema(description = "Number of transactions included in this bucket", example = "42")
    private Long transactionCount;

    @Schema(description = "Total amount reversed (refunds, chargebacks) in this bucket", example = "150.00")
    private BigDecimal totalReversed;
}
