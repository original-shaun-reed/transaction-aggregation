package za.co.reed.apiservice.dto.response.compare;

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
@Schema(description = "Spend for both periods at a given time bucket")
public class TimeSeriesPoint {
    @Schema(description = "Date representing the time bucket (e.g. 2026-03-01 for daily granularity, or 2026-03 for monthly granularity)" , example = "2026-03-01")
    private Date period;

    @Schema(description = "Total spend for the current period in this time bucket", example = "100.00")
    private BigDecimal current;

    @Schema(description = "Total spend for the previous period in this time bucket", example = "80.00")
    private BigDecimal previous;
}