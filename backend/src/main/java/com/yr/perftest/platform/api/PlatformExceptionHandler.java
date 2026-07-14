package com.yr.perftest.platform.api;

import com.yr.perftest.platform.identity.AuthenticationException;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.llm.LlmConflictException;
import com.yr.perftest.platform.llm.LlmValidationException;
import com.yr.perftest.platform.monitoring.MonitoringValidationException;
import com.yr.perftest.platform.project.ProjectValidationException;
import com.yr.perftest.platform.script.ScriptValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PlatformExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("AUTHENTICATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(ProjectValidationException.class)
    public ResponseEntity<ApiError> handleProjectValidation(ProjectValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("PROJECT_VALIDATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(ScriptValidationException.class)
    public ResponseEntity<ApiError> handleScriptValidation(ScriptValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("SCRIPT_VALIDATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(ExecutionValidationException.class)
    public ResponseEntity<ApiError> handleExecutionValidation(ExecutionValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("EXECUTION_VALIDATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(MonitoringValidationException.class)
    public ResponseEntity<ApiError> handleMonitoringValidation(MonitoringValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("MONITORING_VALIDATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(LlmValidationException.class)
    public ResponseEntity<ApiError> handleLlmValidation(LlmValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("LLM_VALIDATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(LlmConflictException.class)
    public ResponseEntity<ApiError> handleLlmConflict(LlmConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("LLM_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("request validation failed");
        return ResponseEntity.badRequest().body(new ApiError("REQUEST_VALIDATION_FAILED", message));
    }
}
