package com.yr.perftest.platform.execution;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class HttpDebugService {
    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "connection",
            "content-length",
            "date",
            "expect",
            "from",
            "host",
            "upgrade",
            "via",
            "warning"
    );
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public HttpDebugResult execute(HttpDebugRequest request) {
        long startedAt = System.currentTimeMillis();
        String method = request.method().trim().toUpperCase(Locale.ROOT);
        Map<String, String> requestHeaders = new LinkedHashMap<>(request.headers());
        String requestBody = request.body();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(request.url()))
                    .timeout(Duration.ofMillis(request.timeoutMs()));
            requestHeaders.forEach((name, value) -> {
                if (!isRestrictedHeader(name)) {
                    builder.header(name, value);
                }
            });
            if (hasRequestBody(method, requestBody)) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(requestBody));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long durationMs = System.currentTimeMillis() - startedAt;
            int status = response.statusCode();
            return new HttpDebugResult(
                    status >= 200 && status < 300,
                    request.url(),
                    method,
                    status,
                    "",
                    durationMs,
                    requestHeaders,
                    headersToMap(response.headers().map()),
                    requestBody,
                    response.body(),
                    ""
            );
        } catch (Exception exception) {
            return new HttpDebugResult(
                    false,
                    request.url(),
                    method,
                    null,
                    "",
                    System.currentTimeMillis() - startedAt,
                    requestHeaders,
                    Map.of(),
                    requestBody,
                    "",
                    exception.getMessage() == null ? "调试请求失败" : exception.getMessage()
            );
        }
    }

    private boolean isRestrictedHeader(String name) {
        return name != null && RESTRICTED_HEADERS.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private boolean hasRequestBody(String method, String body) {
        return !body.isBlank() && !"GET".equals(method) && !"HEAD".equals(method);
    }

    private Map<String, String> headersToMap(Map<String, List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((key, values) -> result.put(key, String.join(", ", values)));
        return result;
    }
}
