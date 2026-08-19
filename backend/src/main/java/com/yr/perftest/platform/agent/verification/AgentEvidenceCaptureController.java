package com.yr.perftest.platform.agent.verification;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.facade.VerificationFacade;
import com.yr.perftest.platform.verification.CaptureImpact;
import com.yr.perftest.platform.verification.EvidenceCaptureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 补充取证 agent 面入口（T9）：创建（预检声明）→ 审批/拒绝 → 执行 → 查询。
 */
@RestController
@RequestMapping("/api/agent/evidence-captures")
public class AgentEvidenceCaptureController {
    private final VerificationFacade verificationFacade;

    public AgentEvidenceCaptureController(VerificationFacade verificationFacade) {
        this.verificationFacade = verificationFacade;
    }

    @PostMapping
    public ApiResponse<EvidenceCaptureService.CaptureView> create(@RequestBody CreateCaptureRequest request) {
        EvidenceCaptureService.CaptureView view = verificationFacade.createCapture(
                request.executionId(),
                request.purpose(),
                request.impactLevel(),
                request.costNote()
        );
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, view);
    }

    @PostMapping("/{captureId}/approve")
    public ApiResponse<EvidenceCaptureService.CaptureView> approve(@PathVariable long captureId) {
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                verificationFacade.approveCapture(captureId)
        );
    }

    @PostMapping("/{captureId}/reject")
    public ApiResponse<EvidenceCaptureService.CaptureView> reject(@PathVariable long captureId) {
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                verificationFacade.rejectCapture(captureId)
        );
    }

    @PostMapping("/{captureId}/execute")
    public ApiResponse<EvidenceCaptureService.CaptureView> execute(@PathVariable long captureId) {
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                verificationFacade.executeCapture(captureId)
        );
    }

    @GetMapping("/{captureId}")
    public ApiResponse<EvidenceCaptureService.CaptureView> get(@PathVariable long captureId) {
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                verificationFacade.getCapture(captureId)
        );
    }

    public record CreateCaptureRequest(
            long executionId,
            String purpose,
            CaptureImpact impactLevel,
            String costNote
    ) {
    }
}
