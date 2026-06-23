package com.yr.perftest.platform.execution.distributed;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExecutionNodeService {
    private final PersistentExecutionNodeRepository nodeRepository;
    private final RemoteRunnerClient remoteRunnerClient;
    private final SshKeyService sshKeyService;

    public ExecutionNodeService(
            PersistentExecutionNodeRepository nodeRepository,
            RemoteRunnerClient remoteRunnerClient,
            SshKeyService sshKeyService
    ) {
        this.nodeRepository = nodeRepository;
        this.remoteRunnerClient = remoteRunnerClient;
        this.sshKeyService = sshKeyService;
    }

    @Transactional(readOnly = true)
    public List<ExecutionNode> listNodes() {
        return nodeRepository.findAllByOrderByIdDesc().stream()
                .map(this::toNode)
                .toList();
    }

    @Transactional
    public ExecutionNode registerNode(RegisterExecutionNodeRequest request) {
        return saveAndInitialize(null, request);
    }

    @Transactional
    public ExecutionNode updateNode(long nodeId, RegisterExecutionNodeRequest request) {
        if (!nodeRepository.existsById(nodeId)) {
            throw new ExecutionValidationException("execution node does not exist");
        }
        return saveAndInitialize(nodeId, request);
    }

    @Transactional
    public void deleteNode(long nodeId) {
        if (!nodeRepository.existsById(nodeId)) {
            throw new ExecutionValidationException("execution node does not exist");
        }
        nodeRepository.deleteById(nodeId);
    }

    @Transactional
    public List<ExecutionNode> initializeNodes(InitializeExecutionNodesRequest request) {
        if (request.hosts() == null || request.hosts().isEmpty()) {
            throw new ExecutionValidationException("execution node hosts are required");
        }
        SshKeyService.KeyMaterial key = sshKeyService.ensureKey();
        String username = required(request.sshUsername(), "ssh username is required");
        String password = required(request.sshPassword(), "ssh password is required");
        int sshPort = request.sshPort() == null ? 22 : request.sshPort();
        String remoteWorkDir = remoteWorkDir(request.remoteWorkDir());
        List<ExecutionNode> nodes = new java.util.ArrayList<>();
        for (int index = 0; index < request.hosts().size(); index++) {
            String host = required(request.hosts().get(index), "node host is required");
            RemoteRunnerResult installResult = remoteRunnerClient.installKey(java.util.Map.of(
                    "host", host,
                    "sshPort", sshPort,
                    "sshUsername", username,
                    "sshPassword", password,
                    "publicKey", key.publicKey(),
                    "remoteWorkDir", remoteWorkDir
            ));
            ExecutionNodeRole role = index == 0 ? ExecutionNodeRole.BOTH : ExecutionNodeRole.WORKER;
            PersistentExecutionNodeRecord node = saveNode(null, "node-" + host.replace('.', '-'), host, sshPort, username, key.privateKeyPath(), role, remoteWorkDir);
            if (!installResult.ok()) {
                node.markHealth(ExecutionNodeStatus.OFFLINE, installResult.message());
                nodes.add(toNode(node));
                continue;
            }
            RemoteRunnerResult checkResult = remoteRunnerClient.checkNode(node);
            node.markHealth(checkResult.ok() ? ExecutionNodeStatus.AVAILABLE : ExecutionNodeStatus.OFFLINE, checkResult.message());
            nodes.add(toNode(node));
        }
        return nodes;
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

    private String remoteWorkDir(String value) {
        return value == null || value.isBlank() ? "/tmp/perftest-platform" : value.trim();
    }

    private PersistentExecutionNodeRecord saveNode(
            Long id,
            String name,
            String host,
            Integer sshPort,
            String sshUsername,
            String sshKeyPath,
            ExecutionNodeRole role,
            String remoteWorkDir
    ) {
        if (id != null) {
            PersistentExecutionNodeRecord node = nodeRepository.findById(id)
                    .orElseThrow(() -> new ExecutionValidationException("execution node does not exist"));
            node.update(name, host, sshPort, sshUsername, sshKeyPath, role, remoteWorkDir);
            return node;
        }
        return nodeRepository.findByHostAndSshUsername(host, sshUsername)
                .map(node -> {
                    node.update(name, host, sshPort, sshUsername, sshKeyPath, role, remoteWorkDir);
                    return node;
                })
                .orElseGet(() -> nodeRepository.save(new PersistentExecutionNodeRecord(
                        name,
                        host,
                        sshPort,
                        sshUsername,
                        sshKeyPath,
                        role,
                        remoteWorkDir
                )));
    }

    private ExecutionNode saveAndInitialize(Long nodeId, RegisterExecutionNodeRequest request) {
        String name = required(request.name(), "node name is required");
        String host = required(request.host(), "node host is required");
        int sshPort = request.sshPort() == null ? 22 : request.sshPort();
        String username = required(request.sshUsername(), "ssh username is required");
        String remoteWorkDir = remoteWorkDir(request.remoteWorkDir());
        String sshKeyPath = request.sshKeyPath();
        RemoteRunnerResult installResult = null;
        if (request.sshPassword() != null && !request.sshPassword().isBlank()) {
            SshKeyService.KeyMaterial key = sshKeyService.ensureKey();
            sshKeyPath = key.privateKeyPath();
            installResult = remoteRunnerClient.installKey(java.util.Map.of(
                    "host", host,
                    "sshPort", sshPort,
                    "sshUsername", username,
                    "sshPassword", request.sshPassword(),
                    "publicKey", key.publicKey(),
                    "remoteWorkDir", remoteWorkDir
            ));
        }
        PersistentExecutionNodeRecord node = saveNode(
                nodeId,
                name,
                host,
                sshPort,
                username,
                required(sshKeyPath, "ssh key path is required"),
                request.role() == null ? ExecutionNodeRole.WORKER : request.role(),
                remoteWorkDir
        );
        if (installResult != null && !installResult.ok()) {
            node.markHealth(ExecutionNodeStatus.OFFLINE, installResult.message());
            return toNode(node);
        }
        RemoteRunnerResult result = remoteRunnerClient.checkNode(node);
        node.markHealth(result.ok() ? ExecutionNodeStatus.AVAILABLE : ExecutionNodeStatus.OFFLINE, result.message());
        return toNode(node);
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
            String remoteWorkDir,
            String sshPassword
    ) {
    }

    public record InitializeExecutionNodesRequest(
            List<String> hosts,
            Integer sshPort,
            String sshUsername,
            String sshPassword,
            String remoteWorkDir
    ) {
    }
}
