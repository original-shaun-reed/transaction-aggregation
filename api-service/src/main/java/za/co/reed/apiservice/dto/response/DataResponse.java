package za.co.reed.apiservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Generic paginated response wrapper containing a list of items and pagination metadata")
public class DataResponse<T> {
    @Schema(description = "List of items returned in the current page", example = "[{\"id\":\"550e8400-e29b-41d4-a716-446655440000\",\"amount\":125.50}]")
    private List<T> data;

    @Schema(description = "Total number of matching items across all pages", example = "20")
    private long totalCount;

    @Schema(description = "Current page number (1-based index)", example = "2")
    private int page;

    @Schema(description = "Number of items contained in this page", example = "5")
    private int pageSize;

    @Schema(description = "Total number of pages available", example = "10")
    private long totalPages;

    @Schema(description = "Indicates whether more pages exist after the current one", example = "true")
    private boolean hasMore;

    public static <T, R> DataResponse<T> of(List<T> data, Page<R> page) {
        return DataResponse.<T>builder()
                .data(data)
                .totalPages(page.getTotalPages())
                .page(page.getPageable().getPageNumber())
                .pageSize(page.getNumberOfElements())
                .totalCount(page.getTotalElements())
                .hasMore(page.hasNext())
                .build();
    }
}
