package za.co.reed.apiservice.controller.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import za.co.reed.apiservice.controller.CategoryController;
import za.co.reed.apiservice.dto.response.DataResponse;
import za.co.reed.apiservice.dto.response.CategoryResponse;
import za.co.reed.apiservice.service.CategoryService;

@RestController
@RequiredArgsConstructor
public class CategoryControllerImpl implements CategoryController {
    private final CategoryService categoryService;

    @Override
    public ResponseEntity<DataResponse<CategoryResponse>> list(int page, int pageSize, String order, String sort) {
        return categoryService.list(page, pageSize, order, sort);
    }

    @Override
    public ResponseEntity<CategoryResponse> getById(UUID id) {
        return categoryService.getById(id);
    }

    @Override
    public ResponseEntity<DataResponse<CategoryResponse>> getByMccCodes(String mccCodes, int page, int pageSize, String order, String sort) {
        return categoryService.getByMccCodes(mccCodes, page, pageSize, order, sort);
    }
}
