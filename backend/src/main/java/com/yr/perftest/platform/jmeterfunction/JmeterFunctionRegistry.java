package com.yr.perftest.platform.jmeterfunction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class JmeterFunctionRegistry {
    private final List<JmeterFunctionDefinition> functions;

    public JmeterFunctionRegistry(ObjectMapper objectMapper) {
        this.functions = load(objectMapper);
    }

    public List<JmeterFunctionDefinition> list() {
        return functions;
    }

    private static List<JmeterFunctionDefinition> load(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource("jmeter-functions/functions.json").getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("failed to load jmeter function metadata", exception);
        }
    }
}
