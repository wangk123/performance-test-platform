package com.yr.perftest.platform.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PrometheusQueryClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final long queryTimeoutMs;

    public PrometheusQueryClient(
            ObjectMapper objectMapper,
            @Value("${platform.monitoring.prometheus.base-url:http://192.168.17.216:9090}") String baseUrl,
            @Value("${platform.monitoring.prometheus.query-timeout-ms:5000}") long queryTimeoutMs
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(queryTimeoutMs))
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.queryTimeoutMs = queryTimeoutMs;
    }

    public List<MetricSeries> queryRange(String promql, long startEpochSeconds, long endEpochSeconds, int stepSeconds) {
        try {
            String url = baseUrl + "/api/v1/query_range?query="
                    + URLEncoder.encode(promql, StandardCharsets.UTF_8)
                    + "&start=" + startEpochSeconds
                    + "&end=" + endEpochSeconds
                    + "&step=" + stepSeconds;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(queryTimeoutMs))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new MonitoringValidationException("prometheus query failed: HTTP " + response.statusCode());
            }
            return parseSeries(objectMapper.readTree(response.body()));
        } catch (MonitoringValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MonitoringValidationException("prometheus query failed: " + exception.getMessage());
        }
    }

    private List<MetricSeries> parseSeries(JsonNode root) {
        if (!"success".equals(root.path("status").asText())) {
            throw new MonitoringValidationException("prometheus query rejected: " + root.path("error").asText());
        }
        JsonNode results = root.path("data").path("result");
        List<MetricSeries> series = new ArrayList<>();
        for (JsonNode item : results) {
            Map<String, String> labels = new LinkedHashMap<>();
            item.path("metric").fields().forEachRemaining(entry -> labels.put(entry.getKey(), entry.getValue().asText()));
            List<MetricSeriesPoint> points = new ArrayList<>();
            for (JsonNode value : item.path("values")) {
                if (value.size() < 2) {
                    continue;
                }
                long timestamp = value.get(0).asLong();
                String raw = value.get(1).asText();
                if ("NaN".equals(raw) || "+Inf".equals(raw) || "-Inf".equals(raw)) {
                    continue;
                }
                points.add(new MetricSeriesPoint(timestamp, Double.parseDouble(raw)));
            }
            series.add(new MetricSeries(resolveDisplayName(labels), labels, points));
        }
        return series;
    }

    private String resolveDisplayName(Map<String, String> labels) {
        if (labels.containsKey("service") && !labels.get("service").isBlank()) {
            return labels.get("service");
        }
        if (labels.containsKey("server") && !labels.get("server").isBlank()) {
            return labels.get("server");
        }
        if (labels.containsKey("device") && !labels.get("device").isBlank()) {
            String server = labels.getOrDefault("server", labels.getOrDefault("instance", ""));
            return server.isBlank() ? labels.get("device") : server + " · " + labels.get("device");
        }
        if (labels.containsKey("gc") && !labels.get("gc").isBlank()) {
            return labels.get("gc");
        }
        if (labels.containsKey("__name__") && labels.containsKey("area")) {
            return labels.get("__name__") + " · " + labels.get("area");
        }
        if (labels.containsKey("__name__")) {
            return labels.get("__name__");
        }
        return labels.getOrDefault("instance", "series");
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
