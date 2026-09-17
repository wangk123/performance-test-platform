package com.yr.perftest.platform.api;

import com.yr.perftest.platform.script.SaveScriptDefinitionResult;
import com.yr.perftest.platform.script.ScriptPublicationService;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import com.yr.perftest.platform.script.ScriptVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ScriptPublicationController {
    private final ScriptPublicationService publicationService;

    public ScriptPublicationController(ScriptPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @PutMapping("/api/projects/{projectId}/scripts/{scriptId}/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public SaveScriptDefinitionResult saveDraft(
            @PathVariable long projectId,
            @PathVariable long scriptId,
            @Valid @RequestBody SaveDraftRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String updatedBy
    ) {
        if (request.content() != null) {
            return publicationService.saveDraftContent(
                    projectId, scriptId, request.filename(), request.content(), updatedBy);
        }
        return publicationService.saveDraftDefinition(
                projectId, scriptId, request.filename(), request.steps(), updatedBy);
    }

    @PostMapping("/api/projects/{projectId}/scripts/{scriptId}/publish")
    public ScriptVersion publish(
            @PathVariable long projectId,
            @PathVariable long scriptId,
            @Valid @RequestBody PublishRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String publishedBy
    ) {
        return publicationService.publish(projectId, scriptId, request.versionNo(), request.remark(), publishedBy);
    }

    @PostMapping("/api/projects/{projectId}/scripts/{scriptId}/fork-draft")
    @ResponseStatus(HttpStatus.CREATED)
    public ScriptVersion forkDraft(
            @PathVariable long projectId,
            @PathVariable long scriptId,
            @Valid @RequestBody ForkDraftRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String updatedBy
    ) {
        return publicationService.forkDraft(projectId, scriptId, request.sourceVersionId(), updatedBy);
    }

    public record SaveDraftRequest(
            String filename,
            List<ScriptStepDefinition> steps,
            String content
    ) {
    }

    public record PublishRequest(
            @NotNull Integer versionNo,
            String remark
    ) {
    }

    public record ForkDraftRequest(
            @NotNull Long sourceVersionId
    ) {
    }
}
