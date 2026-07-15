package com.yr.perftest.platform.api;

import com.yr.perftest.platform.seed.SeedCaptureSampleService;
import com.yr.perftest.platform.seed.SeedCaptureStrategyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SeedCaptureSampleController {
    private final SeedCaptureStrategyService strategyService;
    private final SeedCaptureSampleService sampleService;

    public SeedCaptureSampleController(
            SeedCaptureStrategyService strategyService,
            SeedCaptureSampleService sampleService
    ) {
        this.strategyService = strategyService;
        this.sampleService = sampleService;
    }

    @GetMapping("/projects/{projectId}/seed/capture-strategies/{strategyId}/samples")
    public Map<String, Object> pageSamples(
            @PathVariable long projectId,
            @PathVariable long strategyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return strategyService.pageSamples(projectId, strategyId, status, from, to, page, size);
    }

    @GetMapping("/projects/{projectId}/seed/capture-samples/{sampleId}")
    public Map<String, Object> getSample(
            @PathVariable long projectId,
            @PathVariable long sampleId
    ) {
        return strategyService.getSample(projectId, sampleId);
    }

    @GetMapping("/projects/{projectId}/seed/capture-samples/{sampleId}/tables")
    public Map<String, Object> listTables(
            @PathVariable long projectId,
            @PathVariable long sampleId
    ) {
        return sampleService.listTables(projectId, sampleId);
    }

    @GetMapping("/projects/{projectId}/seed/capture-samples/{sampleId}/tables/{tableName}/rows")
    public Map<String, Object> readRows(
            @PathVariable long projectId,
            @PathVariable long sampleId,
            @PathVariable String tableName,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return sampleService.readRows(projectId, sampleId, tableName, cursor, limit);
    }

    @DeleteMapping("/projects/{projectId}/seed/capture-samples/{sampleId}")
    public ResponseEntity<?> deleteSample(
            @PathVariable long projectId,
            @PathVariable long sampleId
    ) {
        Map<String, Object> result = sampleService.delete(projectId, sampleId);
        if ("DELETING".equals(result.get("status"))) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
        }
        return ResponseEntity.noContent().build();
    }
}
