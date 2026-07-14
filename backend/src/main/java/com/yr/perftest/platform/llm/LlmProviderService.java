package com.yr.perftest.platform.llm;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LlmProviderService {
    private final PersistentModelProviderRepository providerRepository;
    private final PersistentModelDefinitionRepository modelRepository;

    public LlmProviderService(
            PersistentModelProviderRepository providerRepository,
            PersistentModelDefinitionRepository modelRepository
    ) {
        this.providerRepository = providerRepository;
        this.modelRepository = modelRepository;
    }

    @Transactional(readOnly = true)
    public List<LlmProvider> list() {
        return providerRepository.findAllByOrderByIdDesc().stream().map(PersistentModelProviderRecord::toProvider).toList();
    }

    @Transactional(readOnly = true)
    public LlmProvider get(long id) {
        return require(id).toProvider();
    }

    @Transactional(readOnly = true)
    public String requireApiKey(long id) {
        return require(id).getApiKey();
    }

    @Transactional(readOnly = true)
    public PersistentModelProviderRecord requireRecord(long id) {
        return require(id);
    }

    @Transactional
    public LlmProvider create(CreateProviderRequest request) {
        String name = required(request.name(), "name is required");
        String baseUrl = required(request.baseUrl(), "baseUrl is required");
        String apiKey = required(request.apiKey(), "apiKey is required");
        if (providerRepository.findByName(name).isPresent()) {
            throw new LlmValidationException("provider name already exists");
        }
        PersistentModelProviderRecord record = new PersistentModelProviderRecord(
                name,
                trimTrailingSlash(baseUrl),
                blankToNull(request.baseUrlAnthropic()),
                apiKey,
                request.enabled() == null || request.enabled(),
                Boolean.TRUE.equals(request.storeBodyDefault())
        );
        return providerRepository.save(record).toProvider();
    }

    @Transactional
    public LlmProvider update(long id, UpdateProviderRequest request) {
        PersistentModelProviderRecord record = require(id);
        String name = required(request.name(), "name is required");
        String baseUrl = required(request.baseUrl(), "baseUrl is required");
        providerRepository.findByName(name).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new LlmValidationException("provider name already exists");
            }
        });
        record.update(
                name,
                trimTrailingSlash(baseUrl),
                blankToNull(request.baseUrlAnthropic()),
                request.apiKey(),
                request.enabled() == null || request.enabled(),
                Boolean.TRUE.equals(request.storeBodyDefault())
        );
        return providerRepository.save(record).toProvider();
    }

    @Transactional
    public void delete(long id, boolean cascade) {
        require(id);
        long modelCount = modelRepository.countByProviderId(id);
        if (modelCount > 0 && !cascade) {
            throw new LlmConflictException("provider has " + modelCount + " models; confirm cascade delete");
        }
        if (modelCount > 0) {
            modelRepository.deleteAllByProviderId(id);
        }
        providerRepository.deleteById(id);
    }

    public String resolveBaseUrl(PersistentModelProviderRecord provider, LlmApiType apiType) {
        if (apiType == LlmApiType.ANTHROPIC) {
            String anthropic = blankToNull(provider.getBaseUrlAnthropic());
            if (anthropic != null) {
                return trimTrailingSlash(anthropic);
            }
        }
        return trimTrailingSlash(provider.getBaseUrl());
    }

    private PersistentModelProviderRecord require(long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new LlmValidationException("provider not found: " + id));
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

    private static String trimTrailingSlash(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public record CreateProviderRequest(
            String name,
            String baseUrl,
            String baseUrlAnthropic,
            String apiKey,
            Boolean enabled,
            Boolean storeBodyDefault
    ) {
    }

    public record UpdateProviderRequest(
            String name,
            String baseUrl,
            String baseUrlAnthropic,
            String apiKey,
            Boolean enabled,
            Boolean storeBodyDefault
    ) {
    }
}
