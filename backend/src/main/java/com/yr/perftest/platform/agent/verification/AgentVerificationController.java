package com.yr.perftest.platform.agent.verification;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.facade.VerificationFacade;
import com.yr.perftest.platform.verification.ChangeType;
import com.yr.perftest.platform.verification.VerificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 优化验证 agent 面入口（T9）：变更登记 + 基线/候选三态验证。无审批流，与取证入口语义分离。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentVerificationController {
    private final VerificationFacade verificationFacade;

    public AgentVerificationController(VerificationFacade verificationFacade) {
        this.verificationFacade = verificationFacade;
    }

    @PostMapping("/change-records")
    public ApiResponse<VerificationService.ChangeRecordView> registerChange(@RequestBody RegisterChangeRequest request) {
        VerificationService.ChangeRecordView view = verificationFacade.registerChange(
                request.changeType(),
                request.changeRef(),
                request.description()
        );
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, view);
    }

    @PostMapping("/verifications")
    public ApiResponse<VerificationService.VerificationView> verify(@RequestBody VerifyRequest request) {
        VerificationService.VerificationView view = verificationFacade.verify(
                request.baselineExecutionId(),
                request.candidateExecutionId(),
                request.changeRecordId()
        );
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, view);
    }

    @GetMapping("/verifications/{verificationId}")
    public ApiResponse<VerificationService.VerificationView> get(@PathVariable long verificationId) {
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                verificationFacade.getVerification(verificationId)
        );
    }

    public record RegisterChangeRequest(ChangeType changeType, String changeRef, String description) {
    }

    public record VerifyRequest(long baselineExecutionId, long candidateExecutionId, long changeRecordId) {
    }
}
