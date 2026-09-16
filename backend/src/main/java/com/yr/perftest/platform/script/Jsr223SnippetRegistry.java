package com.yr.perftest.platform.script;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class Jsr223SnippetRegistry {
    private final List<Jsr223Snippet> snippets;

    public Jsr223SnippetRegistry(ObjectMapper objectMapper) {
        this.snippets = load(objectMapper);
    }

    public List<Jsr223Snippet> list() {
        return snippets;
    }

    private static List<Jsr223Snippet> load(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource("jsr223-snippets.json").getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("failed to load jsr223 snippet metadata", exception);
        }
    }
}
