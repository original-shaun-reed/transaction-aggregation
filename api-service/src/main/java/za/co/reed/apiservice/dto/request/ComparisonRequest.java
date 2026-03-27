package za.co.reed.apiservice.dto.request;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import za.co.reed.commom.enums.PeriodType;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Parameters for the period-over-period comparison endpoint")
public class ComparisonRequest implements Serializable {

    @Valid
    @NotNull(message = "Current from date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Start of the current period (inclusive)", example = "2026-03-01")
    Date currentFrom;

    @Valid
    @NotNull(message = "Current to date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "End of the current period (inclusive)", example = "2026-03-31")
    Date currentTo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Start of the comparison period. Defaults to same-length window shifted back.")
    Date previousFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "End of the comparison period.")
    Date previousTo;

    @Schema(description = "Scope to a specific account ID. Omit for all accounts.")
    String accountId;

    @Schema(description = "Predefined period type for quick comparisons. Overrides explicit date params if provided.", allowableValues = {"DAILY", "MONTHLY", "WEEKLY"})
    PeriodType periodType;

    @Schema(description = "Filter to specific category codes, comma-separated.")
    List<String> categoryIds;

    @Schema(description = "Time breakdown granularity: DAY or MONTH", allowableValues = {"DAY", "MONTH"})
    String granularity;

    @Schema(description = "Include statistical anomaly detection in the response")
    boolean includeAnomalies;


    /**
     * Derive the previous period if not explicitly provided.
     * Default: same-length window immediately before the current period.
     */
    public Date resolvedPreviousFrom() {
        if (Objects.nonNull(previousFrom)) {
            return previousFrom;
        }

        LocalDate currentTo = LocalDate.ofInstant(this.currentTo.toInstant(), java.time.ZoneId.systemDefault());
        LocalDate currentFrom = LocalDate.ofInstant(this.currentFrom.toInstant(), java.time.ZoneId.systemDefault());

        long daysBetween = currentTo.toEpochDay() - currentFrom.toEpochDay() + 1;
        return Date.from(currentFrom.minusDays(daysBetween).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public Date resolvedPreviousTo() {
        if (Objects.nonNull(previousTo)) {
            return previousTo;
        }

        LocalDate currentFrom = LocalDate.ofInstant(this.currentFrom.toInstant(), java.time.ZoneId.systemDefault());
        return Date.from(currentFrom.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public String resolvedGranularity() {
        return granularity != null ? granularity.toUpperCase() : "MONTH";
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentFrom, currentTo, previousFrom, previousTo, accountId, categoryIds, resolvedGranularity(), includeAnomalies);
    }
}
