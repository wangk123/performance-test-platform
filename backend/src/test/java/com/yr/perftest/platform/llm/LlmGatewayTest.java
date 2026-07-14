package com.yr.perftest.platform.llm;

import com.sun.net.httpserver.HttpServer;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:llm-gateway-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LlmGatewayTest {
    @Autowired
    private LlmProviderService providerService;
    @Autowired
    private LlmModelService modelService;
    @Autowired
    private LlmGateway gateway;
    @Autowired
    private LlmCallRecordService callRecordService;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = """
                    {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void invokeWritesSuccessRecordWithoutApiKeyLeak() {
        LlmProvider provider = providerService.create(new LlmProviderService.CreateProviderRequest(
                "Local", baseUrl, null, "sk-secret-key", true, true));
        LlmModel model = modelService.create(new LlmModelService.CreateModelRequest(
                provider.id(), "m1", null, LlmApiType.OPENAI, true));

        LlmGateway.InvokeResult result = gateway.invoke(new LlmGateway.InvokeRequest(
                model.id(), LlmCallScene.TEST_CONNECTION, List.of(new LlmChatMessage("user", "ping")), null, "admin"));

        assertThat(result.success()).isTrue();
        LlmCallRecord record = callRecordService.page(provider.id(), model.id(), null, null, 0, 10).getContent().get(0);
        assertThat(record.status()).isEqualTo(LlmCallStatus.SUCCESS);
        assertThat(record.requestBody()).contains("ping");
        assertThat(record.requestBody()).doesNotContain("sk-secret-key");
        assertThat(record.responseBody()).contains("ok");
    }

    @Test
    void rejectsDisabledModel() {
        LlmProvider provider = providerService.create(new LlmProviderService.CreateProviderRequest(
                "Local", baseUrl, null, "sk", true, false));
        LlmModel model = modelService.create(new LlmModelService.CreateModelRequest(
                provider.id(), "m1", null, LlmApiType.OPENAI, false));

        assertThatThrownBy(() -> gateway.invoke(new LlmGateway.InvokeRequest(
                model.id(), LlmCallScene.TEST_CONNECTION, null, false, "admin")))
                .isInstanceOf(LlmValidationException.class)
                .hasMessageContaining("disabled");
    }
}
