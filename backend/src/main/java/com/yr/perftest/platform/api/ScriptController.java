package com.yr.perftest.platform.api;

import com.yr.perftest.platform.script.ScriptService;
import com.yr.perftest.platform.script.ScriptVersion;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScriptVersion uploadScript(
            @PathVariable long projectId,
            MultipartFile file,
            @RequestHeader(name = "X-User", defaultValue = "admin") String uploadedBy
    ) {
        return scriptService.uploadScript(projectId, file, uploadedBy);
    }
}
