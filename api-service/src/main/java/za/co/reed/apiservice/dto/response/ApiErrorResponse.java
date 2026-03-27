package za.co.reed.apiservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response")
public record ApiErrorResponse(

        @Schema(description = "HTTP status code")
        int status,

        @Schema(description = "Short machine-readable error code", example = "VALIDATION_FAILED")
        String error,

        @Schema(description = "Human-readable description of the error")
        String message,

        @Schema(description = "Request path that triggered the error")
        String path,

        @Schema(description = "Timestamp of the error")
        Instant timestamp,

        @Schema(description = "Field-level validation errors — only present on 400 responses")
        List<FieldError> fieldErrors

) {
    public record FieldError(String field, String message) {}

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(status, error, message, path, Instant.now(), null);
    }

    public static ApiErrorResponse withFieldErrors(String path, List<FieldError> fieldErrors) {
        return new ApiErrorResponse(
                400, "VALIDATION_FAILED",
                "One or more fields failed validation",
                path, Instant.now(), fieldErrors
        );
    }
}
