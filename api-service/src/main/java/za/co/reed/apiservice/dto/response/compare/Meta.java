package za.co.reed.apiservice.dto.response.compare;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response metadata")
public class Meta {
    private boolean cached;
    private int cacheTtlSeconds;
    private Date generatedAt;
}
