package za.co.reed.apiservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import za.co.reed.apiservice.controller.advice.ApiControllerAdvice;
import za.co.reed.apiservice.controller.impl.CategoryControllerImpl;
import za.co.reed.apiservice.dto.response.DataResponse;
import za.co.reed.apiservice.dto.response.CategoryResponse;
import za.co.reed.apiservice.service.CategoryService;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = CategoryControllerImpl.class, includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ApiControllerAdvice.class))
class CategoryControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private CategoryService testCategoryService;

        private final UUID testCategoryId = UUID.fromString("223e4567-e89b-12d3-a456-426614174001");

        @Test
        @WithMockUser
        void list_shouldReturnPaginatedCategories() throws Exception {
                // Given
                CategoryResponse testCategoryResponse = CategoryResponse.builder()
                                .id(testCategoryId)
                                .code("GROCERIES")
                                .label("Groceries")
                                .path("food.groceries")
                                .mccCodes("5411,5422")
                                .build();

                DataResponse<CategoryResponse> mockResponse = DataResponse.<CategoryResponse>builder()
                                .data(Collections.singletonList(testCategoryResponse))
                                .totalCount(1L)
                                .page(0)
                                .pageSize(20)
                                .hasMore(false)
                                .build();

                when(testCategoryService.list(anyInt(), anyInt(), anyString(), anyString()))
                                .thenReturn(ResponseEntity.ok(mockResponse));

                // When & Then
                mockMvc.perform(get("/api/v1/categories")
                                .param("page", "0")
                                .param("pageSize", "20")
                                .param("order", "path")
                                .param("sort", "asc"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                                .andExpect(jsonPath("$.data[0].id").value(testCategoryId.toString()))
                                .andExpect(jsonPath("$.data[0].code").value("GROCERIES"))
                                .andExpect(jsonPath("$.data[0].label").value("Groceries"))
                                .andExpect(jsonPath("$.totalCount").value(1))
                                .andExpect(jsonPath("$.page").value(0))
                                .andExpect(jsonPath("$.pageSize").value(20))
                                .andExpect(jsonPath("$.hasMore").value(false));
        }

        @Test
        @WithMockUser
        void list_shouldUseDefaultParameters() throws Exception {
                // Given
                DataResponse<CategoryResponse> mockResponse = DataResponse.<CategoryResponse>builder()
                                .data(Collections.emptyList())
                                .totalCount(0L)
                                .page(0)
                                .pageSize(20)
                                .hasMore(false)
                                .build();

                when(testCategoryService.list(0, 20, "asc", "path"))
                                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

                // When & Then - No parameters should use defaults
                mockMvc.perform(get("/api/v1/categories"))
                        .andExpect(status().isOk());
        }

        @Test
        @WithMockUser
        void getById_shouldReturnCategoryWhenFound() throws Exception {
                // Given
                CategoryResponse testCategoryResponse = CategoryResponse.builder()
                                .id(testCategoryId)
                                .code("GROCERIES")
                                .label("Groceries")
                                .path("food.groceries")
                                .mccCodes("5411,5422")
                                .build();

                when(testCategoryService.getById(testCategoryId))
                                .thenReturn(new ResponseEntity<>(testCategoryResponse,
                                                HttpStatus.OK));

                // When & Then
                mockMvc.perform(get("/api/v1/categories/{categoryId}", testCategoryId))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.id").value(testCategoryId.toString()))
                                .andExpect(jsonPath("$.code").value("GROCERIES"))
                                .andExpect(jsonPath("$.label").value("Groceries"));
        }

        @Test
        @WithMockUser
        void getById_shouldReturn404WhenNotFound() throws Exception {
                // Given
                when(testCategoryService.getById(testCategoryId))
                                .thenReturn(new ResponseEntity<>(HttpStatus.NOT_FOUND));

                // When & Then
                mockMvc.perform(get("/api/v1/categories/{categoryId}", testCategoryId))
                                .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser
        void getByMccCodes_shouldReturnFilteredCategories() throws Exception {
                // Given
                CategoryResponse testCategoryResponse = CategoryResponse.builder()
                                .id(testCategoryId)
                                .code("GROCERIES")
                                .label("Groceries")
                                .path("food.groceries")
                                .mccCodes("5411,5422")
                                .build();

                DataResponse<CategoryResponse> mockResponse = DataResponse.<CategoryResponse>builder()
                                .data(Collections.singletonList(testCategoryResponse))
                                .totalCount(1L)
                                .page(0)
                                .pageSize(20)
                                .hasMore(false)
                                .build();

                when(testCategoryService.getByMccCodes(anyString(), anyInt(), anyInt(), anyString(), anyString()))
                                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

                // When & Then
                mockMvc.perform(get("/api/v1/categories/mcc")
                                .param("mccCodes", "5411")
                                .param("page", "0")
                                .param("pageSize", "20")
                                .param("order", "code")
                                .param("sort", "asc"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.data[0].code").value("GROCERIES"))
                                .andExpect(jsonPath("$.data[0].mccCodes").value("5411,5422"));
        }

        @Test
        @WithMockUser
        void getByMccCodes_shouldReturnEmptyWhenNoMatches() throws Exception {
                // Given
                DataResponse<CategoryResponse> mockResponse = DataResponse.<CategoryResponse>builder()
                                .data(Collections.emptyList())
                                .totalCount(0L)
                                .page(0)
                                .pageSize(20)
                                .hasMore(false)
                                .build();



                when(testCategoryService.getByMccCodes(anyString(), any(Integer.class), any(Integer.class),
                                any(String.class), any(String.class)))
                        .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

                // When & Then
                mockMvc.perform(get("/api/v1/categories/mcc")
                                .param("mccCodes", "9999"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalCount").value(0))
                                .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        void unauthenticatedRequest_shouldReturn401() throws Exception {
                mockMvc.perform(get("/api/v1/categories"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        void list_shouldReturn500WhenServiceThrowsException() throws Exception {
                // Given
                when(testCategoryService.list(any(Integer.class), any(Integer.class), any(String.class), any(String.class)))
                                .thenThrow(new RuntimeException("Service error"));

                // When & Then
                mockMvc.perform(get("/api/v1/categories")
                                .param("page", "0")
                                .param("pageSize", "20"))
                                .andExpect(status().is5xxServerError());
        }
}