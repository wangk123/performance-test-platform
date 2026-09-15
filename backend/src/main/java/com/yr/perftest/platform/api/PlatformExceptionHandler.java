package com.yr.perftest.platform.api;

import com.yr.perftest.platform.identity.AuthenticationException;
import com.yr.perftest.platform.execution.ExecutionConflictException;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.llm.LlmConflictException;
import com.yr.perftest.platform.llm.LlmValidationException;
import com.yr.perftest.platform.monitoring.MonitoringValidationException;
import com.yr.perftest.platform.project.ProjectValidationException;
import com.yr.perftest.platform.script.ScriptValidationException;
import com.yr.perftest.platform.seed.SeedValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler(ExecutionConflictException.class)
    public ResponseEntity<ApiError> handleExecutionConflict(ExecutionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("EXECUTION_CONFLICT", exception.getMessage()));
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

    @ExceptionHandler(SeedValidationException.class)
    public ResponseEntity<ApiError> handleSeedValidation(SeedValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("SEED_VALIDATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("ACCESS_DENIED", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("request validation failed");
        return ResponseEntity.badRequest().body(new ApiError("REQUEST_VALIDATION_FAILED", message));
    }

    /** 容器层 multipart 超限（max-file-size）在 DispatcherServlet.checkMultipart 抛出、早于 handler 确定，
     * 只有无 scope 限制的全局 advice 能接住（局部 @ExceptionHandler 不可达），统一映射 400。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("REQUEST_VALIDATION_FAILED", "uploaded file exceeds the size limit"));
    }

    @ExceptionHandler(com.yr.perftest.platform.task.plandoc.PlanStateException.class)
    public ResponseEntity<PlanErrorBody> handlePlanState(com.yr.perftest.platform.task.plandoc.PlanStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new PlanErrorBody(
                "PLAN_STATE", exception.getMessage(), null, null,
                exception.getStatus().name(), exception.getAllowedActions()));
    }

    @ExceptionHandler(com.yr.perftest.platform.task.plandoc.PlanRevisionConflictException.class)
    public ResponseEntity<PlanErrorBody> handlePlanRevisionConflict(com.yr.perftest.platform.task.plandoc.PlanRevisionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new PlanErrorBody(
                "PLAN_REVISION_CONFLICT", exception.getMessage(),
                exception.getCurrentRevision(), exception.getServerMarkdown(), null, null));
    }

    @ExceptionHandler(com.yr.perftest.platform.task.plandoc.PlanPrecheckFailedException.class)
    public ResponseEntity<PlanErrorBody> handlePlanPrecheck(com.yr.perftest.platform.task.plandoc.PlanPrecheckFailedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(PlanErrorBody.of("PLAN_PRECHECK_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(com.yr.perftest.platform.task.plandoc.PlanAccessDeniedException.class)
    public ResponseEntity<PlanErrorBody> handlePlanAccessDenied(com.yr.perftest.platform.task.plandoc.PlanAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(PlanErrorBody.of("PLAN_ACCESS_DENIED", exception.getMessage()));
    }

    @ExceptionHandler(com.yr.perftest.platform.task.plandoc.PlanValidationException.class)
    public ResponseEntity<PlanErrorBody> handlePlanValidation(com.yr.perftest.platform.task.plandoc.PlanValidationException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.startsWith("SHARE_NOT_FOUND")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(PlanErrorBody.of("SHARE_NOT_FOUND", message));
        }
        return ResponseEntity.badRequest().body(PlanErrorBody.of("PLAN_INVALID", message));
    }

    @ExceptionHandler(com.yr.perftest.platform.envcheck.EnvCheckValidationException.class)
    public ResponseEntity<ApiError> handleEnvCheckValidation(com.yr.perftest.platform.envcheck.EnvCheckValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("ENV_CREDENTIAL_INVALID", exception.getMessage()));
    }

    @ExceptionHandler(com.yr.perftest.platform.envcheck.EnvCheckCredentialMissingException.class)
    public ResponseEntity<EnvCredentialMissingError> handleEnvCheckCredentialMissing(com.yr.perftest.platform.envcheck.EnvCheckCredentialMissingException exception) {
        return ResponseEntity.badRequest()
                .body(new EnvCredentialMissingError("ENV_CREDENTIALS_MISSING", exception.getMessage(), exception.getMissingHosts()));
    }

    /** 缺凭据 400 body：code + message + 缺失凭据的机器地址清单。 */
    record EnvCredentialMissingError(String code, String message, java.util.List<String> missingHosts) {
    }
}
