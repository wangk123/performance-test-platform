package com.yr.perftest.platform.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏：在 agent 面输出边界前强制执行（T10）。
 * 纯函数逻辑，无随机性与外部 IO，输出可重复。
 */
public class RedactionService {
    public static final String REDACTED = "***";

    private static final List<String> SENSITIVE_FRAGMENTS = List.of(
            "password", "passwd", "pwd", "secret", "token", "apikey",
            "authorization", "cookie", "credential", "privatekey",
            "sshpassword", "accesskey", "sessionid"
    );

    private static final Pattern BEARER =
            Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=\\-]+");
    private static final Pattern JWT =
            Pattern.compile("\\beyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]*");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|token|api[_-]?key|authorization|cookie|access[_-]?key|credential|session[_-]?id)"
                    + "\\s*([:=])\\s*([\"']?)([^\\s\"',;&]+)");
    private static final Pattern SENSITIVE_HEADER_LINE =
            Pattern.compile("(?im)^\\s*(authorization|cookie|set-cookie)\\s*:.*$");

    private final ObjectMapper objectMapper;

    public RedactionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase().replaceAll("[^a-z0-9]", "");
        for (String fragment : SENSITIVE_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    public String redactText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String redacted = SENSITIVE_HEADER_LINE.matcher(text)
                .replaceAll(match -> {
                    String header = match.group(1);
                    return header + ": " + REDACTED;
                });
        redacted = BEARER.matcher(redacted).replaceAll("Bearer " + REDACTED);
        redacted = JWT.matcher(redacted).replaceAll(REDACTED);
        redacted = KEY_VALUE.matcher(redacted).replaceAll(match ->
                match.group(1) + match.group(2) + match.group(3) + REDACTED);
        return redacted;
    }

    public byte[] redactJsonBytes(byte[] body) {
        String json = new String(body, StandardCharsets.UTF_8);
        try {
            JsonNode tree = objectMapper.readTree(json);
            return objectMapper.writeValueAsBytes(redactTree(tree));
        } catch (Exception exception) {
            return redactText(json).getBytes(StandardCharsets.UTF_8);
        }
    }

    public JsonNode redactTree(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveKey(field.getKey())) {
                    field.setValue(objectMapper.getNodeFactory().textNode(REDACTED));
                } else {
                    field.setValue(redactTree(field.getValue()));
                }
            }
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int index = 0; index < array.size(); index++) {
                array.set(index, redactTree(array.get(index)));
            }
            return array;
        }
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(redactText(node.asText()));
        }
        return node;
    }
}
