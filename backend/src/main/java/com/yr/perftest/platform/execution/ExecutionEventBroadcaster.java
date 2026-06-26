package com.yr.perftest.platform.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ExecutionEventBroadcaster {
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final ObjectMapper objectMapper;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final AtomicLong eventIdSeq = new AtomicLong();

    public ExecutionEventBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter connect(long executionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(executionId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(executionId, emitter));
        emitter.onTimeout(() -> remove(executionId, emitter));
        emitter.onError(error -> remove(executionId, emitter));
        try {
            emitter.send(SseEmitter.event().name("hello").data("{}"));
        } catch (IOException ignored) {
        }
        return emitter;
    }

    public void publish(long executionId, String eventName, Object payload) {
        List<SseEmitter> activeEmitters = emitters.get(executionId);
        if (activeEmitters == null || activeEmitters.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            return;
        }
        String id = String.valueOf(eventIdSeq.incrementAndGet());
        for (SseEmitter emitter : activeEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(id)
                        .name(eventName)
                        .data(json));
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
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
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
}
