package za.co.reed.apiservice.dto.response.compare;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated totals for one period")
public class PeriodSummary {

    @Schema(description = "Start of the period (inclusive)", example = "2026-03-01")
    private Date from;

    @Schema(description = "End of the period (inclusive)", example = "2026-03-31")
    private Date to;

    @Schema(description = "Total amount spent in the period", example = "1234.56")
    private BigDecimal totalSpend;

    @Schema(description = "Number of transactions included in the total spent", example = "42")
    private Long transactionCount;
}
