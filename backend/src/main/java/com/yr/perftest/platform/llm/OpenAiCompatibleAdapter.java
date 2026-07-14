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
public class OpenAiCompatibleAdapter implements LlmAdapter {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleAdapter() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    OpenAiCompatibleAdapter(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> listModels(String baseUrl, String apiKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(join(baseUrl, "/models")))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
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
        ArrayNode msgNode = payload.putArray("messages");
        for (LlmChatMessage message : messages) {
            ObjectNode item = msgNode.addObject();
            item.put("role", message.role());
            item.put("content", message.content());
        }
        String rawRequest = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(URI.create(join(baseUrl, "/chat/completions")))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(rawRequest))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String rawResponse = response.body();
        if (response.statusCode() >= 400) {
            throw new LlmValidationException("chat failed: HTTP " + response.statusCode() + " " + rawResponse);
        }
        JsonNode root = objectMapper.readTree(rawResponse);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        JsonNode usage = root.path("usage");
        Integer prompt = usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt();
        Integer completion = usage.path("completion_tokens").isMissingNode() ? null : usage.path("completion_tokens").asInt();
        Integer total = usage.path("total_tokens").isMissingNode() ? null : usage.path("total_tokens").asInt();
        return new LlmChatResult(content, prompt, completion, total, rawRequest, rawResponse);
    }

    private static String join(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + path;
    }
}
