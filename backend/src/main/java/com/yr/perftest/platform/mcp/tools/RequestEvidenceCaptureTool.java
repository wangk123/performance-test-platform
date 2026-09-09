package com.yr.perftest.platform.mcp.tools;

import com.yr.perftest.platform.facade.VerificationFacade;
import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import com.yr.perftest.platform.verification.CaptureImpact;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 补充取证工具：声明目的/影响/成本发起取证请求（进入人工审批流）。
 */
@Component
public class RequestEvidenceCaptureTool implements McpTool {
    private final VerificationFacade verificationFacade;

    public RequestEvidenceCaptureTool(VerificationFacade verificationFacade) {
        this.verificationFacade = verificationFacade;
    }

    @Override
    public String name() {
        return "request_evidence_capture";
    }

    @Override
    public String title() {
        return "申请补充取证";
    }

    @Override
    public String description() {
        return "为已结束的执行申请补充取证：声明目的、影响级别与成本说明；"
                + "经人工审批后才会采集有界快照。";
    }

    @Override
    public String stage() {
        return "CAPTURE";
    }

    @Override
    public boolean requiresWriteScope() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AnalyzeExecutionTool.schema(
                "补充取证申请",
                Map.of(
                        "executionId", Map.of("type", "integer"),
                        "purpose", Map.of("type", "string", "description", "本次取证的目的"),
                        "impactLevel", Map.of("type", "string", "description", "影响级别：NONE / LOW / MEDIUM / HIGH"),
                        "costNote", Map.of("type", "string", "description", "成本说明；impact 为 NONE 时可省略")
                ),
                List.of("executionId", "purpose")
        );
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        long executionId = InspectExecutionTool.requiredLong(args, "executionId");
        String purpose = args.get("purpose") == null ? null : String.valueOf(args.get("purpose"));
        CaptureImpact impact = null;
        Object impactLevel = args.get("impactLevel");
        if (impactLevel != null && !String.valueOf(impactLevel).isBlank()) {
            impact = CaptureImpact.valueOf(String.valueOf(impactLevel).toUpperCase());
        }
        String costNote = args.get("costNote") == null ? null : String.valueOf(args.get("costNote"));
        return verificationFacade.createCapture(executionId, purpose, impact, costNote);
    }
}
