package com.yr.perftest.platform.api;

import com.yr.perftest.platform.script.ScriptContent;
import com.yr.perftest.platform.script.ScriptDefinition;
import com.yr.perftest.platform.script.ScriptService;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import com.yr.perftest.platform.script.ScriptVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/scripts")
public class ScriptController {
    private final ScriptService scriptService;

    public ScriptController(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @GetMapping
    public List<ScriptVersion> listScripts(@PathVariable long projectId) {
        return scriptService.listScripts(projectId);
    }

    @GetMapping("/definitions")
    public List<ScriptDefinition> listScriptDefinitions(@PathVariable long projectId) {
        return scriptService.listScriptDefinitions(projectId);
    }

    @GetMapping("/{versionId:\\d+}")
    public ScriptContent getScriptContent(@PathVariable long projectId, @PathVariable long versionId) {
        return scriptService.getScriptContent(projectId, versionId);
    }

    @GetMapping("/{versionId:\\d+}/definition")
    public ScriptDefinition getScriptDefinition(@PathVariable long projectId, @PathVariable long versionId) {
        return scriptService.getScriptDefinition(projectId, versionId);
    }

    @DeleteMapping("/{versionId:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScript(@PathVariable long projectId, @PathVariable long versionId) {
        scriptService.deleteScript(projectId, versionId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScriptVersion uploadScript(
            @PathVariable long projectId,
            MultipartFile file,
            @RequestHeader(name = "X-User", defaultValue = "admin") String uploadedBy
    ) {
        return scriptService.uploadScript(projectId, file, uploadedBy);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScriptDefinition createScript(
            @PathVariable long projectId,
            @Valid @RequestBody CreateScriptRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String uploadedBy
    ) {
        return scriptService.createScript(projectId, request.name(), uploadedBy);
    }

    @PutMapping("/{versionId:\\d+}")
    @ResponseStatus(HttpStatus.CREATED)
    public ScriptVersion saveScriptContent(
            @PathVariable long projectId,
            @PathVariable long versionId,
            @Valid @RequestBody SaveScriptRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String uploadedBy
    ) {
        return scriptService.saveScriptContent(projectId, versionId, request.content(), request.filename(), uploadedBy);
    }

    @PutMapping("/{versionId:\\d+}/definition")
    @ResponseStatus(HttpStatus.CREATED)
    public ScriptDefinition saveScriptDefinition(
            @PathVariable long projectId,
            @PathVariable long versionId,
            @Valid @RequestBody SaveScriptDefinitionRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String uploadedBy
    ) {
        return scriptService.saveScriptDefinition(projectId, versionId, request.filename(), request.steps(), uploadedBy);
    }

    public record CreateScriptRequest(
            @NotBlank String name
    ) {
    }

    public record SaveScriptRequest(
            @NotBlank String content,
            String filename
    ) {
    }

    public record SaveScriptDefinitionRequest(
            String filename,
            List<ScriptStepDefinition> steps
    ) {
    }
}
