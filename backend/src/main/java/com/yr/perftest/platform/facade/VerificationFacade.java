package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.verification.ChangeType;
import com.yr.perftest.platform.verification.CaptureImpact;
import com.yr.perftest.platform.verification.EvidenceCaptureService;
import com.yr.perftest.platform.verification.VerificationService;
import org.springframework.stereotype.Service;

/**
 * 取证/验证 agent 面入口（T9）：主体校验 + 委托，取证与验证各自独立成组，语义不混用。
 */
@Service
public class VerificationFacade {
    private final FacadeGuard guard;
    private final EvidenceCaptureService captureService;
    private final VerificationService verificationService;

    public VerificationFacade(
            FacadeGuard guard,
            EvidenceCaptureService captureService,
            VerificationService verificationService
    ) {
        this.guard = guard;
        this.captureService = captureService;
        this.verificationService = verificationService;
    }

    public EvidenceCaptureService.CaptureView createCapture(
            long executionId,
            String purpose,
            CaptureImpact impactLevel,
            String costNote
    ) {
        return guard.requirePrincipal(() ->
                captureService.create(executionId, purpose, impactLevel, costNote, currentPrincipal()));
    }

    public EvidenceCaptureService.CaptureView approveCapture(long captureId) {
        return guard.requirePrincipal(() -> captureService.approve(captureId, currentPrincipal()));
    }

    public EvidenceCaptureService.CaptureView rejectCapture(long captureId) {
        return guard.requirePrincipal(() -> captureService.reject(captureId, currentPrincipal()));
    }

    public EvidenceCaptureService.CaptureView executeCapture(long captureId) {
        return guard.requirePrincipal(() -> captureService.execute(captureId, currentPrincipal()));
    }

    public EvidenceCaptureService.CaptureView getCapture(long captureId) {
        return guard.requirePrincipal(() -> captureService.get(captureId, currentPrincipal()));
    }

    public VerificationService.ChangeRecordView registerChange(
            ChangeType changeType,
            String changeRef,
            String description
    ) {
        return guard.requirePrincipal(() ->
                verificationService.registerChange(changeType, changeRef, description, currentPrincipal()));
    }

    public VerificationService.VerificationView verify(
            long baselineExecutionId,
            long candidateExecutionId,
            long changeRecordId
    ) {
        return guard.requirePrincipal(() ->
                verificationService.verify(baselineExecutionId, candidateExecutionId, changeRecordId, currentPrincipal()));
    }

    public VerificationService.VerificationView getVerification(long verificationId) {
        return guard.requirePrincipal(() -> verificationService.get(verificationId, currentPrincipal()));
    }

    private Principal currentPrincipal() {
        return guard.context().principal();
    }
}
