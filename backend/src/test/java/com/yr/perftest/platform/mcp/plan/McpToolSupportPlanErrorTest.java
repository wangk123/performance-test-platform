package com.yr.perftest.platform.mcp.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.mcp.McpToolSupport;
import com.yr.perftest.platform.project.ProjectValidationException;
import com.yr.perftest.platform.task.plandoc.PlanPhase;
import com.yr.perftest.platform.task.plandoc.PlanRevisionConflictException;
import com.yr.perftest.platform.task.plandoc.PlanStateException;
import com.yr.perftest.platform.task.plandoc.PlanStatus;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 错误通道（P0-2①）：PLAN_* 错误码与 details 负载透传（spec §6.1）。
 */
class McpToolSupportPlanErrorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void revisionConflictCarriesCodeAndDetails() throws Exception {
        JsonNode body = text(McpToolSupport.failure(objectMapper,
                new PlanRevisionConflictException("计划文档已被修改", 5, "# 一、背景\n平台全文")));
        assertThat(body.at("/error/code").asText()).isEqualTo("PLAN_REVISION_CONFLICT");
        assertThat(body.at("/error/details/currentRevision").asInt()).isEqualTo(5);
        assertThat(body.at("/error/details/serverMarkdown").asText()).isEqualTo("# 一、背景\n平台全文");
    }

    @Test
    void planStateCarriesPhaseStatusAndAllowedActions() throws Exception {
        JsonNode body = text(McpToolSupport.failure(objectMapper, new PlanStateException(
                "PLAN_STATE：仅草稿可编辑", PlanPhase.REVIEW, PlanStatus.IN_REVIEW,
                java.util.List.of("WITHDRAW"))));
        assertThat(body.at("/error/code").asText()).isEqualTo("PLAN_STATE");
        assertThat(body.at("/error/details/phase").asText()).isEqualTo("REVIEW");
        assertThat(body.at("/error/details/status").asText()).isEqualTo("IN_REVIEW");
        assertThat(body.at("/error/details/allowedActions/0").asText()).isEqualTo("WITHDRAW");
    }

    @Test
    void messageOnlyPlanExceptionsMapToStableCodes() throws Exception {
        assertThat(text(McpToolSupport.failure(objectMapper,
                new com.yr.perftest.platform.task.plandoc.PlanValidationException("PLAN_INVALID：task plan does not exist")))
                .at("/error/code").asText()).isEqualTo("PLAN_INVALID");
        assertThat(text(McpToolSupport.failure(objectMapper,
                new com.yr.perftest.platform.task.plandoc.PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员")))
                .at("/error/code").asText()).isEqualTo("PLAN_ACCESS_DENIED");
        assertThat(text(McpToolSupport.failure(objectMapper,
                new ProjectValidationException("project does not exist")))
                .at("/error/code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void errorWithoutDetailsKeepsLegacyShape() throws Exception {
        JsonNode body = text(McpToolSupport.error(objectMapper, "VALIDATION_FAILED", "bad input"));
        assertThat(body.at("/error/code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.has("details")).isFalse();
        assertThat(body.at("/error").size()).isEqualTo(2);
    }

    private JsonNode text(McpSchema.CallToolResult result) throws Exception {
        // CallToolResult 的文本内容即封套 JSON
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        return objectMapper.readTree(content);
    }
}
