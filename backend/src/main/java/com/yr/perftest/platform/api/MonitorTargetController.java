package com.yr.perftest.platform.api;

import com.yr.perftest.platform.monitoring.MonitorDeployResult;
import com.yr.perftest.platform.monitoring.MonitorDeployService;
import com.yr.perftest.platform.monitoring.MonitorTarget;
import com.yr.perftest.platform.monitoring.MonitorItem;
import com.yr.perftest.platform.monitoring.MonitorTargetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class MonitorTargetController {
    private final MonitorTargetService targetService;
    private final MonitorDeployService deployService;

    public MonitorTargetController(MonitorTargetService targetService, MonitorDeployService deployService) {
        this.targetService = targetService;
        this.deployService = deployService;
    }

    @GetMapping("/projects/{projectId}/monitor-targets")
    public List<MonitorTarget> listTargets(@PathVariable long projectId) {
        return targetService.listTargets(projectId);
    }

    @PostMapping("/projects/{projectId}/monitor-targets")
    @ResponseStatus(HttpStatus.CREATED)
    public MonitorTarget createTarget(@PathVariable long projectId, @Valid @RequestBody MonitorTargetRequest request) {
        return targetService.createTarget(projectId, request.toInput());
    }

    @PutMapping("/monitor-targets/{targetId}")
    public MonitorTarget updateTarget(@PathVariable long targetId, @Valid @RequestBody MonitorTargetRequest request) {
        return targetService.updateTarget(targetId, request.toInput());
    }

    @PostMapping("/monitor-targets/{targetId}/check")
    public MonitorTarget checkTarget(@PathVariable long targetId) {
        return targetService.checkTarget(targetId);
    }

    @PostMapping("/monitor-targets/{targetId}/deploy")
    public MonitorDeployResult deployTarget(@PathVariable long targetId) {
        return deployService.deployTarget(targetId);
    }

    @DeleteMapping("/monitor-targets/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTarget(@PathVariable long targetId) {
        targetService.deleteTarget(targetId);
    }

    public record MonitorTargetRequest(
            @NotBlank String name,
            String serviceName,
            @NotBlank String host,
            String sshUsername,
            String sshPassword,
            Integer sshPort,
            String pluginDir,
            @NotNull Integer port,
            String metricsPath,
            String env,
            Map<String, String> labels,
            List<MonitorItem> items,
            Boolean enabled
    ) {
        MonitorTargetService.MonitorTargetInput toInput() {
            return new MonitorTargetService.MonitorTargetInput(
                    name,
                    serviceName,
                    host,
                    sshUsername,
                    sshPassword,
                    sshPort,
                    pluginDir,
                    port,
                    metricsPath,
                    env,
                    labels,
                    items,
                    enabled
            );
        }
    }
}
