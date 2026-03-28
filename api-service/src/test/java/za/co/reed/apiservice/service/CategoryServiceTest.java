package za.co.reed.apiservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import za.co.reed.apiservice.builder.CacheKeyBuilder;
import za.co.reed.apiservice.config.properties.CacheConfigProperties;
import za.co.reed.apiservice.dto.response.DataResponse;
import za.co.reed.apiservice.dto.response.CategoryResponse;
import za.co.reed.apiservice.exception.ApiInternalServerErrorException;
import za.co.reed.apiservice.exception.ApiNotFoundException;
import za.co.reed.persistence.entity.Category;
import za.co.reed.persistence.repository.CategoryRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class CategoryServiceTest {

    @Mock
    private CategoryRepository testCategoryRepository;

    @Mock
    private RedisCacheService testCacheService;

    @Mock
    private CacheKeyBuilder testCacheKeyBuilder;

    @Mock
    private CacheConfigProperties testCacheConfigProperties;

    @InjectMocks
    private CategoryService testCategoryService;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = new Category();
        testCategory.setId(1L);
        testCategory.setExternalId(UUID.randomUUID());
        testCategory.setCode("GROCERIES");
        testCategory.setLabel("Groceries");
        testCategory.setPath("food.groceries");
        testCategory.setMccCodes("5411,5422");

        // Configure cache properties mock
        when(testCacheConfigProperties.getCategories()).thenReturn(3600);

        // Common stubbing for cache miss (most tests will need this)
        // Individual tests can override this if they want to test cache hit
        when(testCacheKeyBuilder.getKey(anyString(), any(Object[].class))).thenReturn("cache-key");
        when(testCacheService.get(anyString(), any(Class.class))).thenReturn(Optional.empty());
    }

    @Test
    void list_shouldReturnPaginatedCategories() {
        // Given
        List<Category> testCategories = Collections.singletonList(testCategory);
        Page<Category> testCategoryPage = new PageImpl<>(testCategories, PageRequest.of(0, 10, Sort.Direction.ASC, "code"), 1);
        when(testCacheKeyBuilder.getKey(anyString(), any(), any(), any(), any())).thenReturn("cache-key");
        when(testCacheService.get(anyString(), eq(DataResponse.class))).thenReturn(Optional.empty());
        when(testCategoryRepository.findAll(any(PageRequest.class))).thenReturn(testCategoryPage);

        // When
        ResponseEntity<DataResponse<CategoryResponse>> testResponse = testCategoryService.list(0, 10, "code", "ASC");

        // Then
        assertThat(testResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(testResponse.getBody()).isNotNull();
        assertThat(testResponse.getBody().getData()).hasSize(1);
        assertThat(testResponse.getBody().getTotalCount()).isEqualTo(1);

        CategoryResponse testCategoryResponse = testResponse.getBody().getData().get(0);
        assertThat(testCategoryResponse.getCode()).isEqualTo("GROCERIES");
        assertThat(testCategoryResponse.getLabel()).isEqualTo("Groceries");

        // Verify cache was called
        verify(testCacheService).put(anyString(), any(DataResponse.class), any());
    }

    @Test
    void list_shouldReturnEmptyResponseWhenNoCategories() {
        // Given
        Page<Category> testEmptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(testCategoryRepository.findAll(any(PageRequest.class))).thenReturn(testEmptyPage);

        // When
        ResponseEntity<DataResponse<CategoryResponse>> testResponse = testCategoryService.list(0, 10, "code", "ASC");

        // Then
        assertThat(testResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(testResponse.getBody()).isNotNull();
    }

    @Test
    void list_shouldHandleExceptionAndReturnInternalServerError() {
        // Given
        when(testCategoryRepository.findAll(any(PageRequest.class))).thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThatThrownBy(() -> testCategoryService.list(0, 10, "code", "ASC"))
                .isInstanceOf(ApiInternalServerErrorException.class);
    }

    @Test
    void getById_shouldReturnCategoryWhenFound() {
        // Given
        UUID testCategoryId = testCategory.getExternalId();
        when(testCategoryRepository.findByExternalId(testCategoryId)).thenReturn(testCategory);

        // When
        ResponseEntity<CategoryResponse> testResponse = testCategoryService.getById(testCategoryId);

        // Then
        assertThat(testResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(testResponse.getBody()).isNotNull();
        assertThat(testResponse.getBody().getCode()).isEqualTo("GROCERIES");
        assertThat(testResponse.getBody().getLabel()).isEqualTo("Groceries");
    }

    @Test
    void getById_shouldReturnNotFoundWhenCategoryDoesNotExist() {
        // Given
        UUID testCategoryId = UUID.randomUUID();

        // When
        when(testCacheService.get(any(String.class), any(Class.class))).thenReturn(Optional.empty());
        when(testCategoryRepository.findByExternalId(any(UUID.class))).thenReturn(null);

        // Then
        assertThatThrownBy(() -> testCategoryService.getById(testCategoryId)).isInstanceOf(ApiNotFoundException.class);
    }

    @Test
    void getById_shouldHandleExceptionAndReturnInternalServerError() {
        // Given
        UUID testCategoryId = UUID.randomUUID();

        // When
        when(testCategoryRepository.findByExternalId(testCategoryId)).thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThatThrownBy(() -> testCategoryService.getById(testCategoryId)).isInstanceOf(ApiInternalServerErrorException.class);
    }

    @Test
    void getByMccCodes_shouldReturnMatchingCategories() {
        // Given
        List<Category> testCategories = Collections.singletonList(testCategory);
        Page<Category> testCategoryPage = new PageImpl<>(testCategories, PageRequest.of(0, 10, Sort.Direction.ASC, "code"), 1);
        when(testCategoryRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(testCategoryPage);

        // When
        ResponseEntity<DataResponse<CategoryResponse>> testResponse = testCategoryService.getByMccCodes("5411", 0, 10, "code",
                "ASC");

        // Then
        assertThat(testResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(testResponse.getBody()).isNotNull();
        assertThat(testResponse.getBody().getData()).hasSize(1);

        CategoryResponse testCategoryResponse = testResponse.getBody().getData().get(0);
        assertThat(testCategoryResponse.getCode()).isEqualTo("GROCERIES");
    }

    @Test
    void getByMccCodes_shouldReturnEmptyResponseWhenNoMatches() {
        // Given
        Page<Category> testEmptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(testCacheKeyBuilder.getKey(anyString(), any(), any(), any(), any())).thenReturn("cache-key");
        when(testCacheService.get(anyString(), eq(DataResponse.class))).thenReturn(Optional.empty());
        when(testCategoryRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(testEmptyPage);

        // When
        ResponseEntity<DataResponse<CategoryResponse>> testResponse = testCategoryService.getByMccCodes("9999", 0, 10, "code",
                "ASC");

        // Then
        assertThat(testResponse.getBody()).isNotNull();
        assertThat(testResponse.getBody().getData()).isNull();
    }

    @Test
    void getByMccCodes_shouldHandleExceptionAndReturnInternalServerError() {
        // Given
        when(testCacheKeyBuilder.getKey(anyString(), any(), any(), any(), any())).thenReturn("cache-key");
        when(testCacheService.get(anyString(), eq(DataResponse.class))).thenReturn(Optional.empty());
        when(testCategoryRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThatThrownBy(() -> testCategoryService.getByMccCodes("5411", 0, 10, "code", "ASC"))
                .isInstanceOf(ApiInternalServerErrorException.class);
    }
}