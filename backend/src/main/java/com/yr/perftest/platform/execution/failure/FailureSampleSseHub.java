package com.yr.perftest.platform.execution.failure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class FailureSampleSseHub {
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int REPLAY_LIMIT = 200;

    private final ObjectMapper objectMapper;
    private final FailureSampleStore failureSampleStore;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public FailureSampleSseHub(ObjectMapper objectMapper, FailureSampleStore failureSampleStore) {
        this.objectMapper = objectMapper;
        this.failureSampleStore = failureSampleStore;
    }

    public SseEmitter connect(long executionId, PathContext pathContext, long lastEventId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(executionId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(executionId, emitter));
        emitter.onTimeout(() -> remove(executionId, emitter));
        emitter.onError(error -> remove(executionId, emitter));
        try {
            replay(emitter, pathContext, lastEventId);
        } catch (Exception ignored) {
        }
        return emitter;
    }

    public void publish(long executionId, TaskExecutionResult.Sample sample) {
        List<SseEmitter> activeEmitters = emitters.get(executionId);
        if (activeEmitters == null || activeEmitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : activeEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(sample.id()))
                        .name("sample")
                        .data(objectMapper.writeValueAsString(sample)));
            } catch (IOException exception) {
                remove(executionId, emitter);
            }
        }
    }

    public void close(long executionId) {
        List<SseEmitter> activeEmitters = emitters.remove(executionId);
        if (activeEmitters == null) {
            return;
        }
        for (SseEmitter emitter : activeEmitters) {
            emitter.complete();
        }
    }

    private void replay(SseEmitter emitter, PathContext pathContext, long lastEventId) throws Exception {
        List<TaskExecutionResult.Sample> samples = failureSampleStore.listSummariesAfter(
                pathContext.dbPath(),
                lastEventId,
                REPLAY_LIMIT
        );
        for (TaskExecutionResult.Sample sample : samples) {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(sample.id()))
                    .name("sample")
                    .data(objectMapper.writeValueAsString(sample)));
        }
    }

    private void remove(long executionId, SseEmitter emitter) {
        List<SseEmitter> activeEmitters = emitters.get(executionId);
        if (activeEmitters == null) {
            return;
        }
        activeEmitters.remove(emitter);
        if (activeEmitters.isEmpty()) {
            emitters.remove(executionId, activeEmitters);
        }
    }

    public record PathContext(java.nio.file.Path jsonlPath, java.nio.file.Path dbPath) {
    }
}
