package com.yr.perftest.platform.llm;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LlmGateway {
    private static final int BODY_LIMIT = 256 * 1024;

    private final LlmProviderService providerService;
    private final LlmModelService modelService;
    private final LlmCallRecordService callRecordService;
    private final OpenAiCompatibleAdapter openAiAdapter;
    private final AnthropicAdapter anthropicAdapter;

    public LlmGateway(
            LlmProviderService providerService,
            LlmModelService modelService,
            LlmCallRecordService callRecordService,
            OpenAiCompatibleAdapter openAiAdapter,
            AnthropicAdapter anthropicAdapter
    ) {
        this.providerService = providerService;
        this.modelService = modelService;
        this.callRecordService = callRecordService;
        this.openAiAdapter = openAiAdapter;
        this.anthropicAdapter = anthropicAdapter;
    }

    @Transactional
    public InvokeResult invoke(InvokeRequest request) {
        PersistentModelDefinitionRecord model = modelService.requireRecord(request.modelId());
        PersistentModelProviderRecord provider = providerService.requireRecord(model.getProviderId());
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            throw new LlmValidationException("provider is disabled");
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new LlmValidationException("model is disabled");
        }
        LlmApiType apiType = LlmApiTypes.resolve(model.getApiTypes(), request.apiType());
        String baseUrl = providerService.resolveBaseUrl(provider, apiType);
        LlmAdapter adapter = apiType == LlmApiType.ANTHROPIC ? anthropicAdapter : openAiAdapter;
        boolean storeBody = request.storeBody() != null
                ? request.storeBody()
                : Boolean.TRUE.equals(provider.getStoreBodyDefault());
        LlmCallScene scene = request.scene() == null ? LlmCallScene.TEST_CONNECTION : request.scene();
        List<LlmChatMessage> messages = request.messages() == null || request.messages().isEmpty()
                ? List.of(new LlmChatMessage("user", "ping"))
                : request.messages();

        long started = System.currentTimeMillis();
        try {
            LlmChatResult chat = adapter.chat(baseUrl, provider.getApiKey(), model.getModelName(), messages);
            long latency = System.currentTimeMillis() - started;
            LlmCallRecord record = callRecordService.save(new PersistentModelCallRecord(
                    provider.getId(),
                    model.getId(),
                    provider.getName(),
                    model.getModelName(),
                    apiType,
                    scene,
                    LlmCallStatus.SUCCESS,
                    latency,
                    chat.promptTokens(),
                    chat.completionTokens(),
                    chat.totalTokens(),
                    null,
                    storeBody ? truncate(chat.rawRequest()) : null,
                    storeBody ? truncate(chat.rawResponse()) : null,
                    request.triggeredBy()
            ));
            return new InvokeResult(true, chat.content(), latency, record.id(), null);
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - started;
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            LlmCallRecord record = callRecordService.save(new PersistentModelCallRecord(
                    provider.getId(),
                    model.getId(),
                    provider.getName(),
                    model.getModelName(),
                    apiType,
                    scene,
                    LlmCallStatus.FAILED,
                    latency,
                    null,
                    null,
                    null,
                    truncate(message, 2000),
                    null,
                    null,
                    request.triggeredBy()
            ));
            return new InvokeResult(false, null, latency, record.id(), message);
        }
    }

    public List<String> fetchModels(long providerId, LlmApiType apiType) {
        PersistentModelProviderRecord provider = providerService.requireRecord(providerId);
        LlmApiType type = apiType == null ? LlmApiType.OPENAI : apiType;
        String baseUrl = providerService.resolveBaseUrl(provider, type);
        LlmAdapter adapter = type == LlmApiType.ANTHROPIC ? anthropicAdapter : openAiAdapter;
        try {
            return adapter.listModels(baseUrl, provider.getApiKey());
        } catch (Exception ex) {
            throw new LlmValidationException(ex.getMessage() == null ? "fetch models failed" : ex.getMessage());
        }
    }

    private static String truncate(String value) {
        return truncate(value, BODY_LIMIT);
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...[truncated]";
    }

    public record InvokeRequest(
            long modelId,
            LlmCallScene scene,
            List<LlmChatMessage> messages,
            Boolean storeBody,
            String triggeredBy,
            LlmApiType apiType
    ) {
        public InvokeRequest(
                long modelId,
                LlmCallScene scene,
                List<LlmChatMessage> messages,
                Boolean storeBody,
                String triggeredBy
        ) {
            this(modelId, scene, messages, storeBody, triggeredBy, null);
        }
    }

    public record InvokeResult(
            boolean success,
            String content,
            long latencyMs,
            long callRecordId,
            String errorMessage
    ) {
    }
}
