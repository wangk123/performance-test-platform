package com.yr.perftest.platform.api;

import com.yr.perftest.platform.seed.CreateCloneJobRequest;
import com.yr.perftest.platform.seed.CreateSeedDatasourceRequest;
import com.yr.perftest.platform.seed.SeedDatasourceView;
import com.yr.perftest.platform.seed.SeedFactoryService;
import com.yr.perftest.platform.seed.SeedTemplateDraft;
import com.yr.perftest.platform.seed.StartCaptureRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SeedFactoryController {
    private final SeedFactoryService seedFactoryService;

    public SeedFactoryController(SeedFactoryService seedFactoryService) {
        this.seedFactoryService = seedFactoryService;
    }

    @GetMapping("/projects/{projectId}/seed/datasources")
    public List<SeedDatasourceView> listDatasources(@PathVariable long projectId) {
        return seedFactoryService.listDatasources(projectId);
    }

    @PostMapping("/projects/{projectId}/seed/datasources")
    @ResponseStatus(HttpStatus.CREATED)
    public SeedDatasourceView createDatasource(@PathVariable long projectId, @RequestBody CreateSeedDatasourceRequest request) {
        return seedFactoryService.createDatasource(projectId, request);
    }

    @PutMapping("/projects/{projectId}/seed/datasources/{id}")
    public SeedDatasourceView updateDatasource(
            @PathVariable long projectId,
            @PathVariable long id,
            @RequestBody CreateSeedDatasourceRequest request
    ) {
        return seedFactoryService.updateDatasource(projectId, id, request);
    }

    @DeleteMapping("/projects/{projectId}/seed/datasources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDatasource(@PathVariable long projectId, @PathVariable long id) {
        seedFactoryService.deleteDatasource(projectId, id);
    }

    @PostMapping("/projects/{projectId}/seed/datasources/{id}/test")
    public Map<String, Object> testDatasource(@PathVariable long projectId, @PathVariable long id) {
        return seedFactoryService.testDatasource(projectId, id);
    }

    @PostMapping("/projects/{projectId}/seed/captures")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> startCapture(@PathVariable long projectId, @RequestBody StartCaptureRequest request) {
        return seedFactoryService.startCapture(projectId, request);
    }

    @PostMapping("/projects/{projectId}/seed/captures/{sessionId}/samples")
    public Map<String, Object> endSample(@PathVariable long projectId, @PathVariable long sessionId) {
        return seedFactoryService.endSample(projectId, sessionId);
    }

    @PostMapping("/projects/{projectId}/seed/captures/{sessionId}/finish")
    public Map<String, Object> finishCapture(@PathVariable long projectId, @PathVariable long sessionId) {
        return seedFactoryService.finishCapture(projectId, sessionId);
    }

    @GetMapping("/projects/{projectId}/seed/templates")
    public List<Map<String, Object>> listTemplates(@PathVariable long projectId) {
        return seedFactoryService.listTemplates(projectId);
    }

    @GetMapping("/projects/{projectId}/seed/templates/{templateId}")
    public Map<String, Object> getTemplate(@PathVariable long projectId, @PathVariable long templateId) {
        return seedFactoryService.getTemplate(projectId, templateId);
    }

    @PutMapping("/projects/{projectId}/seed/templates/{templateId}")
    public Map<String, Object> updateTemplate(
            @PathVariable long projectId,
            @PathVariable long templateId,
            @RequestBody SeedTemplateDraft draft
    ) {
        return seedFactoryService.updateTemplateDraft(projectId, templateId, draft);
    }

    @PostMapping("/projects/{projectId}/seed/templates/{templateId}/confirm")
    public Map<String, Object> confirmTemplate(
            @PathVariable long projectId,
            @PathVariable long templateId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String operator = body == null ? null : body.get("operator");
        return seedFactoryService.confirmTemplate(projectId, templateId, operator);
    }

    @GetMapping("/projects/{projectId}/seed/clone-jobs")
    public List<Map<String, Object>> listCloneJobs(@PathVariable long projectId) {
        return seedFactoryService.listCloneJobs(projectId);
    }

    @PostMapping("/projects/{projectId}/seed/clone-jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCloneJob(@PathVariable long projectId, @RequestBody CreateCloneJobRequest request) {
        return seedFactoryService.createCloneJob(projectId, request);
    }

    @GetMapping("/projects/{projectId}/seed/clone-jobs/{jobId}")
    public Map<String, Object> getCloneJob(@PathVariable long projectId, @PathVariable long jobId) {
        return seedFactoryService.getCloneJob(projectId, jobId);
    }
}
