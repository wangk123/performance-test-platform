package com.yr.perftest.platform.evidence.deep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

/**
 * SkyWalking OAP GraphQL 访问入口（Task 3 探针 / Task 4 REST / Task 6 快照共用）。
 * endpoint 取 {@code platform.evidence.deep.kinds.trace.endpoint}；
 * 由 {@code DeepEvidenceConfiguration} 注册为 {@code @Bean}。
 */
public class SkyWalkingGraphqlClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String endpoint;

    public SkyWalkingGraphqlClient(DeepEvidenceProperties properties) {
        this.endpoint = properties.forKind(DeepEvidenceKind.TRACE).getEndpoint();
    }

    public SkyWalkingTraceModels.TraceBriefsPage queryBasicTraces(String service, String endpointName,
            Instant from, Instant to, Long minDurationMs, Boolean onlyError, boolean orderByDuration,
            int pageNum, int pageSize) {
        Map<String, Object> condition = Map.of(
                "service", nvl(service),
                "endpointName", nvl(endpointName),
                "queryDuration", Map.of(
                        "start", SkyWalkingTraceModels.SW_TIME.format(from.atZone(ZoneId.systemDefault())),
                        "end", SkyWalkingTraceModels.SW_TIME.format(to.atZone(ZoneId.systemDefault())),
                        "step", "SECOND"),
                "queryOrder", orderByDuration ? "BY_DURATION" : "BY_START_TIME",
                "minTraceDuration", minDurationMs == null ? 0L : minDurationMs,
                "traceState", Boolean.TRUE.equals(onlyError) ? "ERROR" : "ALL",
                "paging", Map.of("pageNum", pageNum, "pageSize", pageSize),
                "queryDurationStep", "SECOND");
        String resp = post("""
                query basicTraces($condition: TraceQueryCondition) {
                  queryBasicTraces(condition: $condition) {
                    traces { traceIds endpointNames service duration start isError }
                    total
                  }
                }""", Map.of("condition", condition));
        return SkyWalkingTraceModels.parseBasicTraces(resp);
    }

    public SkyWalkingTraceModels.TraceDetail queryTrace(String traceId) {
        String resp = post("""
                query trace($traceId: ID!) {
                  queryTrace(traceId: $traceId) {
                    segments { service endpointNames
                      spans { spanId parentSpanId endpointName startTime endTime isError } }
                  }
                }""", Map.of("traceId", traceId));
        return SkyWalkingTraceModels.parseTrace(resp);
    }

    private String post(String query, Map<String, Object> variables) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new SkyWalkingQueryException("skywalking trace endpoint is not configured");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimTrailingSlash(endpoint) + "/graphql"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of("query", query, "variables", variables)),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new SkyWalkingQueryException("skywalking graphql failed: HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode errors = root.get("errors");
            if (errors != null && !errors.isNull() && !errors.isEmpty()) {
                throw new SkyWalkingQueryException("skywalking graphql errors: " + errors);
            }
            return response.body();
        } catch (SkyWalkingQueryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SkyWalkingQueryException("skywalking graphql request failed: " + exception.getMessage(), exception);
        }
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
