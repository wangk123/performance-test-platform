package com.yr.perftest.platform.execution;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class JmeterResultRetainer {
    private static final Pattern XML_SAMPLE_BLOCK = Pattern.compile(
            "<(httpSample|java\\.net\\.URL|sample)\\b[^>]*>[\\s\\S]*?</\\1>",
            Pattern.CASE_INSENSITIVE
    );

    public void retainFailureSamples(Path failureResultPath, int perLabelLimit, int globalLimit) {
        if (failureResultPath == null || !Files.exists(failureResultPath)) {
            return;
        }
        try {
            String content = Files.readString(failureResultPath, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return;
            }
            if (looksLikeXml(content)) {
                retainXmlFailureSamples(failureResultPath, content, perLabelLimit, globalLimit);
                return;
            }
            retainCsvFailureSamples(failureResultPath, content, perLabelLimit, globalLimit);
        } catch (Exception ignored) {
        }
    }

    private boolean looksLikeXml(String content) {
        String trimmed = content.stripLeading();
        return trimmed.startsWith("<?xml") || trimmed.startsWith("<testResults");
    }

    private void retainXmlFailureSamples(Path failureResultPath, String content, int perLabelLimit, int globalLimit) {
        List<IndexedBlock> blocks = new ArrayList<>();
        Matcher matcher = XML_SAMPLE_BLOCK.matcher(content);
        int index = 0;
        while (matcher.find()) {
            blocks.add(new IndexedBlock(index++, matcher.group(), readXmlLabel(matcher.group())));
        }
        if (blocks.isEmpty()) {
            return;
        }
        Map<String, List<IndexedBlock>> grouped = blocks.stream()
                .collect(Collectors.groupingBy(IndexedBlock::label, LinkedHashMap::new, Collectors.toList()));
        List<IndexedBlock> retained = new ArrayList<>();
        for (List<IndexedBlock> labelBlocks : grouped.values()) {
            List<IndexedBlock> limited = labelBlocks.size() > perLabelLimit
                    ? labelBlocks.subList(labelBlocks.size() - perLabelLimit, labelBlocks.size())
                    : labelBlocks;
            retained.addAll(limited);
        }
        retained.sort(Comparator.comparingInt(IndexedBlock::index));
        List<IndexedBlock> finalBlocks = retained.size() > globalLimit
                ? retained.subList(retained.size() - globalLimit, retained.size())
                : retained;
        StringBuilder output = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testResults version=\"1.2\">\n");
        finalBlocks.forEach(block -> output.append(block.block()).append('\n'));
        output.append("</testResults>\n");
        try {
            writeAtomically(failureResultPath, output.toString());
        } catch (Exception ignored) {
        }
    }

    private String readXmlLabel(String block) {
        Matcher matcher = Pattern.compile("\\blb=\"([^\"]*)\"").matcher(block);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void retainCsvFailureSamples(Path failureResultPath, String content, int perLabelLimit, int globalLimit) {
        List<String> records = parseCsvRecords(content);
        if (records.size() < 2) {
            return;
        }
        List<String> header = parseCsvLine(records.get(0));
        Map<String, Integer> indexes = headerIndexes(header);
        Integer labelIndex = indexes.get("label");
        Integer timestampIndex = indexes.get("timestamp");
        if (labelIndex == null || timestampIndex == null) {
            return;
        }
        List<IndexedRecord> dataRecords = IntStream.range(1, records.size())
                .mapToObj(index -> new IndexedRecord(index, records.get(index)))
                .toList();
        Map<String, List<IndexedRecord>> grouped = dataRecords.stream()
                .collect(Collectors.groupingBy(
                        record -> readField(record.value(), labelIndex),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<IndexedRecord> retained = new ArrayList<>();
        for (List<IndexedRecord> labelRecords : grouped.values()) {
            List<IndexedRecord> limited = labelRecords.size() > perLabelLimit
                    ? labelRecords.subList(labelRecords.size() - perLabelLimit, labelRecords.size())
                    : labelRecords;
            retained.addAll(limited);
        }
        retained.sort(Comparator
                .comparingLong((IndexedRecord record) -> readLong(record.value(), timestampIndex))
                .thenComparingInt(IndexedRecord::index));
        List<IndexedRecord> finalRecords = retained.size() > globalLimit
                ? retained.subList(retained.size() - globalLimit, retained.size())
                : retained;
        List<String> output = new ArrayList<>();
        output.add(records.get(0));
        finalRecords.forEach(record -> output.add(record.value()));
        try {
            writeAtomically(failureResultPath, String.join("\n", output) + "\n");
        } catch (Exception ignored) {
        }
    }

    private void writeAtomically(Path failureResultPath, String content) throws Exception {
        Path tempPath = failureResultPath.resolveSibling(failureResultPath.getFileName() + ".tmp");
        Files.writeString(tempPath, content, StandardCharsets.UTF_8);
        Files.move(tempPath, failureResultPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private Map<String, Integer> headerIndexes(List<String> header) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < header.size(); index++) {
            indexes.put(header.get(index), index);
            indexes.putIfAbsent(header.get(index).toLowerCase(Locale.ROOT), index);
        }
        return indexes;
    }

    private String readField(String record, int index) {
        List<String> values = parseCsvLine(record);
        return index < values.size() ? values.get(index) : "";
    }

    private long readLong(String record, int index) {
        try {
            return Long.parseLong(readField(record, index));
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

    private List<String> parseCsvRecords(String content) {
        List<String> records = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char value = content.charAt(i);
            if (value == '"') {
                current.append(value);
                if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    current.append(content.charAt(i + 1));
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if ((value == '\n' || value == '\r') && !quoted) {
                if (!current.isEmpty()) {
                    records.add(current.toString());
                    current.setLength(0);
                }
                if (value == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                current.append(value);
            }
        }
        if (!current.isEmpty()) {
            records.add(current.toString());
        }
        return records;
    }

    private record IndexedRecord(int index, String value) {
    }

    private record IndexedBlock(int index, String block, String label) {
    }
}
