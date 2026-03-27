package za.co.reed.apiservice.dto.response;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AggregationResponse {
    @Schema(description = "Category code for this getKey bucket")
    private String categoryCode;

    @Schema(description = "Category label for this getKey bucket")
    private String categoryLabel;

    @Schema(description = "Merchant name for this getKey bucket")
    private String merchantName;

    @Schema(description = "Merchant category code for this getKey bucket")
    private String merchantMcc;

    @Schema(description = "Date representing the time bucket (e.g. 2026-03-01 for daily granularity, or 2026-03 for monthly granularity)" , example = "2026-03-01")
    private Date period;

    @Schema(description = "Total spend across all transactions for this account")
    private BigDecimal totalSpend;

    @Schema(description = "Total spend across all transactions for this account")
    private Long transactionCount;

    @Schema(description = "Total spend across all transactions for this account")
    private BigDecimal totalReversed;
}
