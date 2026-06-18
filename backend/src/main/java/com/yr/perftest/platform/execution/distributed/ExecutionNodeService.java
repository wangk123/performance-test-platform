package com.yr.perftest.platform.execution.distributed;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExecutionNodeService {
    private final PersistentExecutionNodeRepository nodeRepository;
    private final RemoteRunnerClient remoteRunnerClient;

    public ExecutionNodeService(
            PersistentExecutionNodeRepository nodeRepository,
            RemoteRunnerClient remoteRunnerClient
    ) {
        this.nodeRepository = nodeRepository;
        this.remoteRunnerClient = remoteRunnerClient;
    }

    @Transactional(readOnly = true)
    public List<ExecutionNode> listNodes() {
        return nodeRepository.findAllByOrderByIdDesc().stream()
                .map(this::toNode)
                .toList();
    }

    @Transactional
    public ExecutionNode registerNode(RegisterExecutionNodeRequest request) {
        PersistentExecutionNodeRecord node = nodeRepository.save(new PersistentExecutionNodeRecord(
                required(request.name(), "node name is required"),
                required(request.host(), "node host is required"),
                request.sshPort() == null ? 22 : request.sshPort(),
                required(request.sshUsername(), "ssh username is required"),
                required(request.sshKeyPath(), "ssh key path is required"),
                request.role() == null ? ExecutionNodeRole.WORKER : request.role(),
                request.remoteWorkDir() == null || request.remoteWorkDir().isBlank()
                        ? "/tmp/perftest-platform"
                        : request.remoteWorkDir().trim()
        ));
        RemoteRunnerResult result = remoteRunnerClient.checkNode(node);
        node.markHealth(result.ok() ? ExecutionNodeStatus.AVAILABLE : ExecutionNodeStatus.OFFLINE, result.message());
        return toNode(node);
    }

    @Transactional
    public ExecutionNode checkNode(long nodeId) {
        PersistentExecutionNodeRecord node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ExecutionValidationException("execution node does not exist"));
        RemoteRunnerResult result = remoteRunnerClient.checkNode(node);
        node.markHealth(result.ok() ? ExecutionNodeStatus.AVAILABLE : ExecutionNodeStatus.OFFLINE, result.message());
        return toNode(node);
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ExecutionValidationException(message);
        }
        return value.trim();
    }

    private ExecutionNode toNode(PersistentExecutionNodeRecord node) {
        return new ExecutionNode(
                node.getId(),
                node.getName(),
                node.getHost(),
                node.getSshPort(),
                node.getSshUsername(),
                node.getSshKeyPath(),
                node.getRole(),
                node.getStatus(),
                node.getRemoteWorkDir(),
                node.getLastCheckedAt(),
                node.getLastMessage(),
                node.getCreatedAt(),
                node.getUpdatedAt()
        );
    }

    public record RegisterExecutionNodeRequest(
            String name,
            String host,
            Integer sshPort,
            String sshUsername,
            String sshKeyPath,
            ExecutionNodeRole role,
            String remoteWorkDir
    ) {
    }
}
