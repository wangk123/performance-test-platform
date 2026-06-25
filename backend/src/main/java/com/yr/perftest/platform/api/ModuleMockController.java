package com.yr.perftest.platform.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ModuleMockController {
    @GetMapping("/system/users")
    public List<Map<String, Object>> listSystemUsers() {
        return List.of(
                Map.of("username", "admin", "displayName", "平台管理员", "status", "ENABLED"),
                Map.of("username", "tester", "displayName", "性能测试工程师", "status", "ENABLED")
        );
    }

    @GetMapping("/system/roles")
    public List<Map<String, Object>> listSystemRoles() {
        return List.of(
                Map.of("code", "ADMIN", "name", "系统管理员"),
                Map.of("code", "PROJECT_MEMBER", "name", "项目成员")
        );
    }

    @GetMapping("/system/configs")
    public List<Map<String, Object>> listSystemConfigs() {
        return List.of(
                Map.of("key", "task.maxConcurrent", "value", "1"),
                Map.of("key", "storage.retentionDays", "value", "30")
        );
    }

    @GetMapping("/executions/{executionId}/metrics")
    public List<Map<String, Object>> listExecutionMetrics(@PathVariable long executionId) {
        return List.of(
                Map.of("executionId", executionId, "metricName", "cpu.usage", "metricValue", 42.6),
                Map.of("executionId", executionId, "metricName", "memory.usage", "metricValue", 68.2)
        );
    }

    @GetMapping("/projects/{projectId}/reports")
    public List<Map<String, Object>> listReports(@PathVariable long projectId) {
        return List.of(
                Map.of("id", 1, "projectId", projectId, "title", "容量基线报告", "status", "SUCCESS"),
                Map.of("id", 2, "projectId", projectId, "title", "瓶颈复测报告", "status", "SUCCESS")
        );
    }

    @PostMapping("/executions/{executionId}/reports")
    public Map<String, Object> createReport(@PathVariable long executionId) {
        return Map.of("id", 1001, "executionId", executionId, "status", "GENERATING");
    }

    @GetMapping("/reports/{reportId}")
    public Map<String, Object> getReport(@PathVariable long reportId) {
        return Map.of("id", reportId, "title", "Mock 测试报告", "status", "SUCCESS");
    }

    @GetMapping(value = "/reports/{reportId}/download", produces = MediaType.TEXT_PLAIN_VALUE)
    public String downloadReport(@PathVariable long reportId, @RequestParam(defaultValue = "markdown") String format) {
        return "mock report " + reportId + " format=" + format;
    }

    @PostMapping("/reports/{reportId}/regenerate")
    public Map<String, Object> regenerateReport(@PathVariable long reportId) {
        return Map.of("id", reportId, "status", "REGENERATING");
    }

    @PostMapping("/reports/compare")
    public Map<String, Object> compareReports() {
        return Map.of("id", 1, "status", "MOCK_CREATED");
    }

    @GetMapping("/projects/{projectId}/data-templates")
    public List<Map<String, Object>> listDataTemplates(@PathVariable long projectId) {
        return List.of(
                Map.of("id", 1, "projectId", projectId, "name", "客户授信 CSV", "scope", "PROJECT"),
                Map.of("id", 2, "projectId", projectId, "name", "合同签署 JSON", "scope", "PROJECT")
        );
    }

    @PostMapping("/projects/{projectId}/data-templates")
    public Map<String, Object> createDataTemplate(@PathVariable long projectId) {
        return Map.of("id", 1001, "projectId", projectId, "status", "MOCK_CREATED");
    }

    @PostMapping("/data-templates/{templateId}/preview")
    public List<Map<String, Object>> previewData(@PathVariable long templateId) {
        return List.of(Map.of("templateId", templateId, "customerId", "C102938", "amount", 50000));
    }

    @PostMapping("/data-templates/{templateId}/generate")
    public Map<String, Object> generateData(@PathVariable long templateId) {
        return Map.of("jobId", 1, "templateId", templateId, "status", "QUEUED");
    }

    @GetMapping("/projects/{projectId}/functions")
    public List<Map<String, Object>> listFunctions(@PathVariable long projectId) {
        return List.of(
                Map.of("id", 1, "projectId", projectId, "name", "signRequest", "category", "HTTP"),
                Map.of("id", 2, "projectId", projectId, "name", "randomMobile", "category", "DATA")
        );
    }

    @PostMapping("/projects/{projectId}/functions")
    public Map<String, Object> createFunction(@PathVariable long projectId) {
        return Map.of("id", 1001, "projectId", projectId, "status", "MOCK_CREATED");
    }

    @PostMapping("/function-versions/{versionId}/debug")
    public Map<String, Object> debugFunction(@PathVariable long versionId) {
        return Map.of("versionId", versionId, "output", "mock output", "durationMs", 12);
    }
}
