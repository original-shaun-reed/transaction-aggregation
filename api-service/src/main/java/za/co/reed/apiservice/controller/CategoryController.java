package za.co.reed.apiservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import za.co.reed.apiservice.dto.response.ApiErrorResponse;
import za.co.reed.apiservice.dto.response.CategoryResponse;
import za.co.reed.apiservice.dto.response.DataResponse;

import java.util.UUID;

@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Transaction category hierarchy")
@Validated
public interface CategoryController {
        @Operation(
                summary = "List all categories",
                description = """
                                Returns the full category hierarchy ordered by ltree path (parents before children). 
                                Includes codes, labels, paths, and MCC codes for client-side mapping.
                              """
        )
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Category list retrieved successfully", content = @Content(schema = @Schema(implementation = DataResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Bad Request: Invalid query parameters", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT"),
                        @ApiResponse(responseCode = "500", description = "Internal Server Error: Unexpected error occurred", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<DataResponse<CategoryResponse>> list(@Parameter(description = "Page size (default 0)")
                                                            @RequestParam(defaultValue = "0", required = false)
                                                            @Max(1000)
                                                            int page,

                                                            @Parameter(description = "Page size (max 100, default 20)")
                                                            @RequestParam(defaultValue = "20", required = false)
                                                            @Max(100)
                                                            int pageSize,

                                                            @Parameter(description = "Order (default path)")
                                                            @RequestParam(defaultValue = "path", required = false)
                                                            String order,

                                                            @Parameter(description = "Sort (default asc)")
                                                            @RequestParam(defaultValue = "asc", required = false)
                                                            String sort);

        @Operation(
                summary = "Look up a category by ID",
                description = """
                                  Retrieves the details of a single category resource by its unique identifier.
                                  The response includes metadata such as the category code, name, and any
                                  associated attributes. Use this endpoint when you need to fetch information
                                  about a specific category for display or processing.
                              """
        )
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Category retrieved successfully", content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Bad Request: Invalid category ID format", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT"),
                        @ApiResponse(responseCode = "404", description = "Not Found: Category does not exist", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "500", description = "Internal Server Error: Unexpected error occurred", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping(value = {"/{id}"}, produces = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<CategoryResponse> getById(@Parameter(description = "UUID of the category to retrieve", example = "123e4567-e89b-12d3-a456-426614174000") @PathVariable UUID id);

        @Operation(summary = "Look up categories by MCC code(s)", description = "Returns categories matching the provided MCC code(s). Accepts a comma-separated list of MCC codes for batch lookups.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Categories retrieved successfully", content = @Content(schema = @Schema(implementation = DataResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Bad Request: Invalid MCC code format", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT"),
                        @ApiResponse(responseCode = "404", description = "Not Found: No categories match the MCC codes", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "500", description = "Internal Server Error: Unexpected error occurred", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping(value = {"/mcc"}, produces = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<DataResponse<CategoryResponse>> getByMccCodes(@Parameter(description = "One or more MCC codes to look up, separated by commas", example = "5411,5812")
                                                                     @RequestParam
                                                                     String mccCodes,

                                                                     @Parameter(description = "Page size (default 0)")
                                                                     @RequestParam(defaultValue = "0")
                                                                     @Max(1000)
                                                                     int page,

                                                                     @Parameter(description = "Page size (max 100, default 20)")
                                                                     @RequestParam(defaultValue = "20", required = false)
                                                                     @Max(100)
                                                                     int pageSize,

                                                                     @Parameter(description = "Order (default label)")
                                                                     @RequestParam(defaultValue = "label", required = false)
                                                                     String order,

                                                                     @Parameter(description = "Sort (default asc)")
                                                                     @RequestParam(defaultValue = "asc", required = false)
                                                                     String sort);
}
