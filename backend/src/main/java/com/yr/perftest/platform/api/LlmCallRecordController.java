package com.yr.perftest.platform.api;

import com.yr.perftest.platform.llm.LlmCallRecord;
import com.yr.perftest.platform.llm.LlmCallRecordService;
import com.yr.perftest.platform.llm.LlmCallScene;
import com.yr.perftest.platform.llm.LlmCallStatus;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/llm/call-records")
public class LlmCallRecordController {
    private final LlmCallRecordService callRecordService;

    public LlmCallRecordController(LlmCallRecordService callRecordService) {
        this.callRecordService = callRecordService;
    }

    @GetMapping
    public Map<String, Object> page(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false) LlmCallScene scene,
            @RequestParam(required = false) LlmCallStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<LlmCallRecord> result = callRecordService.page(providerId, modelId, scene, status, page, size);
        return Map.of(
                "content", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        );
    }
}
