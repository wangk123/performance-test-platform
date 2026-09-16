package com.yr.perftest.platform.api;

import com.yr.perftest.platform.script.Jsr223Snippet;
import com.yr.perftest.platform.script.Jsr223SnippetRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jsr223-snippets")
public class Jsr223SnippetController {
    private final Jsr223SnippetRegistry registry;

    public Jsr223SnippetController(Jsr223SnippetRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<Jsr223Snippet> list() {
        return registry.list();
    }
}
