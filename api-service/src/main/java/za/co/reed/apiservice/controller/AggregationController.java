package za.co.reed.apiservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.reed.apiservice.dto.request.ComparisonRequest;
import za.co.reed.apiservice.dto.response.AggregationResponse;
import za.co.reed.apiservice.dto.response.ApiErrorResponse;
import za.co.reed.apiservice.dto.response.compare.ComparisonResponse;
import za.co.reed.commom.enums.PeriodType;
import za.co.reed.commom.enums.TransactionStatus;

import java.util.Date;
import java.util.List;

@Validated
@RequestMapping("/api/v1/aggregations")
@Tag(name = "Aggregations", description = "Pre-computed spend summaries and comparisons")
public interface AggregationController {
        @Operation(summary = "Spend summary for a period", description = "Returns total spend, count, and top category breakdown. Results are cached for 5 minutes.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Summary data", content = @Content(schema = @Schema(implementation = List.class))),
                        @ApiResponse(responseCode = "400", description = "Bad Request: Invalid date parameters", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT"),
                        @ApiResponse(responseCode = "404", description = "Not Found: No data for the specified criteria", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "500", description = "Internal Server Error: Unexpected error occurred", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping(value = { "/{accountId}/summary" }, produces = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<List<AggregationResponse>> summary(@Valid
                                                          @Parameter(description = "Filter to a specific account ID")
                                                          @PathVariable
                                                          String accountId,

                                                          @Valid
                                                          @Parameter(description = "Filter by period type: DAILY, MONTHLY, WEEKLY", example = "DAILY")
                                                          @RequestParam
                                                          PeriodType periodType,

                                                          @Valid
                                                          @Parameter(description = "Start of the period (inclusive)", example = "2026-03-01")
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                          Date from,

                                                          @Valid
                                                          @Parameter(description = "End of the period (inclusive)", example = "2026-03-31")
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                          Date to);

        @Operation(summary = "Period-over-period comparison", description = "Compares spend between two time windows. Runs both DB queries in parallel. Cached 5 minutes.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Comparison data", content = @Content(schema = @Schema(implementation = ComparisonResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Bad Request: Invalid request parameters", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT"),
                        @ApiResponse(responseCode = "404", description = "Not Found: No data for the specified criteria", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "500", description = "Internal Server Error: Unexpected error occurred", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PostMapping(value = { "/{accountId}/compare" }, produces = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<ComparisonResponse> compare(@PathVariable String accountId,
                        @Valid @RequestBody ComparisonRequest request);

        @Operation(summary = "Spend over time", description = "Returns spend grouped by day or month. Useful for chart visualisations.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Time series data", content = @Content(schema = @Schema(implementation = List.class))),
                        @ApiResponse(responseCode = "400", description = "Bad Request: Invalid date parameters", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT"),
                        @ApiResponse(responseCode = "404", description = "Not Found: No data for the specified criteria", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "500", description = "Internal Server Error: Unexpected error occurred", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping(value = { "/{accountId}/time-series" }, produces = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<List<AggregationResponse>> timeSeries(@Valid @Parameter(description = "Filter to a specific account ID")
                                                             @PathVariable
                                                             String accountId,

                                                             @Valid
                                                             @Parameter(description = "Filter by period type: DAILY, MONTHLY, WEEKLY", example = "DAILY")
                                                             @RequestParam
                                                             PeriodType periodType,

                                                             @Valid
                                                             @Parameter(description = "Start of the period (inclusive)", example = "2026-03-01")
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                             Date from,

                                                             @Valid  @Parameter(description = "End of the period (inclusive)", example = "2026-03-31")
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                             Date to);

        @Operation(summary = "Top merchants by spend", description = "Returns merchants ranked by total spend. Cached for 2 minutes.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Top merchants data", content = @Content(schema = @Schema(implementation = List.class))),
                        @ApiResponse(responseCode = "400", description = "Bad Request: Invalid parameters", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT"),
                        @ApiResponse(responseCode = "404", description = "Not Found: No data for the specified criteria", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "500", description = "Internal Server Error: Unexpected error occurred", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping(value = { "/{accountId}/top-merchants" }, produces = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<List<AggregationResponse>> topMerchants(@Valid
                                                               @Parameter(description = "Filter to a specific account ID")
                                                               @PathVariable String accountId,

                                                               @Valid
                                                               @Parameter(description = "Filter by status: PENDING, SETTLED, REVERSED")
                                                               @RequestParam TransactionStatus status,

                                                               @Valid
                                                               @Parameter(description = "Start of the period (inclusive)", example = "2026-03-01")
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                               Date from,

                                                               @Valid @Parameter(description = "End of the period (inclusive)", example = "2026-03-31")
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                               Date to,

                                                               @Valid
                                                               @Parameter(description = "Limit of results")
                                                               @RequestParam(defaultValue = "10")
                                                               int limit);

        @Operation(summary = "Spend broken down by category", description = "Returns spend and count per category for the given period.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Category breakdown data", content = @Content(schema = @Schema(implementation = List.class))),
                        @ApiResponse(responseCode = "400", description = "Bad Request: Invalid date parameters", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT"),
                        @ApiResponse(responseCode = "404", description = "Not Found: No data for the specified criteria", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "500", description = "Internal Server Error: Unexpected error occurred", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping(value = { "/{accountId}/by-category" }, produces = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<List<AggregationResponse>> byCategory(@Parameter(description = "Filter to a specific account ID")
                                                             @PathVariable
                                                             String accountId,

                                                             @Valid
                                                             @Parameter(description = "Start of the period (inclusive)", example = "2026-03-01")
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                             Date from,

                                                             @Valid
                                                             @Parameter(description = "End of the period (inclusive)", example = "2026-03-31")
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                             Date to);
}
