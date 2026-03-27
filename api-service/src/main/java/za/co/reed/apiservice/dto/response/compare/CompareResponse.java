package za.co.reed.apiservice.dto.response.compare;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

@Schema(description = "Period-over-period comparison result")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompareResponse(

        PeriodSummary current,
        PeriodSummary previous,
        Delta delta,
        List<CategoryBreakdown> byCategory,
        List<TimeSeriesPoint> timeSeries,
        List<Anomaly> anomalies,
        Meta meta

) {
    @Schema(description = "Aggregated totals for one period")
    public record PeriodSummary(
            LocalDate from,
            LocalDate to,
            BigDecimal totalSpend,
            int transactionCount
    ) {}

    @Schema(description = "Differences between current and previous period")
    public record Delta(
            BigDecimal spendAbsolute,
            BigDecimal spendPct,
            int countAbsolute,
            String direction   // INCREASE | DECREASE | FLAT
    ) {}

    @Schema(description = "Per-category breakdown of spend in each period")
    public record CategoryBreakdown(
            String categoryId,
            String label,
            BigDecimal currentSpend,
            BigDecimal previousSpend,
            BigDecimal deltaPct,
            BigDecimal shareOfTotalCurrent
    ) {}

    @Schema(description = "Spend for both periods at a given time bucket")
    public record TimeSeriesPoint(
            LocalDate period,
            BigDecimal current,
            BigDecimal previous
    ) {}

    @Schema(description = "Statistical anomaly detected in a category")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Anomaly(
            String categoryId,
            String reason,      // SPIKE | DROP
            BigDecimal zScore,
            String description
    ) {}

    @Schema(description = "Response metadata")
    public record Meta(
            boolean cached,
            int cacheTtlSeconds,
            Instant generatedAt
    ) {}
}
