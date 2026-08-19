package com.yr.perftest.platform.agent;

import com.yr.perftest.platform.agent.contract.AgentErrorCode;
import com.yr.perftest.platform.agent.contract.ApiErrorBody;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.execution.ExecutionConflictException;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.IdempotencyConflictException;
import com.yr.perftest.platform.facade.DataSourceUnavailableException;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.identity.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice(basePackages = "com.yr.perftest.platform.agent")
public class AgentExceptionHandler {
    public static final String SCHEMA_VERSION = "1";

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException exception) {
        return envelope(HttpStatus.UNAUTHORIZED, AgentErrorCode.AUTHENTICATION_FAILED, exception.getMessage());
    }

    @ExceptionHandler(ExecutionValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleExecutionValidation(ExecutionValidationException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("does not exist")) {
            return envelope(HttpStatus.NOT_FOUND, AgentErrorCode.NOT_FOUND, message);
        }
        return envelope(HttpStatus.BAD_REQUEST, AgentErrorCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return envelope(HttpStatus.BAD_REQUEST, AgentErrorCode.VALIDATION_FAILED, exception.getMessage());
    }

    @ExceptionHandler(DataSourceUnavailableException.class)
    public ResponseEntity<?> handleDataSourceUnavailable(DataSourceUnavailableException exception) {
        if (exception.availability() != null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiResponse<>(
                            UUID.randomUUID().toString(),
                            SCHEMA_VERSION,
                            new AvailabilityData(exception.availability()),
                            new ApiErrorBody(AgentErrorCode.DATA_SOURCE_UNAVAILABLE, exception.getMessage()),
                            List.of(),
                            null,
                            null
                    ));
        }
        return envelope(HttpStatus.SERVICE_UNAVAILABLE, AgentErrorCode.DATA_SOURCE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(IdempotencyConflictException exception) {
        return envelope(HttpStatus.CONFLICT, AgentErrorCode.IDEMPOTENCY_CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ExecutionConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleExecutionConflict(ExecutionConflictException exception) {
        return envelope(HttpStatus.CONFLICT, AgentErrorCode.EXECUTION_CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return envelope(HttpStatus.FORBIDDEN, AgentErrorCode.ACCESS_DENIED, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("request validation failed");
        return envelope(HttpStatus.BAD_REQUEST, AgentErrorCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        return envelope(HttpStatus.INTERNAL_SERVER_ERROR, AgentErrorCode.INTERNAL_ERROR, "internal error");
    }

    private static ResponseEntity<ApiResponse<Void>> envelope(
            HttpStatus status,
            AgentErrorCode code,
            String message
    ) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(
                        UUID.randomUUID().toString(),
                        SCHEMA_VERSION,
                        new ApiErrorBody(code, message)
                ));
    }

    public record AvailabilityData(Availability availability) {
    }
}
