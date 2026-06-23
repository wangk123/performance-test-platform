package com.yr.perftest.platform.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class InfluxdbMonitoringClient {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String queryUrl;
    private final String database;
    private final String measurement;

    public InfluxdbMonitoringClient(
            ObjectMapper objectMapper,
            @Value("${platform.distributed.influxdb-url:http://127.0.0.1:8086/write?db=jmeter}") String influxdbUrl,
            @Value("${platform.distributed.influxdb-measurement:jmeter_runtime}") String measurement
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.queryUrl = toQueryUrl(influxdbUrl);
        this.database = database(influxdbUrl);
        this.measurement = measurement;
    }

    public TaskMonitoringResult query(long executionId) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(queryUrl)
                    .queryParam("db", database)
                    .queryParam("q", querySql(executionId))
                    .build()
                    .encode()
                    .toUri();
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return TaskMonitoringResult.empty();
            }
            return parse(response.body());
        } catch (Exception exception) {
            return TaskMonitoringResult.empty();
        }
    }

    private String querySql(long executionId) {
        return "SELECT \"count\", \"avg\", \"pct90.0\", \"pct95.0\" "
                + "FROM \"" + measurement + "\" "
                + "WHERE \"application\" = 'execution-" + executionId + "' "
                + "AND \"statut\" = 'all' "
                + "AND \"transaction\" !~ /^(all|internal)$/ "
                + "GROUP BY \"transaction\"";
    }

    private TaskMonitoringResult parse(String body) throws Exception {
        JsonNode results = objectMapper.readTree(body).path("results");
        if (!results.isArray() || results.isEmpty()) {
            return TaskMonitoringResult.empty();
        }
        JsonNode series = results.get(0).path("series");
        if (!series.isArray()) {
            return TaskMonitoringResult.empty();
        }
        Set<String> interfaces = new LinkedHashSet<>();
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (JsonNode item : series) {
            String interfaceName = item.path("tags").path("transaction").asText("");
            JsonNode values = item.path("values");
            if (interfaceName.isBlank() || !values.isArray()) {
                continue;
            }
            interfaces.add(interfaceName);
            for (JsonNode value : values) {
                String time = formatTime(value.path(0).asText());
                Bucket bucket = buckets.computeIfAbsent(time + "\n" + interfaceName, key -> new Bucket(time, interfaceName));
                bucket.add(
                        value.path(1).asDouble(0),
                        value.path(2).asDouble(0),
                        value.path(3).asDouble(0),
                        value.path(4).asDouble(0)
                );
            }
        }
        List<TaskMonitoringResult.Point> points = buckets.values().stream()
                .map(Bucket::toPoint)
                .toList();
        return new TaskMonitoringResult(List.copyOf(interfaces), points);
    }

    private String formatTime(String value) {
        try {
            return TIME_FORMATTER.format(Instant.parse(value));
        } catch (Exception exception) {
            return value;
        }
    }

    private String toQueryUrl(String influxdbUrl) {
        URI uri = URI.create(influxdbUrl);
        return UriComponentsBuilder.newInstance()
                .scheme(uri.getScheme())
                .host(uri.getHost())
                .port(uri.getPort())
                .path("/query")
                .toUriString();
    }

    private String database(String influxdbUrl) {
        String query = URI.create(influxdbUrl).getRawQuery();
        if (query == null || query.isBlank()) {
            return "jmeter";
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && "db".equals(pair[0])) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return "jmeter";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private final class Bucket {
        private final String time;
        private final String interfaceName;
        private double samples;
        private double weightedAvgRt;
        private double weightedP90;
        private double weightedP95;

        private Bucket(String time, String interfaceName) {
            this.time = time;
            this.interfaceName = interfaceName;
        }

        private void add(double count, double avgRt, double p90, double p95) {
            samples += count;
            weightedAvgRt += avgRt * count;
            weightedP90 += p90 * count;
            weightedP95 += p95 * count;
        }

        private TaskMonitoringResult.Point toPoint() {
            double divisor = Math.max(1, samples);
            return new TaskMonitoringResult.Point(
                    time,
                    interfaceName,
                    round(samples),
                    round(weightedAvgRt / divisor),
                    round(weightedP90 / divisor),
                    round(weightedP95 / divisor)
            );
        }
    }
}
