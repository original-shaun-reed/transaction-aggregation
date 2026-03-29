package za.co.reed.apiservice.dto;

import lombok.Data;
import za.co.reed.apiservice.dto.response.compare.CategoryBreakdown;

@Data
public class CategoryBreakdownZScore {
    CategoryBreakdown breakdown;
    double zScore;

    public CategoryBreakdownZScore(CategoryBreakdown breakdown, double zScore) {
        this.breakdown = breakdown;
        this.zScore = zScore;
    }
}
