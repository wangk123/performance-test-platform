package com.yr.perftest.platform.api;

import com.yr.perftest.platform.auxscript.AuxScriptBindingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场景辅助脚本绑定接口（模块 09）：替换式保存场景前置/后置脚本绑定。
 */
@RestController
public class AuxScriptBindingController {
    private final AuxScriptBindingService bindingService;

    public AuxScriptBindingController(AuxScriptBindingService bindingService) {
        this.bindingService = bindingService;
    }

    @PutMapping("/api/scenarios/{scenarioId}/aux-scripts")
    public List<AuxScriptBindingService.BindingView> replaceBindings(
            @PathVariable long scenarioId,
            @RequestBody ReplaceBindingsRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        return bindingService.replaceBindings(scenarioId, request.bindings(), operatorUsername);
    }

    @GetMapping("/api/scenarios/{scenarioId}/aux-scripts")
    public List<AuxScriptBindingService.BindingView> list(@PathVariable long scenarioId) {
        return bindingService.listBindings(scenarioId);
    }

    public record ReplaceBindingsRequest(List<AuxScriptBindingService.BindingInput> bindings) {
    }
}
