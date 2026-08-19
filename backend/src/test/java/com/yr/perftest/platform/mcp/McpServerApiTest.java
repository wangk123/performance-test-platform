package com.yr.perftest.platform.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.distributed.ExecutionNodeRole;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRecord;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Streamable HTTP MCP Server 端到端测试（T12）：机器身份接入、任务型工具发现、
 * 只读 scope 越权拦截、写操作幂等。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:mcp-server-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class McpServerApiTest {
    private static final String PROTOCOL_VERSION = "2025-06-18";

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentExecutionNodeRepository nodeRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private String opsApiKey;
    private String readonlyApiKey;

    @BeforeEach
    void setUp() throws Exception {
        String adminToken = loginToken();
        opsApiKey = issueApiKey(adminToken, "ops");
        readonlyApiKey = issueApiKey(adminToken, "readonly");
    }

    @Test
    void rejectsRequestsWithoutIdentity() throws Exception {
        HttpResponse<String> response = rpc("initialize",
                "{\"protocolVersion\":\"" + PROTOCOL_VERSION + "\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}",
                null, null);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void initializeAndListToolsOverStreamableHttp() throws Exception {
        RpcSession session = initialize(opsApiKey);

        HttpResponse<String> listed = rpc("tools/list", "{}", opsApiKey, session.sessionId());
        assertThat(listed.statusCode()).isEqualTo(200);
        JsonNode body = parseRpc(listed.body());
        JsonNode errorNode = body.get("error");
        assertThat(errorNode == null || errorNode.isNull()).as("tools/list response: " + listed.body()).isTrue();
        List<String> names = new ArrayList<>();
        for (JsonNode tool : body.at("/result/tools")) {
            names.add(tool.get("name").asText());
        }
        assertThat(names).contains(
                "list_projects",
                "start_execution",
                "inspect_execution",
                "analyze_execution",
                "collect_evidence",
                "request_evidence_capture",
                "register_change",
                "verify_change"
        );
    }

    @Test
    void readonlyScopeCannotInvokeWriteTool() throws Exception {
        RpcSession session = initialize(readonlyApiKey);

        HttpResponse<String> response = rpc(
                "tools/call",
                "{\"name\":\"start_execution\",\"arguments\":{\"scenarioId\":1}}",
                readonlyApiKey,
                session.sessionId()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = parseRpc(response.body());
        assertThat(body.at("/result/isError").asBoolean()).isTrue();
        assertThat(body.at("/result/content/0/text").asText()).contains("ACCESS_DENIED");
    }

    @Test
    void startExecutionToolIsIdempotent() throws Exception {
        long scenarioId = scenarioWithControllerNode();
        RpcSession session = initialize(opsApiKey);
        String arguments = "{\"scenarioId\":" + scenarioId
                + ",\"executionName\":\"mcp-run\",\"idempotencyKey\":\"mcp-idem-1\"}";

        HttpResponse<String> first = rpc("tools/call",
                "{\"name\":\"start_execution\",\"arguments\":" + arguments + "}",
                opsApiKey, session.sessionId());
        assertThat(first.statusCode()).isEqualTo(200);
        JsonNode firstBody = parseRpc(first.body());
        assertThat(firstBody.at("/result/isError").asBoolean())
                .as("first start response: " + first.body())
                .isFalse();
        long executionId = firstBody.at("/result/content/0/text")
                .asText().isEmpty() ? -1 : readExecutionId(firstBody);
        assertThat(executionId).isPositive();
        assertThat(readReplayed(firstBody)).isFalse();

        HttpResponse<String> second = rpc("tools/call",
                "{\"name\":\"start_execution\",\"arguments\":" + arguments + "}",
                opsApiKey, session.sessionId());
        assertThat(second.statusCode()).isEqualTo(200);
        JsonNode secondBody = parseRpc(second.body());
        assertThat(readExecutionId(secondBody)).isEqualTo(executionId);
        assertThat(readReplayed(secondBody)).isTrue();
    }

    @Test
    void listProjectsToolReturnsFacadeData() throws Exception {
        RpcSession session = initialize(opsApiKey);

        HttpResponse<String> response = rpc(
                "tools/call",
                "{\"name\":\"list_projects\",\"arguments\":{}}",
                opsApiKey,
                session.sessionId()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = parseRpc(response.body());
        assertThat(body.at("/result/isError").asBoolean())
                .as("list projects response: " + response.body())
                .isFalse();
        JsonNode payload = objectMapper.readTree(body.at("/result/content/0/text").asText());
        assertThat(payload.at("/data/items").isArray()).isTrue();
    }

    private RpcSession initialize(String apiKey) throws Exception {
        HttpResponse<String> response = rpc("initialize",
                "{\"protocolVersion\":\"" + PROTOCOL_VERSION + "\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}",
                apiKey, null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = parseRpc(response.body());
        JsonNode errorNode = body.get("error");
        assertThat(errorNode == null || errorNode.isNull()).as("initialize succeeds: " + response.body()).isTrue();
        assertThat(body.at("/result/serverInfo/name").asText()).isEqualTo("performance-test-platform");
        return new RpcSession(response.headers().firstValue("Mcp-Session-Id").orElse(null));
    }

    private JsonNode parseRpc(String body) throws Exception {
        String json = body;
        if (body != null && body.startsWith("{")) {
            json = body;
        } else {
            // SSE（text/event-stream）：取最后一个非空 data 段作为 JSON-RPC 消息
            String[] lines = body.split("\\r?\\n");
            StringBuilder lastData = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("data:")) {
                    lastData = new StringBuilder(line.substring("data:".length()).stripLeading());
                }
            }
            json = lastData.toString();
        }
        return objectMapper.readTree(json);
    }

    private HttpResponse<String> rpc(String method, String params, String apiKey, String sessionId)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}"));
        if (apiKey != null) {
            builder.header("X-API-Key", apiKey);
        }
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private long readExecutionId(JsonNode rpcBody) throws Exception {
        JsonNode payload = objectMapper.readTree(rpcBody.at("/result/content/0/text").asText());
        return payload.at("/data/execution/executionId").asLong();
    }

    private boolean readReplayed(JsonNode rpcBody) throws Exception {
        JsonNode payload = objectMapper.readTree(rpcBody.at("/result/content/0/text").asText());
        return payload.at("/data/execution/replayed").asBoolean();
    }

    private long scenarioWithControllerNode() {
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0));
        PersistentExecutionNodeRecord node = nodeRepository.save(new PersistentExecutionNodeRecord(
                "node-1", "10.0.0.1", 22, "ops", "/keys/id", ExecutionNodeRole.CONTROLLER, "/tmp/perf"));
        scenario.updateProfile("scenario-a", 1L, "{}", node.getId(), null, null, null);
        return scenarioRepository.save(scenario).getId();
    }

    private String issueApiKey(String adminToken, String scope) throws Exception {
        MvcResult issued = mockMvc.perform(post("/api/agent-api-keys")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"" + scope + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(issued.getResponse().getContentAsString()).get("plainKey").asText();
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private record RpcSession(String sessionId) {
    }
}
