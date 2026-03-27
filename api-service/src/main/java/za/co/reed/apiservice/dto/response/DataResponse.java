package za.co.reed.apiservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataResponse<T> {
    @Schema(description = "Items in the current page")
    private List<T> data;

    @Schema(description = "Total number of matching items across all pages", example = "20")
    private long totalCount;

    @Schema(description = "Page number", example = "2")
    private int page;

    @Schema(description = "Number of items in this page", example = "5")
    private int pageSize;

    @Schema(description = "Total number of pages", example = "10")
    private long totalPages;

    @Schema(description = "True if there are more pages after this one", example = "true")
    boolean hasMore;
}
