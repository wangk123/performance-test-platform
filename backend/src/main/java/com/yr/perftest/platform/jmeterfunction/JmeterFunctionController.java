package com.yr.perftest.platform.jmeterfunction;

import com.yr.perftest.platform.api.ApiError;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/jmeter-functions")
public class JmeterFunctionController {
    private static final String JAR_RESOURCE = "jmeter-runtime/perftest-jmeter-functions.jar";

    private final JmeterFunctionRegistry registry;

    public JmeterFunctionController(JmeterFunctionRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<JmeterFunctionDefinition> list() {
        return registry.list();
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download() throws IOException {
        ClassPathResource resource = new ClassPathResource(JAR_RESOURCE);
        if (!resource.exists()) {
            throw new JmeterFunctionPackageUnavailableException("function package is unavailable");
        }
        byte[] body = resource.getInputStream().readAllBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"perftest-jmeter-functions.jar\"")
                .contentType(MediaType.parseMediaType("application/java-archive"))
                .body(body);
    }

    @ExceptionHandler(JmeterFunctionPackageUnavailableException.class)
    public ResponseEntity<ApiError> handleUnavailable(JmeterFunctionPackageUnavailableException exception) {
        return ResponseEntity.status(503).body(new ApiError("UNAVAILABLE", exception.getMessage()));
    }

    static class JmeterFunctionPackageUnavailableException extends RuntimeException {
        JmeterFunctionPackageUnavailableException(String message) {
            super(message);
        }
    }
}
