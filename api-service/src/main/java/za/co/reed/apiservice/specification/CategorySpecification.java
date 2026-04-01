package za.co.reed.apiservice.specification;

import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import za.co.reed.persistence.entity.Category;

public class CategorySpecification {

    public static Specification<Category> mccCodeContains(String mccCodes) {
        List<String> mccCodeList = List.of(mccCodes.split(","));

       return mccCodeList.stream()
               .map(CategorySpecification::mccCodeLike)
               .reduce(Specification::or)
               .orElse((root, query, cb) -> cb.conjunction());
    }

    private static Specification<Category> mccCodeLike(String mccCode) {
        return (root, query, cb) -> cb.like(root.get("mccCodes"), "%" + mccCode + "%");
    }
}
