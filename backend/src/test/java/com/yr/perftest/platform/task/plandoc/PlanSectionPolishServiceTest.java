package com.yr.perftest.platform.task.plandoc;

import com.sun.net.httpserver.HttpServer;
import com.yr.perftest.platform.llm.LlmApiType;
import com.yr.perftest.platform.llm.LlmCallRecordService;
import com.yr.perftest.platform.llm.LlmCallScene;
import com.yr.perftest.platform.llm.LlmCallStatus;
import com.yr.perftest.platform.llm.LlmModelService;
import com.yr.perftest.platform.llm.LlmProviderService;
import com.yr.perftest.platform.llm.LlmModel;
import com.yr.perftest.platform.llm.LlmProvider;
import com.yr.perftest.platform.llm.PersistentModelCallRecord;
import com.yr.perftest.platform.llm.PersistentModelCallRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-polish-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanSectionPolishServiceTest {
    @Autowired
    private PlanSectionPolishService polishService;
    @Autowired
    private LlmProviderService providerService;
    @Autowired
    private LlmModelService modelService;
    @Autowired
    private LlmCallRecordService callRecordService;
    @Autowired
    private PersistentModelCallRecordRepository callRecordRepository;

    private HttpServer server;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] raw = exchange.getRequestBody().readAllBytes();
            lastRequestBody.set(new String(raw, StandardCharsets.UTF_8));
            byte[] body = """
                    {"choices":[{"message":{"content":"```markdown\\n# 润色后的标题\\n\\n润色后的正文。\\n```"}}],"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void seedDefaultModel() {
        LlmProvider provider = providerService.create(new LlmProviderService.CreateProviderRequest(
                "Local", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", null, "sk-key", true, true));
        LlmModel model = modelService.create(new LlmModelService.CreateModelRequest(
                provider.id(), "m1", null, LlmApiType.OPENAI, true));
        modelService.setDefault(model.id());
    }

    @Test
    void polishesSectionViaDefaultModelAndStripsCodeFence() {
        seedDefaultModel();

        PlanSectionPolishService.PolishResult result = polishService.polish(
                "二、测试目的", "验证系统能撑住大促流量。", "alice");

        assertThat(result.content()).isEqualTo("# 润色后的标题\n\n润色后的正文。");
        assertThat(result.promptVersion()).isEqualTo(PlanSectionPolishService.PROMPT_VERSION);
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.callRecordId()).isPositive();

        String sent = lastRequestBody.get();
        assertThat(sent).contains("性能测试计划");
        assertThat(sent).contains("二、测试目的");
        assertThat(sent).contains("验证系统能撑住大促流量。");

        PersistentModelCallRecord record = callRecordRepository.findById(result.callRecordId()).orElseThrow();
        assertThat(record.getScene()).isEqualTo(LlmCallScene.PLAN_POLISH);
        assertThat(record.getStatus()).isEqualTo(LlmCallStatus.SUCCESS);
        assertThat(record.getTriggeredBy()).isEqualTo("alice");
    }

    @Test
    void rejectsWhenNoDefaultModelConfigured() {
        assertThatThrownBy(() -> polishService.polish("标题", "正文", "alice"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("默认模型");
    }

    @Test
    void wrapsUpstreamFailureIntoPlanValidationException() {
        seedDefaultModel();
        server.stop(0);

        assertThatThrownBy(() -> polishService.polish("标题", "正文", "alice"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("AI 润色失败");

        assertThat(callRecordService.page(null, null, LlmCallScene.PLAN_POLISH, LlmCallStatus.FAILED, 0, 10)
                .getContent()).hasSize(1);
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> polishService.polish("标题", "   ", "alice"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("内容为空");
    }
}
