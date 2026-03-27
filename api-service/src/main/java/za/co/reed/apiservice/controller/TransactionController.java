package za.co.reed.apiservice.controller;

import java.util.Date;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.apiservice.dto.response.ApiErrorResponse;
import za.co.reed.apiservice.dto.response.DataResponse;
import za.co.reed.apiservice.dto.response.TransactionResponse;

@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Endpoints to retrieve, search, export, and manage transactions")
@Validated
public interface TransactionController {

    @Operation(
            summary = "Retrieve a paginated list of transactions",
            description = "Returns a paginated list of transactions filtered by account, status, date range, and sorting options."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved paginated transaction list", content = @Content(schema = @Schema(implementation = DataResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request: Invalid query parameters", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT"),
            @ApiResponse(responseCode = "404", description = "Not Found: No transactions match the criteria", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error: Unexpected error occurred", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<DataResponse<TransactionResponse>> list(@Parameter(description = "Filter results to a specific account ID")
                                                           @RequestParam(required = false)
                                                           String accountId,

                                                           @Parameter(description = "Filter by transaction status: PENDING, SETTLED, REVERSED")
                                                           @RequestParam(required = false)
                                                           TransactionStatus status,

                                                           @Parameter(description = "Start of date range (ISO-8601 format)") @RequestParam(required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                           Date from,

                                                           @Parameter(description = "End of date range (ISO-8601 format)") @RequestParam(required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                           Date to,

                                                           @Parameter(description = "Page number (default 0)")
                                                           @RequestParam(defaultValue = "0")
                                                           @Max(100)
                                                           int page,

                                                           @Parameter(description = "Page size (max 100, default 20)")
                                                           @RequestParam(defaultValue = "20")
                                                           @Max(1000)
                                                           int pageSize,

                                                           @Parameter(description = "Field to order results by (default: createdAt)")
                                                           @RequestParam(defaultValue = "createdAt")
                                                           String order,

                                                           @Parameter(description = "Sort direction (asc or desc, default asc)")
                                                           @RequestParam(defaultValue = "asc")
                                                           String sort);

    @Operation(
            summary = "Retrieve a transaction by ID",
            description = "Fetches a single transaction using its unique identifier."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction successfully retrieved", content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request: Invalid transaction ID format", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found: Transaction does not exist", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error: Unexpected error occurred", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
    ResponseEntity<TransactionResponse> getById(@Parameter(description = "Unique identifier of the transaction") @PathVariable UUID id);

    @Operation(
            summary = "Search transactions by merchant name",
            description = "Searches transactions by merchant name with optional date range and pagination."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved search results", content = @Content(schema = @Schema(implementation = DataResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request: Invalid query parameters", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found: No transactions match the search term", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error: Unexpected error occurred", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/search")
    ResponseEntity<DataResponse<TransactionResponse>> merchantSearch(@Parameter(description = "Search term matched against merchant name", required = true)
                                                                     @RequestParam
                                                                     @NotBlank
                                                                     String merchantName,

                                                                     @Parameter(description = "Start of date range (ISO-8601 format)")
                                                                     @RequestParam(required = false)
                                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                                     Date from,

                                                                     @Parameter(description = "End of date range (ISO-8601 format)")
                                                                     @RequestParam(required = false)
                                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                                     Date to,

                                                                     @Parameter(description = "Page number (default 0)")
                                                                     @RequestParam(defaultValue = "0")
                                                                     int page,

                                                                     @Parameter(description = "Page size (max 100, default 20)")
                                                                     @RequestParam(defaultValue = "20")
                                                                     int pageSize,

                                                                     @Parameter(description = "Field to order results by (default: createdAt)")
                                                                     @RequestParam(defaultValue = "createdAt")
                                                                     String order,

                                                                     @Parameter(description = "Sort direction (asc or desc, default asc)")
                                                                     @RequestParam(defaultValue = "asc")
                                                                     String sort);
}

