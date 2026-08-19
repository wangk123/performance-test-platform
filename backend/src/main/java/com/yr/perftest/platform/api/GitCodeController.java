package com.yr.perftest.platform.api;

import com.yr.perftest.platform.gitlog.GitRepositoryService;
import com.yr.perftest.platform.gitlog.TaskCodeBindingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Git 仓库与任务代码绑定接口（模块 10）。
 */
@RestController
public class GitCodeController {
    private final GitRepositoryService gitRepositoryService;
    private final TaskCodeBindingService codeBindingService;

    public GitCodeController(
            GitRepositoryService gitRepositoryService,
            TaskCodeBindingService codeBindingService
    ) {
        this.gitRepositoryService = gitRepositoryService;
        this.codeBindingService = codeBindingService;
    }

    @GetMapping("/api/projects/{projectId}/git-repositories")
    public List<GitRepositoryService.RepositoryView> listRepositories(@PathVariable long projectId) {
        return gitRepositoryService.listRepositories(projectId);
    }

    @PostMapping("/api/projects/{projectId}/git-repositories")
    @ResponseStatus(HttpStatus.CREATED)
    public GitRepositoryService.RepositoryView createRepository(
            @PathVariable long projectId,
            @RequestBody CreateRepositoryRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        return gitRepositoryService.createRepository(
                projectId, request.name(), request.url(), request.authType(), request.credential(), operatorUsername);
    }

    @PostMapping("/api/git-repositories/{repositoryId}/import-commits")
    public GitRepositoryService.ImportResult importCommits(
            @PathVariable long repositoryId,
            @RequestBody ImportCommitsRequest request
    ) {
        return gitRepositoryService.importCommits(repositoryId, request.branch());
    }

    @GetMapping("/api/git-repositories/{repositoryId}/commits")
    public List<GitRepositoryService.CommitView> listCommits(
            @PathVariable long repositoryId,
            @RequestParam String branch
    ) {
        return gitRepositoryService.listCommits(repositoryId, branch);
    }

    @PutMapping("/api/scenarios/{scenarioId}/code-binding")
    public TaskCodeBindingService.BindingView bindCode(
            @PathVariable long scenarioId,
            @RequestBody BindCodeRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        return codeBindingService.bind(
                scenarioId,
                request.repositoryId(),
                request.branch(),
                request.commitId(),
                request.remark(),
                operatorUsername
        );
    }

    @GetMapping("/api/scenarios/{scenarioId}/code-binding")
    public TaskCodeBindingService.BindingView getBinding(@PathVariable long scenarioId) {
        return codeBindingService.bindingForScenario(scenarioId);
    }

    public record CreateRepositoryRequest(String name, String url, String authType, String credential) {
    }

    public record ImportCommitsRequest(String branch) {
    }

    public record BindCodeRequest(long repositoryId, String branch, String commitId, String remark) {
    }
}
