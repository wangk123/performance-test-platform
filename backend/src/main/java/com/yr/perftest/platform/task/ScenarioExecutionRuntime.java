package com.yr.perftest.platform.task;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ScenarioExecutionRuntime {
    private final Map<Long, AtomicBoolean> cancelled = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Object>> runPayloads = new ConcurrentHashMap<>();

    public void register(long executionId) {
        cancelled.put(executionId, new AtomicBoolean(false));
    }

    public void storePayload(long executionId, Map<String, Object> payload) {
        runPayloads.put(executionId, payload);
    }

    public Map<String, Object> payload(long executionId) {
        return runPayloads.get(executionId);
    }

    public boolean requestStop(long executionId) {
        AtomicBoolean flag = cancelled.get(executionId);
        if (flag == null) {
            return false;
        }
        return flag.compareAndSet(false, true);
    }

    public boolean isStopRequested(long executionId) {
        AtomicBoolean flag = cancelled.get(executionId);
        return flag != null && flag.get();
    }

    public void clear(long executionId) {
        cancelled.remove(executionId);
        runPayloads.remove(executionId);
    }
}
