package com.yr.perftest.platform.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MCP 工具目录端点测试（P0-2 ②）：目录与注册表严格一致（单一事实源），
 * 固定规范 stage 序列（含预留 PLAN），未登录 401。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mcp-directory-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class McpDirectoryControllerTest {
    private static final List<String> STAGE_ORDER =
            List.of("PLAN", "NAVIGATE", "DESIGN", "OBSERVE", "DIAGNOSE", "VERIFY", "CAPTURE");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private McpToolRegistry registry;

    @Test
    void rejectsUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/mcp/tools")).andExpect(status().isUnauthorized());
    }

    @Test
    void directoryMirrorsRegistryWithCanonicalStageOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/mcp/tools")
                        .header("Authorization", "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.at("/server/name").asText()).isEqualTo("performance-test-platform");
        assertThat(body.at("/server/endpoint").asText()).isEqualTo("/mcp");
        assertThat(body.at("/server/toolCount").asInt()).isEqualTo(registry.all().size());

        List<String> stages = new ArrayList<>();
        body.at("/stages").forEach(node -> stages.add(node.asText()));
        assertThat(stages).containsExactlyElementsOf(STAGE_ORDER);

        List<String> names = new ArrayList<>();
        body.at("/tools").forEach(node -> names.add(node.get("name").asText()));
        assertThat(names).containsExactlyInAnyOrderElementsOf(
                registry.all().stream().map(McpTool::name).toList());

        // tools 按规范 stage 序列排序（组内顺序不作断言）
        int previous = -1;
        for (JsonNode tool : body.at("/tools")) {
            int index = STAGE_ORDER.indexOf(tool.get("stage").asText());
            assertThat(index).isGreaterThanOrEqualTo(previous);
            previous = index;
        }

        // 字段口径：两态 status 全 ENABLED（注册表 v1 无启停标志）、usageExample 默认空串、schema 透传
        for (JsonNode tool : body.at("/tools")) {
            assertThat(tool.get("status").asText()).isEqualTo("ENABLED");
            assertThat(tool.get("usageExample").asText()).isEmpty();
            assertThat(tool.get("requiresWriteScope").isBoolean()).isTrue();
            assertThat(tool.get("inputSchema").isObject()).isTrue();
        }

        JsonNode listProjects = toolByName(body, "list_projects");
        assertThat(listProjects.at("/inputSchema/properties/includeArchived/type").asText())
                .isEqualTo("boolean");
        assertThat(listProjects.get("requiresWriteScope").asBoolean()).isFalse();
        assertThat(toolByName(body, "start_execution").get("requiresWriteScope").asBoolean()).isTrue();
    }

    private JsonNode toolByName(JsonNode body, String name) {
        for (JsonNode tool : body.at("/tools")) {
            if (name.equals(tool.get("name").asText())) {
                return tool;
            }
        }
        throw new AssertionError("tool not found in directory: " + name);
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
