package za.co.reed.apiservice.dto.response.compare;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.reed.apiservice.enums.Direction;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Differences between current and previous period, expressed in absolute values, percentages, and directional change")
public class Delta {

    @Schema(description = "Absolute difference in spend between current and previous period", example = "150.75")
    private BigDecimal absoluteSpend;

    @Schema(description = "Percentage difference in spend compared to the previous period", example = "12.5")
    private BigDecimal spendPercentage;

    @Schema(description = "Absolute difference in transaction count between current and previous period", example = "8")
    private Long countAbsolute;

    @Schema(description = "Direction of change compared to the previous period (e.g., INCREASE, DECREASE, NO_CHANGE)", implementation = Direction.class, example = "INCREASE")
    private Direction direction;
}

