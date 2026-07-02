package com.yr.perftest.platform.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TaskJsonSupport {
    private final ObjectMapper objectMapper;

    public TaskJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String writeLongList(List<Long> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception exception) {
            throw new ExecutionValidationException("list json is invalid");
        }
    }

    public List<Long> readLongList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception exception) {
            throw new ExecutionValidationException("list json is invalid");
        }
    }

    public String writeStringMap(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? Map.of() : values);
        } catch (Exception exception) {
            throw new ExecutionValidationException("map json is invalid");
        }
    }

    public Map<String, String> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception exception) {
            throw new ExecutionValidationException("map json is invalid");
        }
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
