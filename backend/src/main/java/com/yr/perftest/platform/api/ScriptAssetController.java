package com.yr.perftest.platform.api;

import com.yr.perftest.platform.script.ScriptAssetSummary;
import com.yr.perftest.platform.script.ScriptQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ScriptAssetController {
    private final ScriptQueryService queryService;

    public ScriptAssetController(ScriptQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/api/projects/{projectId}/scripts/assets")
    public List<ScriptAssetSummary> listAssets(@PathVariable long projectId) {
        return queryService.listAssets(projectId);
    }

    @GetMapping("/api/projects/{projectId}/scripts/{scriptId:\\d+}/versions")
    public List<ScriptQueryService.ScriptVersionWithRefs> listVersions(
            @PathVariable long projectId,
            @PathVariable long scriptId
    ) {
        return queryService.listVersions(projectId, scriptId);
    }
}
