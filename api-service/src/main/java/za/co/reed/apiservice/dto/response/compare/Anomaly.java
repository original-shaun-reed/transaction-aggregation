package za.co.reed.apiservice.dto.response.compare;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.reed.apiservice.enums.AnomalyReason;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Statistical anomaly detected in a category, including reason, z-score, and descriptive context")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Anomaly {

    @Schema(description = "Unique identifier of the category where the anomaly was detected", example = "groceries")
    private String categoryId;

    @Schema(description = "Reason code or classification for the anomaly", implementation = AnomalyReason.class, example = "OUTLIER_HIGH_SPEND")
    private AnomalyReason reason;

    @Schema(description = "Z-score quantifying the deviation from the mean (standard deviations)", example = "3.25")
    private BigDecimal zScore;

    @Schema(description = "Human-readable explanation of the anomaly", example = "Spending in groceries is significantly higher than expected compared to historical averages")
    private String description;
}

