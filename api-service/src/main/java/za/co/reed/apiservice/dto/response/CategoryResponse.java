package za.co.reed.apiservice.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.reed.persistence.entity.Category;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    @Schema(description = "Unique identifier for the category", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @Schema(description = "Unique code for the category, used for lookups", example = "food_and_drink")
    private String code;

    @Schema(description = "Human-readable label for the category", example = "Food & Drink")
    private String label;

    @Schema(description = "Hierarchical path of the category in the format 'parent.child.grandchild'", example = "food_and_drink.restaurants")
    private String path;

    @Schema(description = "Comma-separated list of ISO 18245 MCC codes that map to this category", example = "5411,5422,5451")
    private String mccCodes;

    public static CategoryResponse of(Category category) {
        return CategoryResponse.builder()
                .id(category.getExternalId())
                .code(category.getCode())
                .label(category.getLabel())
                .path(category.getPath())
                .mccCodes(category.getMccCodes())
                .build();
    }
}
