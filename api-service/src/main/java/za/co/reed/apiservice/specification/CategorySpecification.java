package za.co.reed.apiservice.specification;

import org.springframework.data.jpa.domain.Specification;
import za.co.reed.persistence.entity.Category;

import java.util.List;

public class CategorySpecification {

    public static Specification<Category> pathNotLike() {
       return (root, query, cb) -> cb.notLike(root.get("path").as(String.class), "%.%");
    }

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
