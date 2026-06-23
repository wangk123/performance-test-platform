package com.yr.perftest.platform.api;

import com.yr.perftest.platform.execution.HttpDebugRequest;
import com.yr.perftest.platform.execution.HttpDebugResult;
import com.yr.perftest.platform.execution.HttpDebugService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/http-debug")
public class HttpDebugController {
    private final HttpDebugService httpDebugService;

    public HttpDebugController(HttpDebugService httpDebugService) {
        this.httpDebugService = httpDebugService;
    }

    @PostMapping
    public HttpDebugResult debug(@Valid @RequestBody HttpDebugRequest request) {
        return httpDebugService.execute(request);
    }
}
