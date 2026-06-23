package com.yr.perftest.platform.api;

import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionMode;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.TaskMonitoringResult;
import com.yr.perftest.platform.execution.TestExecutionService;
import com.yr.perftest.platform.execution.TestTask;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TaskController {
    private final TestExecutionService executionService;

    public TaskController(TestExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TestTask submitTask(
            @PathVariable long projectId,
            @Valid @RequestBody SubmitTaskRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String createdBy
    ) {
        return executionService.submitTask(
                projectId,
                request.scriptVersionId(),
                request.name(),
                request.toExecutionConfig(),
                request.remark(),
                createdBy
        );
    }

    @GetMapping("/projects/{projectId}/tasks")
    public List<TestTask> listTasks(@PathVariable long projectId) {
        return executionService.listTasks(projectId);
    }

    @GetMapping("/tasks/{taskId}")
    public TestTask getTask(@PathVariable long taskId) {
        return executionService.getTask(taskId);
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable long taskId) {
        executionService.deleteTask(taskId);
    }

    @GetMapping(value = "/tasks/{taskId}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getTaskLogs(@PathVariable long taskId) {
        return executionService.getTaskLogs(taskId);
    }

    @GetMapping("/tasks/{taskId}/result")
    public TaskExecutionResult getTaskResult(@PathVariable long taskId) {
        return executionService.getTaskResult(taskId);
    }

    @GetMapping("/tasks/{taskId}/monitoring")
    public TaskMonitoringResult getTaskMonitoring(@PathVariable long taskId) {
        return executionService.getTaskMonitoring(taskId);
    }

    public record SubmitTaskRequest(
            @NotNull Long scriptVersionId,
            @NotBlank String name,
            Integer threads,
            Integer rampUp,
            Integer duration,
            Integer loops,
            String environment,
            Map<String, String> jmeterProperties,
            ExecutionMode executionMode,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            String remark
    ) {
        ExecutionConfig toExecutionConfig() {
            return new ExecutionConfig(
                    threads == null ? 1 : threads,
                    rampUp == null ? 0 : rampUp,
                    duration == null ? 0 : duration,
                    loops == null ? 1 : loops,
                    environment,
                    jmeterProperties == null ? Map.of() : jmeterProperties,
                    executionMode == null ? ExecutionMode.LOCAL : executionMode,
                    controllerNodeId,
                    workerNodeIds == null ? List.of() : workerNodeIds
            );
        }
    }
}
