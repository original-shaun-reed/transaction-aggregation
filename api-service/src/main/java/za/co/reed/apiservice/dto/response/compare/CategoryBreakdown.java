package za.co.reed.apiservice.dto.response.compare;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Per-category breakdown of spend in each period, including current spend, previous spend, and relative share of total")
public class CategoryBreakdown {

    @Schema(description = "Unique identifier of the category", example = "groceries")
    private String categoryId;

    @Schema(description = "Human-readable label for the category", example = "Groceries")
    private String label;

    @Schema(description = "Total spend in the current period for this category", example = "1250.75")
    private BigDecimal currentSpend;

    @Schema(description = "Total spend in the previous period for this category", example = "1100.00")
    private BigDecimal previousSpend;

    @Schema(description = "Percentage change in spend compared to the previous period", example = "13.64")
    private BigDecimal deltaPct;

    @Schema(description = "Share of total spend represented by this category in the current period", example = "0.25")
    private BigDecimal shareOfTotalCurrent;
}