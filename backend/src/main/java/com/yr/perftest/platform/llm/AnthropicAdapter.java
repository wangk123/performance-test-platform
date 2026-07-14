package com.yr.perftest.platform.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class AnthropicAdapter implements LlmAdapter {
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnthropicAdapter() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    AnthropicAdapter(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> listModels(String baseUrl, String apiKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(join(baseUrl, "/v1/models")))
                .timeout(Duration.ofSeconds(30))
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new LlmValidationException("list models failed: HTTP " + response.statusCode() + " " + response.body());
        }
        JsonNode data = objectMapper.readTree(response.body()).path("data");
        List<String> ids = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode item : data) {
                String id = item.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    @Override
    public LlmChatResult chat(String baseUrl, String apiKey, String modelName, List<LlmChatMessage> messages) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", modelName);
        payload.put("max_tokens", 256);
        ArrayNode msgNode = payload.putArray("messages");
        for (LlmChatMessage message : messages) {
            ObjectNode item = msgNode.addObject();
            item.put("role", message.role());
            item.put("content", message.content());
        }
        String rawRequest = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(URI.create(join(baseUrl, "/v1/messages")))
                .timeout(Duration.ofSeconds(60))
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(rawRequest))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String rawResponse = response.body();
        if (response.statusCode() >= 400) {
            throw new LlmValidationException("chat failed: HTTP " + response.statusCode() + " " + rawResponse);
        }
        JsonNode root = objectMapper.readTree(rawResponse);
        String content = "";
        JsonNode contentNode = root.path("content");
        if (contentNode.isArray()) {
            for (JsonNode block : contentNode) {
                if ("text".equals(block.path("type").asText())) {
                    content = block.path("text").asText("");
                    break;
                }
            }
        }
        JsonNode usage = root.path("usage");
        Integer prompt = usage.path("input_tokens").isMissingNode() ? null : usage.path("input_tokens").asInt();
        Integer completion = usage.path("output_tokens").isMissingNode() ? null : usage.path("output_tokens").asInt();
        Integer total = prompt == null || completion == null ? null : prompt + completion;
        return new LlmChatResult(content, prompt, completion, total, rawRequest, rawResponse);
    }

    private static String join(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (base.endsWith("/v1") && path.startsWith("/v1/")) {
            return base.substring(0, base.length() - 3) + path;
        }
        return base + path;
    }
}
