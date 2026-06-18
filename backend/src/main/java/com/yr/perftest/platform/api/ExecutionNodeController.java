package com.yr.perftest.platform.api;

import com.yr.perftest.platform.execution.distributed.ExecutionNode;
import com.yr.perftest.platform.execution.distributed.ExecutionNodeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/execution-nodes")
public class ExecutionNodeController {
    private final ExecutionNodeService executionNodeService;

    public ExecutionNodeController(ExecutionNodeService executionNodeService) {
        this.executionNodeService = executionNodeService;
    }

    @GetMapping
    public List<ExecutionNode> listNodes() {
        return executionNodeService.listNodes();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionNode registerNode(@Valid @RequestBody ExecutionNodeService.RegisterExecutionNodeRequest request) {
        return executionNodeService.registerNode(request);
    }

    @PostMapping("/{nodeId}/check")
    public ExecutionNode checkNode(@PathVariable long nodeId) {
        return executionNodeService.checkNode(nodeId);
    }
}
