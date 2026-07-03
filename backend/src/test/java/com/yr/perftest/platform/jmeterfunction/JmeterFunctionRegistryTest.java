package com.yr.perftest.platform.jmeterfunction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmeterFunctionRegistryTest {

    @Test
    void loadsBundledFunctionMetadata() {
        JmeterFunctionRegistry registry = new JmeterFunctionRegistry(new ObjectMapper());
        assertTrue(registry.list().size() >= 2);
        assertTrue(registry.list().stream().anyMatch(item -> "randomMobile".equals(item.key())));
        assertEquals("${__randomMobile()}", registry.list().stream()
                .filter(item -> "randomMobile".equals(item.key()))
                .findFirst()
                .orElseThrow()
                .example());
    }
}
