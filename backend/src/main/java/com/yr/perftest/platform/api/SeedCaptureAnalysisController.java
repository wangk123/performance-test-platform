package com.yr.perftest.platform.api;

import com.yr.perftest.platform.seed.CreateSeedCaptureAnalysisRequest;
import com.yr.perftest.platform.seed.SeedCaptureAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SeedCaptureAnalysisController {
    private final SeedCaptureAnalysisService analysisService;

    public SeedCaptureAnalysisController(SeedCaptureAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/projects/{projectId}/seed/capture-analyses")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @PathVariable long projectId,
            @RequestBody CreateSeedCaptureAnalysisRequest request
    ) {
        return analysisService.create(projectId, request);
    }

    @GetMapping("/projects/{projectId}/seed/capture-analyses")
    public List<Map<String, Object>> list(@PathVariable long projectId) {
        return analysisService.list(projectId);
    }

    @GetMapping("/projects/{projectId}/seed/capture-analyses/{analysisId}")
    public Map<String, Object> get(
            @PathVariable long projectId,
            @PathVariable long analysisId
    ) {
        return analysisService.get(projectId, analysisId);
    }

    @PostMapping("/projects/{projectId}/seed/capture-analyses/{analysisId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> cancel(
            @PathVariable long projectId,
            @PathVariable long analysisId
    ) {
        return analysisService.cancel(projectId, analysisId);
    }

    @DeleteMapping("/projects/{projectId}/seed/capture-analyses/{analysisId}")
    public ResponseEntity<?> delete(
            @PathVariable long projectId,
            @PathVariable long analysisId
    ) {
        Map<String, Object> result = analysisService.delete(projectId, analysisId);
        if ("DELETING".equals(result.get("status"))) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/seed/capture-analyses/{analysisId}/tables/{tableName}/diffs")
    public Map<String, Object> readDiffs(
            @PathVariable long projectId,
            @PathVariable long analysisId,
            @PathVariable String tableName,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return analysisService.readDiffs(projectId, analysisId, tableName, cursor, limit);
    }
}
