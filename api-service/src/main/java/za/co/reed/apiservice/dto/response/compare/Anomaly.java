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
@Schema(description = "Statistical anomaly detected in a category")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Anomaly {

    private String categoryId;
    private AnomalyReason reason;
    private BigDecimal zScore;
    private String description;
}
