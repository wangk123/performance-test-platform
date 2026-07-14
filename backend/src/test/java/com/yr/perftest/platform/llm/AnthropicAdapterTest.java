package com.yr.perftest.platform.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicAdapterTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastKey = new AtomicReference<>();
    private final AtomicReference<String> lastVersion = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            lastKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            lastVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            byte[] body = """
                    {"data":[{"id":"claude-sonnet"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/v1/messages", exchange -> {
            lastKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] body = """
                    {"content":[{"type":"text","text":"hi"}],"usage":{"input_tokens":2,"output_tokens":1}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void listsAndChats() throws Exception {
        AnthropicAdapter adapter = new AnthropicAdapter();
        assertThat(adapter.listModels(baseUrl, "sk-ant")).containsExactly("claude-sonnet");
        assertThat(lastKey.get()).isEqualTo("sk-ant");
        assertThat(lastVersion.get()).isNotBlank();

        LlmChatResult result = adapter.chat(baseUrl, "sk-ant", "claude-sonnet", List.of(
                new LlmChatMessage("user", "hello")
        ));
        assertThat(result.content()).isEqualTo("hi");
        assertThat(result.promptTokens()).isEqualTo(2);
        assertThat(result.completionTokens()).isEqualTo(1);
    }
}
