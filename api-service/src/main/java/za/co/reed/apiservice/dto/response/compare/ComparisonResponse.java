package za.co.reed.apiservice.dto.response.compare;


import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Period-over-period comparison result")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComparisonResponse {
    @Schema(description = "Summary of the current period")
    private PeriodSummary current;

    @Schema(description = "Summary of the previous period")
    private PeriodSummary previous;

    @Schema(description = "Delta between the two periods (absolute and percentage change)")
    private Delta delta;

    @Schema(description = "Breakdown of spend by category for the current period")
    private List<CategoryBreakdown> byCategory;

    @Schema(description = "Time series of spend for the current period, broken down by the requested granularity (DAY or MONTH)")
    private List<TimeSeriesPoint> timeSeries;

    @Schema(description = "List of detected anomalies in the category breakdown")
    private List<Anomaly> anomalies;

    @Schema(description = "Metadata about the comparison, such as the parameters used and cache status")
    private Meta meta;
}

