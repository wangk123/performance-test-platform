package com.yr.perftest.platform.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionServiceTest {
    private final RedactionService service = new RedactionService(new ObjectMapper());

    @Test
    void redactsSensitiveJsonKeysRecursively() throws Exception {
        String json = "{\"password\":\"top-secret\",\"user\":{\"token\":\"t-1\"},"
                + "\"items\":[{\"apiKey\":\"k-1\"}],\"ok\":\"visible\",\"count\":7}";

        JsonNode redacted = new ObjectMapper().readTree(service.redactJsonBytes(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(redacted.get("password").asText()).isEqualTo(RedactionService.REDACTED);
        assertThat(redacted.get("user").get("token").asText()).isEqualTo(RedactionService.REDACTED);
        assertThat(redacted.get("items").get(0).get("apiKey").asText()).isEqualTo(RedactionService.REDACTED);
        assertThat(redacted.get("ok").asText()).isEqualTo("visible");
        assertThat(redacted.get("count").asInt()).isEqualTo(7);
    }

    @Test
    void redactsSensitiveKeyRegardlessOfValueType() throws Exception {
        String json = "{\"password\":12345,\"nested\":{\"secret\":{\"a\":1}}}";

        JsonNode redacted = new ObjectMapper().readTree(service.redactJsonBytes(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(redacted.get("password").asText()).isEqualTo(RedactionService.REDACTED);
        assertThat(redacted.get("nested").get("secret").asText()).isEqualTo(RedactionService.REDACTED);
    }

    @Test
    void redactsBearerTokensJwtAndKeyValueAssignmentsInText() {
        String text = "GET /x Authorization: Bearer abc.def123\n"
                + "token=sekret123&ok=1\n"
                + "jwt: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature\n"
                + "Cookie: session=abc; Path=/";

        String redacted = service.redactText(text);

        assertThat(redacted).doesNotContain("abc.def123");
        assertThat(redacted).contains("Authorization:***");
        assertThat(redacted).contains("token=***");
        assertThat(redacted).doesNotContain("sekret123");
        assertThat(redacted).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(redacted).contains("Cookie:***");
        assertThat(redacted).doesNotContain("session=abc");
    }

    @Test
    void leavesNonSensitiveContentUntouched() {
        String text = "label=checkout&statusCode=500&message=timeout after 3012 ms";

        String redacted = service.redactText(text);

        assertThat(redacted).isEqualTo(text);
    }

    @Test
    void nonJsonContentFallsBackToTextRedaction() {
        byte[] redacted = service.redactJsonBytes("not json at all token=abc".getBytes(StandardCharsets.UTF_8));

        assertThat(new String(redacted, StandardCharsets.UTF_8)).contains("token=***");
    }

    @Test
    void sensitiveKeyDetectionIsCaseInsensitiveAndIgnoresSeparators() {
        assertThat(service.isSensitiveKey("Password")).isTrue();
        assertThat(service.isSensitiveKey("api_key")).isTrue();
        assertThat(service.isSensitiveKey("X-Api-Key")).isTrue();
        assertThat(service.isSensitiveKey("tickCount")).isFalse();
        assertThat(service.isSensitiveKey("throughput")).isFalse();
    }
}
