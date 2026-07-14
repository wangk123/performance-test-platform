package com.yr.perftest.platform.api;

import com.yr.perftest.platform.llm.LlmModel;
import com.yr.perftest.platform.llm.LlmModelService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/llm")
public class LlmModelController {
    private final LlmModelService modelService;

    public LlmModelController(LlmModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping("/models")
    public List<LlmModel> list(@RequestParam(required = false) Long providerId) {
        return modelService.list(providerId);
    }

    @GetMapping("/models/{id}")
    public LlmModel get(@PathVariable long id) {
        return modelService.get(id);
    }

    @PostMapping("/models")
    @ResponseStatus(HttpStatus.CREATED)
    public LlmModel create(@RequestBody LlmModelService.CreateModelRequest request) {
        return modelService.create(request);
    }

    @PutMapping("/models/{id}")
    public LlmModel update(@PathVariable long id, @RequestBody LlmModelService.UpdateModelRequest request) {
        return modelService.update(id, request);
    }

    @DeleteMapping("/models/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        modelService.delete(id);
    }

    @PutMapping("/models/{id}/default")
    public LlmModel setDefault(@PathVariable long id) {
        return modelService.setDefault(id);
    }

    @GetMapping("/available-models")
    public List<LlmModelService.AvailableProviderGroup> availableModels() {
        return modelService.listAvailable();
    }
}
