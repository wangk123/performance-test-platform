package com.yr.perftest.platform.api;

import com.yr.perftest.platform.auxscript.AuxScriptScope;
import com.yr.perftest.platform.auxscript.AuxScriptService;
import com.yr.perftest.platform.auxscript.AuxScriptType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 辅助脚本管理接口（模块 09）。
 */
@RestController
public class AuxScriptController {
    private final AuxScriptService auxScriptService;

    public AuxScriptController(AuxScriptService auxScriptService) {
        this.auxScriptService = auxScriptService;
    }

    @GetMapping("/api/projects/{projectId}/aux-scripts")
    public List<AuxScriptService.AuxScriptView> list(@PathVariable long projectId) {
        return auxScriptService.listScripts(projectId);
    }

    @PostMapping("/api/projects/{projectId}/aux-scripts")
    @ResponseStatus(HttpStatus.CREATED)
    public AuxScriptService.AuxScriptView create(
            @PathVariable long projectId,
            @RequestBody CreateAuxScriptRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        return auxScriptService.createScript(
                projectId,
                request.name(),
                request.type(),
                request.scope(),
                request.description(),
                operatorUsername
        );
    }

    @GetMapping("/api/aux-scripts/{scriptId}")
    public AuxScriptService.AuxScriptView get(@PathVariable long scriptId) {
        return auxScriptService.getScript(scriptId);
    }

    @PostMapping("/api/aux-scripts/{scriptId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public AuxScriptService.AuxScriptVersionView addVersion(
            @PathVariable long scriptId,
            @RequestBody AddVersionRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        return auxScriptService.addVersion(
                scriptId,
                request.sourceCode(),
                request.remark(),
                operatorUsername
        );
    }

    public record CreateAuxScriptRequest(
            String name,
            AuxScriptType type,
            AuxScriptScope scope,
            String description
    ) {
    }

    public record AddVersionRequest(String sourceCode, String remark) {
    }
}
