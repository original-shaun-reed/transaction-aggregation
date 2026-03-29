package za.co.reed.apiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import za.co.reed.apiservice.builder.CacheKeyBuilder;
import za.co.reed.apiservice.config.properties.CacheConfigProperties;
import za.co.reed.apiservice.dto.response.CategoryResponse;
import za.co.reed.apiservice.dto.response.DataResponse;
import za.co.reed.apiservice.exception.ApiInternalServerErrorException;
import za.co.reed.apiservice.exception.ApiNotFoundException;
import za.co.reed.apiservice.specification.CategorySpecification;
import za.co.reed.persistence.entity.Category;
import za.co.reed.persistence.repository.CategoryRepository;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final RedisCacheService cacheService;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final CacheConfigProperties cacheConfigProperties;

    public ResponseEntity<DataResponse<CategoryResponse>> list(int page, int pageSize, String order, String sort) {
        try {
            String cacheKey = cacheKeyBuilder.getKey("categoryList", page, pageSize, order, sort);

            Optional<DataResponse> cachedResponse = cacheService.get(cacheKey, DataResponse.class);
            if (cachedResponse.isPresent()) {
                return ResponseEntity.ok(cachedResponse.get());
            }

            PageRequest pageRequest = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.fromString(sort), order));
            Page<Category> categories = categoryRepository.findAll(pageRequest);

            DataResponse<CategoryResponse> response = createCategoryResponse(categories);
            cacheService.put(cacheKey, response, Duration.ofSeconds(cacheConfigProperties.getCategories()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching categories: {}", e.getMessage(), e);
            throw new ApiInternalServerErrorException("Error occurred while retrieving categories");
        }
    }

    public ResponseEntity<CategoryResponse> getById(UUID externalId) {
        try {
            String cacheKey = cacheKeyBuilder.getKey("getByUUID", externalId);

            Optional<CategoryResponse> cachedResponse = cacheService.get(cacheKey, CategoryResponse.class);
            if (cachedResponse.isPresent()) {
                return ResponseEntity.ok(cachedResponse.get());
            }

            Category category = categoryRepository.findByExternalId(externalId);
            if (Objects.isNull(category)) {
                throw new ApiNotFoundException("Category not found: " + externalId.toString());
            }

            CategoryResponse response = CategoryResponse.of(category);
            cacheService.put(cacheKey, response, Duration.ofSeconds(cacheConfigProperties.getCategories()));

            return ResponseEntity.ok(response);
        } catch (ApiNotFoundException apiNotFoundException) {
            throw apiNotFoundException;
        } catch (Exception e) {
            log.error("Error fetching category by ID: {}", e.getMessage(), e);
            throw new ApiInternalServerErrorException("Error occurred while retrieving category for: " + externalId);
        }
    }

    public ResponseEntity<DataResponse<CategoryResponse>> getByMccCodes(String mccCodes, int page, int pageSize,
                                                                        String order, String sort) {
        try {
            String cacheKey = cacheKeyBuilder.getKey("getByMccCodes", page, pageSize, order, sort);

            Optional<DataResponse> cachedResponse = cacheService.get(cacheKey, DataResponse.class);
            if (cachedResponse.isPresent()) {
                return ResponseEntity.ok(cachedResponse.get());
            }

            Specification<Category> specification = CategorySpecification.mccCodeContains(mccCodes);
            DataResponse<CategoryResponse> response = getResponse(specification, cacheKey, page, pageSize, order, sort);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching category for MCC Codes: {}", e.getMessage(), e);
            throw new ApiInternalServerErrorException("Error occurred while retrieving categories for MCC Codes: " + mccCodes);
        }
    }

    private DataResponse<CategoryResponse> getResponse(Specification<Category> specification, String cacheKey, int page,
                                                       int pageSize, String order, String sort) {
        PageRequest pageRequest = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.fromString(sort), order));
        Page<Category> categories = categoryRepository.findAll(specification, pageRequest);
        DataResponse<CategoryResponse> response = createCategoryResponse(categories);

        cacheService.put(cacheKey, response, Duration.ofSeconds(cacheConfigProperties.getCategories()));
        return response;
    }

    private DataResponse<CategoryResponse> createCategoryResponse(Page<Category> categories) {
        if (!categories.hasContent()) {
            return new DataResponse<>();
        }

        List<CategoryResponse> data = new ArrayList<>();
        for (Category category : categories.getContent()) {
            data.add(CategoryResponse.of(category));
        }

        return DataResponse.of(data, categories);
    }
}
