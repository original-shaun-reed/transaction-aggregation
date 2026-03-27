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
@Schema(description = "Differences between current and previous period")
public class Delta {

    private BigDecimal absoluteSpend;
    private BigDecimal spendPercentage;
    private Long countAbsolute;
    private Direction direction;
}
