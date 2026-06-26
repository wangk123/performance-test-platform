package com.yr.perftest.platform.execution.failure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionEventBroadcaster;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.distributed.FailureSampleTailResult;
import com.yr.perftest.platform.execution.distributed.RemoteRunnerClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FailureSampleIngestor {
    private final ObjectMapper objectMapper;
    private final FailureSampleStore failureSampleStore;
    private final FailureSampleSseHub failureSampleSseHub;
    private final ExecutionEventBroadcaster broadcaster;
    private final RemoteRunnerClient remoteRunnerClient;
    private final Map<Long, IngestState> states = new ConcurrentHashMap<>();

    public FailureSampleIngestor(
            ObjectMapper objectMapper,
            FailureSampleStore failureSampleStore,
            FailureSampleSseHub failureSampleSseHub,
            ExecutionEventBroadcaster broadcaster,
            RemoteRunnerClient remoteRunnerClient
    ) {
        this.objectMapper = objectMapper;
        this.failureSampleStore = failureSampleStore;
        this.failureSampleSseHub = failureSampleSseHub;
        this.broadcaster = broadcaster;
        this.remoteRunnerClient = remoteRunnerClient;
    }

    public void begin(long executionId, Path executionDirectory) {
        try {
            Path jsonlPath = FailureSamplePaths.jsonl(executionDirectory);
            Path dbPath = FailureSamplePaths.sqlite(executionDirectory);
            Files.createDirectories(executionDirectory);
            Files.writeString(jsonlPath, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            failureSampleStore.initialize(dbPath);
            states.put(executionId, new IngestState(
                    jsonlPath,
                    dbPath,
                    new ConcurrentHashMap<>(),
                    new StringBuilder(),
                    ConcurrentHashMap.newKeySet()
            ));
        } catch (Exception ignored) {
        }
    }

    public void tailRemote(long executionId, Map<String, Object> payload) {
        IngestState state = states.get(executionId);
        if (state == null) {
            return;
        }
        try {
            for (Map<String, Object> source : tailSources(payload)) {
                String host = String.valueOf(source.get("host"));
                long offset = state.remoteOffsets().getOrDefault(host, 0L);
                Map<String, Object> tailPayload = new LinkedHashMap<>(payload);
                tailPayload.put("tailNode", source);
                tailPayload.put("offset", offset);
                FailureSampleTailResult tailResult = remoteRunnerClient.tailFailureSamples(tailPayload, offset);
                if (tailResult.data().length > 0) {
                    Files.write(state.jsonlPath(), tailResult.data(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    ingestBytes(executionId, state, tailResult.data());
                }
                state.remoteOffsets().put(host, tailResult.newOffset());
            }
        } catch (Exception ignored) {
        }
    }

    public void ingestLocalFile(long executionId) {
        IngestState state = states.get(executionId);
        if (state == null || !Files.exists(state.jsonlPath())) {
            return;
        }
        try {
            long size = Files.size(state.jsonlPath());
            long offset = state.remoteOffsets().getOrDefault("__local__", 0L);
            if (size <= offset) {
                return;
            }
            byte[] all = Files.readAllBytes(state.jsonlPath());
            int from = (int) Math.min(offset, all.length);
            byte[] tail = new byte[all.length - from];
            System.arraycopy(all, from, tail, 0, tail.length);
            ingestBytes(executionId, state, tail);
            state.remoteOffsets().put("__local__", (long) all.length);
        } catch (Exception ignored) {
        }
    }

    public void finish(long executionId) {
        failureSampleSseHub.close(executionId);
        broadcaster.close(executionId);
        IngestState state = states.remove(executionId);
        if (state != null) {
            failureSampleStore.closeConnection(state.dbPath());
        }
    }

    public FailureSampleSseHub.PathContext pathContext(long executionId) {
        IngestState state = states.get(executionId);
        if (state != null) {
            return new FailureSampleSseHub.PathContext(state.jsonlPath(), state.dbPath());
        }
        return null;
    }

    public Path resolveDbPath(Path logFilePath) {
        if (logFilePath == null) {
            return null;
        }
        return FailureSamplePaths.sqlite(FailureSamplePaths.executionDirectory(logFilePath));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tailSources(Map<String, Object> payload) {
        List<Map<String, Object>> sources = new ArrayList<>();
        Object workers = payload.get("workers");
        if (workers instanceof List<?> workerList) {
            for (Object worker : workerList) {
                if (worker instanceof Map<?, ?> workerMap) {
                    sources.add((Map<String, Object>) workerMap);
                }
            }
        }
        Object controller = payload.get("controller");
        if (controller instanceof Map<?, ?> controllerMap) {
            Map<String, Object> controllerCast = (Map<String, Object>) controllerMap;
            boolean dup = sources.stream().anyMatch(item -> String.valueOf(item.get("host")).equals(String.valueOf(controllerCast.get("host"))));
            if (!dup) {
                sources.add(controllerCast);
            }
        }
        return sources;
    }

    private void ingestBytes(long executionId, IngestState state, byte[] data) throws Exception {
        state.lineBuffer().append(new String(data, StandardCharsets.UTF_8));
        int newlineIndex = state.lineBuffer().indexOf("\n");
        while (newlineIndex >= 0) {
            String line = state.lineBuffer().substring(0, newlineIndex).trim();
            state.lineBuffer().delete(0, newlineIndex + 1);
            if (!line.isBlank()) {
                FailureSampleRecord parsed = objectMapper.readValue(line, FailureSampleRecord.class);
                String fingerprint = safe(parsed.host()) + ":" + parsed.id();
                if (!state.dedupKeys().add(fingerprint)) {
                    continue;
                }
                Long persistedId = failureSampleStore.insertReturningId(state.dbPath(), parsed);
                if (persistedId == null) {
                    continue;
                }
                TaskExecutionResult.Sample detail = toDetail(persistedId, parsed);
                failureSampleSseHub.publish(executionId, detail);
                broadcaster.publish(executionId, "sample", detail);
            }
            newlineIndex = state.lineBuffer().indexOf("\n");
        }
    }

    private TaskExecutionResult.Sample toDetail(long persistedId, FailureSampleRecord record) {
        String rawRequestBody = safe(record.requestBody());
        String requestLine = FailureSampleNormalizer.extractRequestLine(rawRequestBody, safe(record.url()));
        return new TaskExecutionResult.Sample(
                (int) persistedId,
                formatTime(record.ts()),
                safe(record.code()),
                record.success(),
                safe(record.label()),
                record.elapsed(),
                safe(record.message()),
                safe(record.threadName()),
                requestLine,
                safe(record.requestHeaders()),
                FailureSampleNormalizer.cleanRequestBody(rawRequestBody),
                FailureSampleNormalizer.cleanResponseHeaders(safe(record.responseHeaders())),
                safe(record.responseBody()),
                safe(record.failureMessage())
        );
    }

    private String formatTime(long timestamp) {
        return java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(timestamp));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record IngestState(
            Path jsonlPath,
            Path dbPath,
            Map<String, Long> remoteOffsets,
            StringBuilder lineBuffer,
            Set<String> dedupKeys
    ) {
    }
}
