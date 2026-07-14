package com.yr.perftest.platform.llm;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class LlmModelService {
    private final PersistentModelDefinitionRepository modelRepository;
    private final PersistentModelProviderRepository providerRepository;

    public LlmModelService(
            PersistentModelDefinitionRepository modelRepository,
            PersistentModelProviderRepository providerRepository
    ) {
        this.modelRepository = modelRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<LlmModel> list(Long providerId) {
        List<PersistentModelDefinitionRecord> records = providerId == null
                ? modelRepository.findAllByOrderByIdDesc()
                : modelRepository.findAllByProviderIdOrderByIdDesc(providerId);
        return records.stream().map(PersistentModelDefinitionRecord::toModel).toList();
    }

    @Transactional(readOnly = true)
    public LlmModel get(long id) {
        return require(id).toModel();
    }

    @Transactional(readOnly = true)
    public PersistentModelDefinitionRecord requireRecord(long id) {
        return require(id);
    }

    @Transactional
    public LlmModel create(CreateModelRequest request) {
        long providerId = request.providerId();
        if (!providerRepository.existsById(providerId)) {
            throw new LlmValidationException("provider not found: " + providerId);
        }
        String modelName = required(request.modelName(), "modelName is required");
        if (modelRepository.findByProviderIdAndModelName(providerId, modelName).isPresent()) {
            throw new LlmValidationException("modelName already exists under provider");
        }
        List<LlmApiType> apiTypes = LlmApiTypes.normalize(request.apiTypes(), request.apiType());
        PersistentModelDefinitionRecord record = new PersistentModelDefinitionRecord(
                providerId,
                modelName,
                blankToNull(request.displayName()),
                apiTypes,
                request.enabled() == null || request.enabled()
        );
        return modelRepository.save(record).toModel();
    }

    @Transactional
    public LlmModel update(long id, UpdateModelRequest request) {
        PersistentModelDefinitionRecord record = require(id);
        List<LlmApiType> apiTypes = request.apiTypes() != null || request.apiType() != null
                ? LlmApiTypes.normalize(request.apiTypes(), request.apiType())
                : record.getApiTypes();
        record.update(
                blankToNull(request.displayName()),
                apiTypes,
                request.enabled() == null || request.enabled()
        );
        return modelRepository.save(record).toModel();
    }

    @Transactional
    public void delete(long id) {
        require(id);
        modelRepository.deleteById(id);
    }

    @Transactional
    public LlmModel setDefault(long id) {
        PersistentModelDefinitionRecord record = require(id);
        modelRepository.clearAllDefaults();
        record.setDefault(true);
        return modelRepository.save(record).toModel();
    }

    @Transactional
    public List<LlmModel> importModels(long providerId, LlmApiType apiType, List<ImportModelItem> items) {
        if (!providerRepository.existsById(providerId)) {
            throw new LlmValidationException("provider not found: " + providerId);
        }
        LlmApiType type = apiType == null ? LlmApiType.OPENAI : apiType;
        List<LlmModel> imported = new ArrayList<>();
        for (ImportModelItem item : items) {
            String modelName = required(item.modelName(), "modelName is required");
            var existing = modelRepository.findByProviderIdAndModelName(providerId, modelName);
            if (existing.isPresent()) {
                PersistentModelDefinitionRecord record = existing.get();
                List<LlmApiType> merged = LlmApiTypes.merge(record.getApiTypes(), type);
                if (!merged.equals(record.getApiTypes())) {
                    record.setApiTypes(merged);
                    imported.add(modelRepository.save(record).toModel());
                }
                continue;
            }
            PersistentModelDefinitionRecord record = new PersistentModelDefinitionRecord(
                    providerId,
                    modelName,
                    blankToNull(item.displayName()),
                    List.of(type),
                    true
            );
            imported.add(modelRepository.save(record).toModel());
        }
        return imported;
    }

    @Transactional(readOnly = true)
    public List<AvailableProviderGroup> listAvailable() {
        List<AvailableProviderGroup> groups = new ArrayList<>();
        for (PersistentModelProviderRecord provider : providerRepository.findAllByEnabledTrueOrderByIdAsc()) {
            List<AvailableModelItem> models = modelRepository
                    .findAllByProviderIdAndEnabledTrueOrderByIdAsc(provider.getId())
                    .stream()
                    .map(m -> {
                        List<LlmApiType> types = m.getApiTypes();
                        return new AvailableModelItem(
                                m.getId(),
                                m.getModelName(),
                                m.getDisplayName(),
                                types,
                                types.get(0),
                                Boolean.TRUE.equals(m.getIsDefault())
                        );
                    })
                    .toList();
            if (!models.isEmpty()) {
                groups.add(new AvailableProviderGroup(provider.getId(), provider.getName(), models));
            }
        }
        return groups;
    }

    @Transactional(readOnly = true)
    public PersistentModelDefinitionRecord resolveForProviderTest(long providerId, Long modelId) {
        if (modelId != null) {
            PersistentModelDefinitionRecord model = require(modelId);
            if (!model.getProviderId().equals(providerId)) {
                throw new LlmValidationException("model does not belong to provider");
            }
            if (!Boolean.TRUE.equals(model.getEnabled())) {
                throw new LlmValidationException("model is disabled");
            }
            return model;
        }
        return modelRepository.findFirstByProviderIdAndEnabledTrueAndIsDefaultTrue(providerId)
                .or(() -> modelRepository.findFirstByProviderIdAndEnabledTrueOrderByIdAsc(providerId))
                .orElseThrow(() -> new LlmValidationException("no enabled model under provider"));
    }

    private PersistentModelDefinitionRecord require(long id) {
        return modelRepository.findById(id)
                .orElseThrow(() -> new LlmValidationException("model not found: " + id));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new LlmValidationException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateModelRequest(
            Long providerId,
            String modelName,
            String displayName,
            LlmApiType apiType,
            List<LlmApiType> apiTypes,
            Boolean enabled
    ) {
        public CreateModelRequest(Long providerId, String modelName, String displayName, LlmApiType apiType, Boolean enabled) {
            this(providerId, modelName, displayName, apiType, null, enabled);
        }
    }

    public record UpdateModelRequest(
            String displayName,
            LlmApiType apiType,
            List<LlmApiType> apiTypes,
            Boolean enabled
    ) {
        public UpdateModelRequest(String displayName, LlmApiType apiType, Boolean enabled) {
            this(displayName, apiType, null, enabled);
        }
    }

    public record ImportModelItem(String modelName, String displayName) {
    }

    public record AvailableModelItem(
            long modelId,
            String modelName,
            String displayName,
            List<LlmApiType> apiTypes,
            LlmApiType apiType,
            boolean isDefault
    ) {
    }

    public record AvailableProviderGroup(
            long providerId,
            String providerName,
            List<AvailableModelItem> models
    ) {
    }
}
