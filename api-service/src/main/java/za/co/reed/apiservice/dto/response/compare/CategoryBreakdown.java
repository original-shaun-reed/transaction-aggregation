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
@Schema(description = "Per-category breakdown of spend in each period")
public class CategoryBreakdown {

    private String categoryId;
    private String label;
    private BigDecimal currentSpend;
    private BigDecimal previousSpend;
    private BigDecimal deltaPct;
    private BigDecimal shareOfTotalCurrent;
}
