package com.yr.perftest.platform.execution;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JmeterResultParser {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final Pattern XML_SAMPLE_BLOCK = Pattern.compile(
            "<(httpSample|java\\.net\\.URL|sample)\\b[^>]*>[\\s\\S]*?</\\1>",
            Pattern.CASE_INSENSITIVE
    );

    public TaskSamplePage parseSamplePage(Path samplePath, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        if (samplePath == null || !Files.exists(samplePath)) {
            return new TaskSamplePage(safePage, safePageSize, 0, List.of());
        }
        try {
            String content = Files.readString(samplePath, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return new TaskSamplePage(safePage, safePageSize, 0, List.of());
            }
            if (looksLikeXml(content)) {
                return parseXmlSamplePage(content, safePage, safePageSize);
            }
            return parseCsvSamplePage(content, safePage, safePageSize);
        } catch (Exception exception) {
            return new TaskSamplePage(safePage, safePageSize, 0, List.of());
        }
    }

    private boolean looksLikeXml(String content) {
        String trimmed = content.stripLeading();
        return trimmed.startsWith("<?xml") || trimmed.startsWith("<testResults");
    }

    private TaskSamplePage parseXmlSamplePage(String content, int safePage, int safePageSize) {
        List<Row> rows = new ArrayList<>();
        Matcher matcher = XML_SAMPLE_BLOCK.matcher(content);
        while (matcher.find()) {
            rows.add(parseXmlSampleBlock(matcher.group()));
        }
        int offset = (safePage - 1) * safePageSize;
        int total = rows.size();
        List<TaskExecutionResult.Sample> samples = new ArrayList<>();
        for (int index = offset; index < rows.size() && samples.size() < safePageSize; index++) {
            samples.add(toSample(rows.get(index)));
        }
        return new TaskSamplePage(safePage, safePageSize, total, samples);
    }

    private Row parseXmlSampleBlock(String block) {
        String opening = block.substring(0, block.indexOf('>'));
        Map<String, String> attributes = parseAttributes(opening);
        String failureMessage = extractXmlChild(block, "failureMessage");
        if (failureMessage.isBlank()) {
            failureMessage = extractAssertionFailure(block);
        }
        return new Row(
                readLong(attributes.get("ts")),
                readLong(attributes.get("t")),
                attributes.getOrDefault("lb", ""),
                attributes.getOrDefault("rc", ""),
                attributes.getOrDefault("rm", ""),
                attributes.getOrDefault("tn", ""),
                !"false".equalsIgnoreCase(attributes.get("s")),
                failureMessage,
                extractXmlChild(block, "java.net.URL"),
                extractXmlChild(block, "queryString"),
                extractXmlChild(block, "samplerData"),
                extractXmlChild(block, "requestHeader"),
                extractXmlChild(block, "responseData"),
                extractXmlChild(block, "responseHeader")
        );
    }

    private Map<String, String> parseAttributes(String openingTag) {
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("(\\w+)=\"([^\"]*)\"").matcher(openingTag);
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(2));
        }
        return attributes;
    }

    private String extractXmlChild(String block, String tagName) {
        Pattern pattern = Pattern.compile(
                "<" + Pattern.quote(tagName) + "(?:\\s[^>]*)?>([\\s\\S]*?)</" + Pattern.quote(tagName) + ">",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(block);
        if (!matcher.find()) {
            return "";
        }
        return decodeXmlText(matcher.group(1));
    }

    private String extractAssertionFailure(String block) {
        Matcher matcher = Pattern.compile(
                "<failure\\b[^>]*>([\\s\\S]*?)</failure>",
                Pattern.CASE_INSENSITIVE
        ).matcher(block);
        if (!matcher.find()) {
            return "";
        }
        return decodeXmlText(matcher.group(1)).trim();
    }

    private String decodeXmlText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private long readLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (Exception exception) {
            return 0L;
        }
    }

    private TaskSamplePage parseCsvSamplePage(String content, int safePage, int safePageSize) {
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(content))) {
            String headerRecord = readRecord(reader);
            if (headerRecord == null) {
                return new TaskSamplePage(safePage, safePageSize, 0, List.of());
            }
            Map<String, Integer> header = headerIndexes(parseCsvLine(headerRecord));
            List<TaskExecutionResult.Sample> samples = new ArrayList<>();
            int offset = (safePage - 1) * safePageSize;
            int total = 0;
            String record;
            while ((record = readRecord(reader)) != null) {
                if (record.isBlank()) {
                    continue;
                }
                if (total >= offset && samples.size() < safePageSize) {
                    samples.add(toSample(toRow(parseCsvLine(record), header)));
                }
                total++;
            }
            return new TaskSamplePage(safePage, safePageSize, total, samples);
        } catch (Exception exception) {
            return new TaskSamplePage(safePage, safePageSize, 0, List.of());
        }
    }

    private TaskExecutionResult.Sample toSample(Row row) {
        String requestLine = extractRequestLine(row);
        String requestHeaders = row.requestHeaders();
        String requestBody = extractRequestBody(row);
        String responseHeaders = row.responseHeaders();
        String responseBody = decodePayload(row.responseData());
        String failureMessage = row.failureMessage();
        return new TaskExecutionResult.Sample(
                row.id(),
                TIME_FORMATTER.format(Instant.ofEpochMilli(row.timestamp())),
                row.statusCode(),
                row.success(),
                row.label(),
                row.elapsed(),
                row.message(),
                row.threadName(),
                formatRequest(requestLine, requestHeaders, requestBody, row),
                formatResponse(row.statusCode(), row.message(), responseHeaders, responseBody, failureMessage),
                requestLine,
                requestHeaders,
                requestBody,
                responseHeaders,
                responseBody,
                failureMessage
        );
    }

    private String formatRequest(String requestLine, String requestHeaders, String requestBody, Row row) {
        List<String> sections = new ArrayList<>();
        if (!requestLine.isBlank()) {
            sections.add(requestLine);
        }
        if (!requestHeaders.isBlank()) {
            sections.add(requestHeaders);
        }
        if (!requestBody.isBlank()) {
            sections.add(requestBody);
        }
        if (!sections.isEmpty()) {
            return String.join("\n\n", sections);
        }
        return row.url().isBlank() ? row.label() : row.url();
    }

    private String formatResponse(
            String statusCode,
            String message,
            String responseHeaders,
            String responseBody,
            String failureMessage
    ) {
        List<String> sections = new ArrayList<>();
        if (!statusCode.isBlank()) {
            String statusLine = "HTTP " + statusCode;
            if (!message.isBlank()) {
                statusLine += " " + message;
            }
            sections.add(statusLine);
        }
        if (!responseHeaders.isBlank()) {
            sections.add(responseHeaders);
        }
        if (!responseBody.isBlank()) {
            sections.add(responseBody);
        }
        if (!failureMessage.isBlank()) {
            sections.add("--- Failure Message ---\n" + failureMessage);
        }
        if (!sections.isEmpty()) {
            return String.join("\n\n", sections);
        }
        return message;
    }

    private String extractRequestLine(Row row) {
        String samplerData = decodePayload(row.samplerData());
        if (!samplerData.isBlank()) {
            String firstLine = samplerData.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .findFirst()
                    .orElse("");
            if (looksLikeRequestLine(firstLine)) {
                return firstLine;
            }
        }
        if (!row.url().isBlank()) {
            return row.queryString().isBlank() ? row.url() : row.url() + "?" + row.queryString();
        }
        return "";
    }

    private String extractRequestBody(Row row) {
        String samplerData = decodePayload(row.samplerData());
        if (!samplerData.isBlank()) {
            String body = extractBodyFromSamplerData(samplerData);
            if (!body.isBlank()) {
                return body;
            }
        }
        return row.queryString();
    }

    private String extractBodyFromSamplerData(String samplerData) {
        String[] markers = {"POST data:", "PUT data:", "PATCH data:", "DELETE data:", "GET data:"};
        for (String marker : markers) {
            int index = indexOfIgnoreCase(samplerData, marker);
            if (index < 0) {
                continue;
            }
            String body = samplerData.substring(index + marker.length()).trim();
            return stripCookieSuffix(body);
        }
        return "";
    }

    private String stripCookieSuffix(String body) {
        int cookieIndex = indexOfIgnoreCase(body, "[no cookies]");
        if (cookieIndex >= 0) {
            body = body.substring(0, cookieIndex).trim();
        }
        return body;
    }

    private boolean looksLikeRequestLine(String line) {
        return line.contains("://")
                || line.matches("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+\\S+.*");
    }

    private int indexOfIgnoreCase(String source, String target) {
        return source.toLowerCase(Locale.ROOT).indexOf(target.toLowerCase(Locale.ROOT));
    }

    private String normalizeField(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
        }
        return trimmed;
    }

    private String decodePayload(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = normalizeField(value);
        if (!looksLikeBase64(normalized)) {
            return normalized;
        }
        try {
            return new String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return normalized;
        }
    }

    private boolean looksLikeBase64(String value) {
        if (value.length() < 16 || value.length() % 4 != 0) {
            return false;
        }
        return value.chars().allMatch(character ->
                Character.isLetterOrDigit(character) || character == '+' || character == '/' || character == '=');
    }

    private Row toRow(List<String> values, Map<String, Integer> header) {
        return new Row(
                readLong(values, header, "timeStamp"),
                readLong(values, header, "elapsed"),
                read(values, header, "label"),
                read(values, header, "responseCode"),
                read(values, header, "responseMessage"),
                read(values, header, "threadName"),
                Boolean.parseBoolean(read(values, header, "success")),
                read(values, header, "failureMessage"),
                read(values, header, "URL", "url"),
                read(values, header, "queryString"),
                read(values, header, "samplerData"),
                read(values, header, "requestHeaders"),
                read(values, header, "responseData"),
                read(values, header, "responseHeaders")
        );
    }

    private Map<String, Integer> headerIndexes(List<String> header) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            indexes.put(header.get(i), i);
            indexes.putIfAbsent(header.get(i).toLowerCase(Locale.ROOT), i);
        }
        return indexes;
    }

    private String read(List<String> values, Map<String, Integer> header, String... names) {
        for (String name : names) {
            Integer index = header.get(name);
            if (index == null) {
                index = header.get(name.toLowerCase(Locale.ROOT));
            }
            if (index != null && index < values.size()) {
                return normalizeField(values.get(index));
            }
        }
        return "";
    }

    private long readLong(List<String> values, Map<String, Integer> header, String name) {
        try {
            return Long.parseLong(read(values, header, name));
        } catch (Exception exception) {
            return 0L;
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char value = line.charAt(i);
            if (value == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (value == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(value);
            }
        }
        values.add(current.toString());
        return values;
    }

    private String readRecord(Reader reader) throws Exception {
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        int raw;
        while ((raw = reader.read()) != -1) {
            char value = (char) raw;
            if (value == '"') {
                current.append(value);
                if (quoted) {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        current.append((char) next);
                    } else {
                        quoted = false;
                        if (next != -1) {
                            reader.reset();
                        }
                    }
                } else {
                    quoted = true;
                }
            } else if ((value == '\n' || value == '\r') && !quoted) {
                if (value == '\r') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next != '\n' && next != -1) {
                        reader.reset();
                    }
                }
                if (!current.isEmpty()) {
                    return current.toString();
                }
            } else {
                current.append(value);
            }
        }
        return current.isEmpty() ? null : current.toString();
    }

    private record Row(
            long timestamp,
            long elapsed,
            String label,
            String statusCode,
            String message,
            String threadName,
            boolean success,
            String failureMessage,
            String url,
            String queryString,
            String samplerData,
            String requestHeaders,
            String responseData,
            String responseHeaders
    ) {
        int id() {
            return Math.abs((timestamp + ":" + label + ":" + threadName).hashCode());
        }
    }
}
