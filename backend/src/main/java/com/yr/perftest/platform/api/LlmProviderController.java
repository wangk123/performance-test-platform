package com.yr.perftest.platform.api;

import com.yr.perftest.platform.llm.LlmApiType;
import com.yr.perftest.platform.llm.LlmCallScene;
import com.yr.perftest.platform.llm.LlmChatMessage;
import com.yr.perftest.platform.llm.LlmGateway;
import com.yr.perftest.platform.llm.LlmModel;
import com.yr.perftest.platform.llm.LlmModelService;
import com.yr.perftest.platform.llm.LlmProvider;
import com.yr.perftest.platform.llm.LlmProviderService;
import com.yr.perftest.platform.llm.PersistentModelDefinitionRecord;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/llm/providers")
public class LlmProviderController {
    private final LlmProviderService providerService;
    private final LlmModelService modelService;
    private final LlmGateway gateway;

    public LlmProviderController(
            LlmProviderService providerService,
            LlmModelService modelService,
            LlmGateway gateway
    ) {
        this.providerService = providerService;
        this.modelService = modelService;
        this.gateway = gateway;
    }

    @GetMapping
    public List<LlmProvider> list() {
        return providerService.list();
    }

    @GetMapping("/{id}")
    public LlmProvider get(@PathVariable long id) {
        return providerService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LlmProvider create(@RequestBody LlmProviderService.CreateProviderRequest request) {
        return providerService.create(request);
    }

    @PutMapping("/{id}")
    public LlmProvider update(@PathVariable long id, @RequestBody LlmProviderService.UpdateProviderRequest request) {
        return providerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @RequestParam(defaultValue = "false") boolean cascade) {
        providerService.delete(id, cascade);
    }

    @PostMapping("/{id}/fetch-models")
    public Map<String, Object> fetchModels(@PathVariable long id, @RequestBody(required = false) FetchModelsRequest request) {
        LlmApiType apiType = request == null || request.apiType() == null ? LlmApiType.OPENAI : request.apiType();
        List<String> models = gateway.fetchModels(id, apiType);
        return Map.of("apiType", apiType, "models", models);
    }

    @PostMapping("/{id}/import-models")
    public List<LlmModel> importModels(@PathVariable long id, @RequestBody ImportModelsRequest request) {
        LlmApiType apiType = request.apiType() == null ? LlmApiType.OPENAI : request.apiType();
        List<LlmModelService.ImportModelItem> items = request.models() == null
                ? List.of()
                : request.models().stream()
                .map(item -> new LlmModelService.ImportModelItem(item.modelName(), item.displayName()))
                .toList();
        return modelService.importModels(id, apiType, items);
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(
            @PathVariable long id,
            @RequestBody(required = false) TestRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String user
    ) {
        Long modelId = request == null ? null : request.modelId();
        PersistentModelDefinitionRecord model = modelService.resolveForProviderTest(id, modelId);
        LlmGateway.InvokeResult result = gateway.invoke(new LlmGateway.InvokeRequest(
                model.getId(),
                LlmCallScene.TEST_CONNECTION,
                List.of(new LlmChatMessage("user", "ping")),
                request == null ? null : request.storeBody(),
                user,
                request == null ? null : request.apiType()
        ));
        return Map.of(
                "success", result.success(),
                "latencyMs", result.latencyMs(),
                "callRecordId", result.callRecordId(),
                "content", result.content() == null ? "" : result.content(),
                "errorMessage", result.errorMessage() == null ? "" : result.errorMessage()
        );
    }

    public record FetchModelsRequest(LlmApiType apiType) {
    }

    public record ImportModelsRequest(LlmApiType apiType, List<ImportModelItem> models) {
    }

    public record ImportModelItem(String modelName, String displayName) {
    }

    public record TestRequest(Long modelId, Boolean storeBody, LlmApiType apiType) {
    }
}
