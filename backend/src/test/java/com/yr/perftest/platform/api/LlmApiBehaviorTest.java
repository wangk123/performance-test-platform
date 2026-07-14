package com.yr.perftest.platform.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:llm-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LlmApiBehaviorTest {
    @Autowired
    private MockMvc mockMvc;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            byte[] body = """
                    {"data":[{"id":"deepseek-v4-flash"},{"id":"other"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = """
                    {"choices":[{"message":{"content":"pong"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
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
    void coversProviderModelAvailableAndCallFlow() throws Exception {
        mockMvc.perform(post("/api/llm/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"DeepSeek",
                                  "baseUrl":"%s",
                                  "apiKey":"sk-secret",
                                  "enabled":true,
                                  "storeBodyDefault":false
                                }
                                """.formatted(baseUrl)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKeyConfigured", is(true)))
                .andExpect(jsonPath("$.name", is("DeepSeek")));

        mockMvc.perform(get("/api/llm/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].apiKeyConfigured", is(true)))
                .andExpect(jsonPath("$[0].baseUrl", is(baseUrl)));

        mockMvc.perform(post("/api/llm/providers/1/fetch-models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models", hasSize(2)))
                .andExpect(jsonPath("$.models[0]", is("deepseek-v4-flash")));

        mockMvc.perform(post("/api/llm/providers/1/import-models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apiType":"OPENAI",
                                  "models":[{"modelName":"deepseek-v4-flash"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(post("/api/llm/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Other",
                                  "baseUrl":"%s",
                                  "apiKey":"sk-other",
                                  "enabled":true
                                }
                                """.formatted(baseUrl)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/llm/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":2,"modelName":"deepseek-v4-flash"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiType", is("OPENAI")))
                .andExpect(jsonPath("$.apiTypes[0]", is("OPENAI")));

        mockMvc.perform(put("/api/llm/models/1/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault", is(true)));

        mockMvc.perform(get("/api/llm/available-models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].models[0].modelName", is("deepseek-v4-flash")))
                .andExpect(jsonPath("$[1].models[0].modelName", is("deepseek-v4-flash")));

        mockMvc.perform(post("/api/llm/providers/1/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(get("/api/llm/call-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].scene", is("TEST_CONNECTION")))
                .andExpect(jsonPath("$.content[0].status", is("SUCCESS")))
                .andExpect(jsonPath("$.content[0].requestBody", nullValue()));

        mockMvc.perform(delete("/api/llm/providers/1"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/llm/providers/1?cascade=true"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/llm/models?providerId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/llm/call-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));
    }
}
