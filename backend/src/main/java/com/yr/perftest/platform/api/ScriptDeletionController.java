package com.yr.perftest.platform.api;

import com.yr.perftest.platform.script.ScriptDeletionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScriptDeletionController {
    private final ScriptDeletionService deletionService;

    public ScriptDeletionController(ScriptDeletionService deletionService) {
        this.deletionService = deletionService;
    }

    @DeleteMapping("/api/projects/{projectId}/scripts/{scriptId:\\d+}/versions/{versionId:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVersion(
            @PathVariable long projectId,
            @PathVariable long scriptId,
            @PathVariable long versionId
    ) {
        deletionService.deleteVersion(projectId, versionId);
    }

    @DeleteMapping("/api/projects/{projectId}/scripts/{scriptId:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScript(
            @PathVariable long projectId,
            @PathVariable long scriptId
    ) {
        deletionService.deleteScript(projectId, scriptId);
    }
}
