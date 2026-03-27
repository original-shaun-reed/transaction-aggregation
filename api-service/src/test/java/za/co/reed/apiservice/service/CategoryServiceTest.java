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
import za.co.reed.apiservice.service.RedisCacheService;
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
    private CategoryRepository categoryRepository;

    @Mock
    private RedisCacheService cacheService;

    @Mock
    private CacheKeyBuilder cacheKeyBuilder;

    @Mock
    private CacheConfigProperties cacheConfigProperties;

    @InjectMocks
    private CategoryService categoryService;

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
        when(cacheConfigProperties.getCategories()).thenReturn(3600);

        // Common stubbing for cache miss (most tests will need this)
        // Individual tests can override this if they want to test cache hit
        when(cacheKeyBuilder.getKey(anyString(), any(Object[].class))).thenReturn("cache-key");
        when(cacheService.get(anyString(), any(Class.class))).thenReturn(Optional.empty());
    }

    @Test
    void list_shouldReturnPaginatedCategories() {
        // Given
        List<Category> categories = Collections.singletonList(testCategory);
        Page<Category> categoryPage = new PageImpl<>(categories, PageRequest.of(0, 10, Sort.Direction.ASC, "code"), 1);
        when(cacheKeyBuilder.getKey(anyString(), any(), any(), any(), any())).thenReturn("cache-key");
        when(cacheService.get(anyString(), eq(DataResponse.class))).thenReturn(Optional.empty());
        when(categoryRepository.findAll(any(PageRequest.class))).thenReturn(categoryPage);

        // When
        ResponseEntity<DataResponse<CategoryResponse>> response = categoryService.list(0, 10, "code", "ASC");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getTotalCount()).isEqualTo(1);

        CategoryResponse categoryResponse = response.getBody().getData().get(0);
        assertThat(categoryResponse.getCode()).isEqualTo("GROCERIES");
        assertThat(categoryResponse.getLabel()).isEqualTo("Groceries");

        // Verify cache was called
        verify(cacheService).put(anyString(), any(DataResponse.class), any());
    }

    @Test
    void list_shouldReturnEmptyResponseWhenNoCategories() {
        // Given
        Page<Category> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(categoryRepository.findAll(any(PageRequest.class))).thenReturn(emptyPage);

        // When
        ResponseEntity<DataResponse<CategoryResponse>> response = categoryService.list(0, 10, "code", "ASC");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void list_shouldHandleExceptionAndReturnInternalServerError() {
        // Given
        when(categoryRepository.findAll(any(PageRequest.class))).thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThatThrownBy(() -> categoryService.list(0, 10, "code", "ASC"))
                .isInstanceOf(ApiInternalServerErrorException.class);
    }

    @Test
    void getById_shouldReturnCategoryWhenFound() {
        // Given
        UUID categoryId = testCategory.getExternalId();
        when(categoryRepository.findByExternalId(categoryId)).thenReturn(testCategory);

        // When
        ResponseEntity<CategoryResponse> response = categoryService.getById(categoryId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("GROCERIES");
        assertThat(response.getBody().getLabel()).isEqualTo("Groceries");
    }

    @Test
    void getById_shouldReturnNotFoundWhenCategoryDoesNotExist() {
        // Given
        UUID categoryId = UUID.randomUUID();

        // When
        when(cacheService.get(any(String.class), any(Class.class))).thenReturn(Optional.empty());
        when(categoryRepository.findByExternalId(any(UUID.class))).thenReturn(null);

        // Then
        assertThatThrownBy(() -> categoryService.getById(categoryId)).isInstanceOf(ApiNotFoundException.class);
    }

    @Test
    void getById_shouldHandleExceptionAndReturnInternalServerError() {
        // Given
        UUID categoryId = UUID.randomUUID();

        // When
        when(categoryRepository.findByExternalId(categoryId)).thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThatThrownBy(() -> categoryService.getById(categoryId)).isInstanceOf(ApiInternalServerErrorException.class);
    }

    @Test
    void getByMccCodes_shouldReturnMatchingCategories() {
        // Given
        List<Category> categories = Collections.singletonList(testCategory);
        Page<Category> categoryPage = new PageImpl<>(categories, PageRequest.of(0, 10, Sort.Direction.ASC, "code"), 1);
        when(categoryRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(categoryPage);

        // When
        ResponseEntity<DataResponse<CategoryResponse>> response = categoryService.getByMccCodes("5411", 0, 10, "code",
                "ASC");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);

        CategoryResponse categoryResponse = response.getBody().getData().get(0);
        assertThat(categoryResponse.getCode()).isEqualTo("GROCERIES");
    }

    @Test
    void getByMccCodes_shouldReturnEmptyResponseWhenNoMatches() {
        // Given
        Page<Category> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(cacheKeyBuilder.getKey(anyString(), any(), any(), any(), any())).thenReturn("cache-key");
        when(cacheService.get(anyString(), eq(DataResponse.class))).thenReturn(Optional.empty());
        when(categoryRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(emptyPage);

        // When
        ResponseEntity<DataResponse<CategoryResponse>> response = categoryService.getByMccCodes("9999", 0, 10, "code",
                "ASC");

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void getByMccCodes_shouldHandleExceptionAndReturnInternalServerError() {
        // Given
        when(cacheKeyBuilder.getKey(anyString(), any(), any(), any(), any())).thenReturn("cache-key");
        when(cacheService.get(anyString(), eq(DataResponse.class))).thenReturn(Optional.empty());
        when(categoryRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThatThrownBy(() -> categoryService.getByMccCodes("5411", 0, 10, "code", "ASC"))
                .isInstanceOf(ApiInternalServerErrorException.class);
    }
}