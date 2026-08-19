package com.yr.perftest.platform.gitlog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yr.perftest.platform.llm.LlmApiType;
import com.yr.perftest.platform.llm.LlmModel;
import com.yr.perftest.platform.llm.LlmModelService;
import com.yr.perftest.platform.llm.LlmProvider;
import com.yr.perftest.platform.llm.LlmProviderService;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:git-log-ai-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.storage.root=build/tmp/git-log-ai-storage"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class GitLogAiApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LlmProviderService llmProviderService;

    @Autowired
    private LlmModelService llmModelService;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    private String adminToken;
    private long scenarioId;
    private HttpServer mockLlmServer;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginToken();
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        scenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0)).getId();
    }

    @AfterEach
    void tearDown() {
        if (mockLlmServer != null) {
            mockLlmServer.stop(0);
        }
    }

    @Test
    void gitCommitImportAndCodeBinding() throws Exception {
        Path repoDir = Files.createTempDirectory("local-git-repo");
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            Files.writeString(repoDir.resolve("app.txt"), "v1\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("first commit").call();
            Files.writeString(repoDir.resolve("app.txt"), "v2\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("second commit").call();
        }

        MvcResult created = mockMvc.perform(post("/api/projects/1/git-repositories")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"local-repo\",\"url\":\"file://" + repoDir + "\",\"authType\":\"NONE\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long repositoryId = objectMapper.readTree(created.getResponse().getContentAsString()).get("repositoryId").asLong();

        MvcResult imported = mockMvc.perform(post("/api/git-repositories/" + repositoryId + "/import-commits")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branch\":\"master\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitCount", is(2)))
                .andExpect(jsonPath("$.latest.message", is("second commit")))
                .andReturn();
        String latestHash = objectMapper.readTree(imported.getResponse().getContentAsString())
                .at("/latest/commitHash").asText();
        assertThat(latestHash).hasSize(40);

        mockMvc.perform(get("/api/git-repositories/" + repositoryId + "/commits")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("branch", "master"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));

        mockMvc.perform(put("/api/scenarios/" + scenarioId + "/code-binding")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryId\":" + repositoryId
                                + ",\"branch\":\"master\",\"commitId\":\"" + latestHash + "\",\"remark\":\"perf run\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitId", is(latestHash)));

        mockMvc.perform(get("/api/scenarios/" + scenarioId + "/code-binding")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryId", is((int) repositoryId)))
                .andExpect(jsonPath("$.branch", is("master")));
    }

    @Test
    void logArtifactUploadAndSearch() throws Exception {
        long executionId = createExecution();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "app.log",
                MediaType.TEXT_PLAIN_VALUE,
                "line one\nERROR timeout at step login\nline three\n".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/executions/" + executionId + "/logs")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User", "admin"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName", is("app.log")))
                .andExpect(jsonPath("$.indexStatus", is("INDEXED")));

        mockMvc.perform(get("/api/executions/" + executionId + "/logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));

        mockMvc.perform(get("/api/executions/" + executionId + "/logs/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", "timeout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].lineNo", is(2)))
                .andExpect(jsonPath("$[0].line", is("ERROR timeout at step login")));
    }

    @Test
    void aiAnalysisFailsCleanlyWithoutModel() throws Exception {
        long executionId = createExecution();

        mockMvc.perform(post("/api/executions/" + executionId + "/ai-analysis")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":999999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.promptVersion", is("v1")))
                .andExpect(jsonPath("$.result", notNullValue()));
    }

    @Test
    void aiAnalysisSucceedsWithMockProvider() throws Exception {
        mockLlmServer = startMockLlmServer();
        LlmProvider provider = llmProviderService.create(new LlmProviderService.CreateProviderRequest(
                "mock-provider",
                "http://127.0.0.1:" + mockLlmServer.getAddress().getPort() + "/v1",
                null,
                "test-key",
                true,
                false
        ));
        LlmModel model = llmModelService.create(new LlmModelService.CreateModelRequest(
                provider.id(), "mock-model", "mock-model", LlmApiType.OPENAI, true));
        long executionId = createExecution();

        mockMvc.perform(post("/api/executions/" + executionId + "/ai-analysis")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":" + model.id() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.result", is("mock analysis")))
                .andExpect(jsonPath("$.modelId", is((int) model.id())));
    }

    private HttpServer startMockLlmServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = ("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"mock analysis\"}}],"
                    + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private long createExecution() {
        return executionRepository.save(new PersistentScenarioExecutionRecord(scenarioId, "{}")).getId();
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }
}
