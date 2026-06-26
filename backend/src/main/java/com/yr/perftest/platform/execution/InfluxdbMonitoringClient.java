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

    public TaskExecutionResult aggregate(long executionId, double durationSeconds) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(queryUrl)
                    .queryParam("db", database)
                    .queryParam("q", aggregateSql(executionId))
                    .build()
                    .encode()
                    .toUri();
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return TaskExecutionResult.empty();
            }
            Map<String, Double> koCounts = queryKoCounts(executionId);
            return parseAggregate(response.body(), durationSeconds, koCounts);
        } catch (Exception exception) {
            return TaskExecutionResult.empty();
        }
    }

    private Map<String, Double> queryKoCounts(long executionId) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(queryUrl)
                    .queryParam("db", database)
                    .queryParam("q", koCountSql(executionId))
                    .build()
                    .encode()
                    .toUri();
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return Map.of();
            }
            return parseKoCounts(response.body());
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String koCountSql(long executionId) {
        return "SELECT sum(\"count\") FROM \"" + measurement + "\" "
                + "WHERE \"application\" = 'execution-" + executionId + "' "
                + "AND \"statut\" = 'ko' "
                + "AND \"transaction\" !~ /^(all|internal)$/ "
                + "GROUP BY \"transaction\"";
    }

    private Map<String, Double> parseKoCounts(String body) throws Exception {
        Map<String, Double> koCounts = new LinkedHashMap<>();
        JsonNode results = objectMapper.readTree(body).path("results");
        if (!results.isArray() || results.isEmpty()) {
            return koCounts;
        }
        JsonNode series = results.get(0).path("series");
        if (!series.isArray()) {
            return koCounts;
        }
        for (JsonNode item : series) {
            String transaction = item.path("tags").path("transaction").asText("");
            if (transaction.isBlank()) {
                continue;
            }
            JsonNode values = item.path("values");
            if (!values.isArray() || values.isEmpty()) {
                continue;
            }
            JsonNode last = values.get(values.size() - 1);
            if (last.isArray() && last.size() > 1 && last.get(1).isNumber()) {
                koCounts.put(transaction, last.get(1).asDouble());
            }
        }
        return koCounts;
    }

    private String aggregateSql(long executionId) {
        return "SELECT \"count\", \"countError\", \"avg\", \"min\", \"max\", \"pct50.0\", \"pct90.0\", \"pct95.0\", \"pct99.0\" "
                + "FROM \"" + measurement + "\" "
                + "WHERE \"application\" = 'execution-" + executionId + "' "
                + "AND \"statut\" = 'all' "
                + "AND \"transaction\" !~ /^(all|internal)$/ "
                + "GROUP BY \"transaction\"";
    }

    private TaskExecutionResult parseAggregate(String body, double durationSeconds, Map<String, Double> koCounts) throws Exception {
        JsonNode results = objectMapper.readTree(body).path("results");
        if (!results.isArray() || results.isEmpty()) {
            return TaskExecutionResult.empty();
        }
        JsonNode series = results.get(0).path("series");
        if (!series.isArray() || series.isEmpty()) {
            return TaskExecutionResult.empty();
        }
        double safeDuration = Math.max(1, durationSeconds);
        List<TaskExecutionResult.AggregateRow> rows = new ArrayList<>();
        Accumulator total = new Accumulator();
        for (JsonNode item : series) {
            String transaction = item.path("tags").path("transaction").asText("");
            if (transaction.isBlank()) {
                continue;
            }
            Map<String, Integer> columns = columnIndexes(item.path("columns"));
            Accumulator accumulator = new Accumulator();
            for (JsonNode value : item.path("values")) {
                accumulator.add(value, columns);
            }
            if (accumulator.count <= 0) {
                continue;
            }
            accumulator.applyKoCount(koCounts.getOrDefault(transaction, 0.0));
            total.merge(accumulator);
            rows.add(accumulator.toRow(transaction, safeDuration));
        }
        if (rows.isEmpty()) {
            return TaskExecutionResult.empty();
        }
        return new TaskExecutionResult(total.toSummary(safeDuration), List.of(), rows, List.of());
    }

    private Map<String, Integer> columnIndexes(JsonNode columns) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            indexes.put(columns.get(i).asText(), i);
        }
        return indexes;
    }

    private double readField(JsonNode value, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null) {
            return 0;
        }
        JsonNode node = value.path(index);
        return node.isNumber() ? node.asDouble() : 0;
    }

    private double mergeMin(double current, double candidate) {
        if (Double.isNaN(current)) {
            return candidate;
        }
        return Double.isNaN(candidate) ? current : Math.min(current, candidate);
    }

    private double mergeMax(double current, double candidate) {
        if (Double.isNaN(current)) {
            return candidate;
        }
        return Double.isNaN(candidate) ? current : Math.max(current, candidate);
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

    private final class Accumulator {
        private double count;
        private double error;
        private double weightedAvg;
        private double weightedP50;
        private double weightedP90;
        private double weightedP95;
        private double weightedP99;
        private double min = Double.NaN;
        private double max = Double.NaN;

        private void add(JsonNode value, Map<String, Integer> columns) {
            double samples = readField(value, columns, "count");
            if (samples <= 0) {
                return;
            }
            count += samples;
            double countError = readField(value, columns, "countError");
            if (countError > 0) {
                error += countError;
            }
            weightedAvg += readField(value, columns, "avg") * samples;
            weightedP50 += readField(value, columns, "pct50.0") * samples;
            weightedP90 += readField(value, columns, "pct90.0") * samples;
            weightedP95 += readField(value, columns, "pct95.0") * samples;
            weightedP99 += readField(value, columns, "pct99.0") * samples;
            min = mergeMin(min, readField(value, columns, "min"));
            max = mergeMax(max, readField(value, columns, "max"));
        }

        private void applyKoCount(double koCount) {
            if (error <= 0) {
                error = koCount;
            }
        }

        private void merge(Accumulator other) {
            count += other.count;
            error += other.error;
            weightedAvg += other.weightedAvg;
            weightedP50 += other.weightedP50;
            weightedP90 += other.weightedP90;
            weightedP95 += other.weightedP95;
            weightedP99 += other.weightedP99;
            min = mergeMin(min, other.min);
            max = mergeMax(max, other.max);
        }

        private TaskExecutionResult.AggregateRow toRow(String transaction, double durationSeconds) {
            double divisor = Math.max(1, count);
            return new TaskExecutionResult.AggregateRow(
                    transaction,
                    "全部节点",
                    (int) Math.round(count),
                    Math.round(weightedAvg / divisor),
                    Math.round(weightedP50 / divisor),
                    Math.round(weightedP90 / divisor),
                    Math.round(weightedP95 / divisor),
                    Math.round(weightedP99 / divisor),
                    Math.round(Double.isNaN(min) ? 0 : min),
                    Math.round(Double.isNaN(max) ? 0 : max),
                    round(error * 100.0 / divisor),
                    round(count / durationSeconds)
            );
        }

        private TaskExecutionResult.Summary toSummary(double durationSeconds) {
            double divisor = Math.max(1, count);
            return new TaskExecutionResult.Summary(
                    (int) Math.round(count),
                    round(count / durationSeconds),
                    Math.round(weightedAvg / divisor),
                    Math.round(weightedP95 / divisor),
                    round(error * 100.0 / divisor)
            );
        }
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
