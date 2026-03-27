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
@Schema(description = "Response metadata providing caching and generation details")
public class Meta {

    @Schema(description = "Indicates whether the response was served from cache", example = "true")
    private boolean cached;

    @Schema(description = "Time-to-live (TTL) for the cached response, in seconds", example = "300")
    private int cacheTtlSeconds;

    @Schema(description = "Timestamp when the response was generated", example = "2026-03-27T18:45:00")
    private Date generatedAt;
}