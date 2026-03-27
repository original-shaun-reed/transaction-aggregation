package za.co.reed.apiservice.controller.advice;

import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import za.co.reed.apiservice.dto.response.ApiErrorResponse;
import za.co.reed.apiservice.exception.ApiInternalServerErrorException;
import za.co.reed.apiservice.exception.ApiNotFoundException;

@Slf4j
@RestControllerAdvice
public class ApiControllerAdvice {

        @ExceptionHandler(HttpMessageNotReadableException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        @ResponseBody
        public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(
                HttpMessageNotReadableException ex, HttpServletRequest request) {

                String errorMessage = "Invalid request body format";

                // Optional: Provide more specific message for date/format issues
                Throwable cause = ex.getCause();
                if (cause instanceof InvalidFormatException ife) {
                        errorMessage = "Invalid data format: " + ife.getValue() + " cannot be converted to " + ife.getTargetType().getSimpleName();
                } else if (cause != null) {
                        errorMessage = "Invalid request body: " + cause.getMessage();
                }

                ApiErrorResponse errorResponse = ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "INVALID_REQUEST_BODY",
                        errorMessage,
                        request.getRequestURI());

                return ResponseEntity.badRequest().body(errorResponse);
        }

        /**
         * Handles validation errors from @Valid annotated request bodies.
         * Returns a 400 BAD_REQUEST with field-level error details.
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        @ResponseBody
        public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
                                                                                      HttpServletRequest request) {

                List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(fe -> new ApiErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                                .toList();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                ApiErrorResponse errorResponse = ApiErrorResponse.withFieldErrors(
                                request.getRequestURI(), fieldErrors);

                return ResponseEntity.badRequest().headers(headers).body(errorResponse);
        }

        @ExceptionHandler(ConstraintViolationException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        @ResponseBody
        public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(ConstraintViolationException ex,
                                                                                   HttpServletRequest request) {

                // Collect all violation messages into a single string
                String errorMessage = ex.getConstraintViolations().stream()
                        .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                        .collect(Collectors.joining("; "));

                ApiErrorResponse errorResponse = ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "CONSTRAINT_VIOLATION",
                        errorMessage,
                        request.getRequestURI()
                );

                return ResponseEntity.badRequest().body(errorResponse);
        }

        /**
         * Handles AuthenticationException - when user authentication fails.
         * Returns a 401 UNAUTHORIZED with appropriate error message.
         */
        @ExceptionHandler(AuthenticationException.class)
        @ResponseStatus(HttpStatus.UNAUTHORIZED)
        @ResponseBody
        public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
                ApiErrorResponse errorResponse = ApiErrorResponse.of(
                        HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHORIZED",
                        "Authentication failed. Please provide valid credentials.",
                        request.getRequestURI()
                );

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }

        /**
         * Handles AccessDeniedException - when user lacks required permissions.
         * Returns a 403 FORBIDDEN with appropriate error message.
         */
        @ExceptionHandler(AccessDeniedException.class)
        @ResponseStatus(HttpStatus.FORBIDDEN)
        @ResponseBody
        public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {

                ApiErrorResponse errorResponse = ApiErrorResponse.of(
                        HttpStatus.FORBIDDEN.value(),
                        "FORBIDDEN",
                        "You do not have permission to access this resource.",
                        request.getRequestURI());

                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }

        /**
         * Handles CategoryNotFoundException - when a category is not found in the
         * system.
         * Returns a 404 NOT_FOUND with appropriate error message.
         */
        @ExceptionHandler(ApiNotFoundException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        @ResponseBody
        public ResponseEntity<ApiErrorResponse> handleCategoryNotFoundException(ApiNotFoundException ex, HttpServletRequest request) {
                ApiErrorResponse errorResponse = ApiErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        "NOT_FOUND",
                        ex.getMessage(),
                        request.getRequestURI()
                );

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }

        /**
         * Handles ApiInternalServerErrorException - when there's an internal server
         * error
         * related to category operations.
         * Returns a 500 INTERNAL_SERVER_ERROR with appropriate error message.
         */
        @ExceptionHandler(ApiInternalServerErrorException.class)
        @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        @ResponseBody
        public ResponseEntity<ApiErrorResponse> handleCategoryInternalServerException(ApiInternalServerErrorException ex,
                                                                                      HttpServletRequest request) {
                ApiErrorResponse errorResponse = ApiErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_ERROR",
                        ex.getMessage(),
                        request.getRequestURI()
                );

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }

        /**
         * Handles IllegalArgumentException - when invalid arguments are passed to
         * methods.
         * Returns a 400 BAD_REQUEST with appropriate error message.
         */
        @ExceptionHandler(IllegalArgumentException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        @ResponseBody
        public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex,
                                                                               HttpServletRequest request) {
                ApiErrorResponse errorResponse = ApiErrorResponse.of(
                                HttpStatus.BAD_REQUEST.value(),
                                "INVALID_ARGUMENT",
                                ex.getMessage(),
                                request.getRequestURI());

                return ResponseEntity.badRequest().body(errorResponse);
        }

        /**
         * Handles MissingServletRequestParameterException - when required request
         * parameters are missing.
         * Returns a 400 BAD_REQUEST with appropriate error message.
         */
        @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        @ResponseBody
        public ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex,
                                                                                              HttpServletRequest request) {
                ApiErrorResponse errorResponse = ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "MISSING_PARAMETER",
                        ex.getMessage(),
                        request.getRequestURI()
                );

                return ResponseEntity.badRequest().body(errorResponse);
        }

        /**
         * Handles MethodArgumentTypeMismatchException - when request parameter type
         * conversion fails.
         * Returns a 400 BAD_REQUEST with appropriate error message.
         */
        @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        @ResponseBody
        public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex,
                                                                                          HttpServletRequest request) {
                ApiErrorResponse errorResponse = ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "TYPE_MISMATCH",
                        ex.getMessage(),
                        request.getRequestURI()
                );

                return ResponseEntity.badRequest().body(errorResponse);
        }

        /**
         * Generic exception handler for any unhandled exceptions.
         * Returns a 500 INTERNAL_SERVER_ERROR with a generic error message.
         */
        @ExceptionHandler(Exception.class)
        @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        @ResponseBody
        public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
                ApiErrorResponse errorResponse = ApiErrorResponse.of(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "INTERNAL_SERVER_ERROR",
                                "An unexpected error occurred. Please try again later.",
                                request.getRequestURI());

                log.error("Unexpected error occurred at URI: {}", request.getRequestURI(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
}