package com.yr.perftest.platform.llm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:llm-domain-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LlmDomainServiceTest {
    @Autowired
    private LlmProviderService providerService;
    @Autowired
    private LlmModelService modelService;

    @Test
    void createsProviderWithMaskedApiKeyAndOptionalAnthropicUrl() {
        LlmProvider provider = providerService.create(new LlmProviderService.CreateProviderRequest(
                "DeepSeek",
                "https://api.deepseek.com/v1",
                null,
                "sk-secret",
                true,
                false
        ));

        assertThat(provider.name()).isEqualTo("DeepSeek");
        assertThat(provider.baseUrl()).isEqualTo("https://api.deepseek.com/v1");
        assertThat(provider.baseUrlAnthropic()).isNull();
        assertThat(provider.apiKeyConfigured()).isTrue();
        assertThat(provider).hasNoNullFieldsOrPropertiesExcept("baseUrlAnthropic");
        assertThat(provider.toString()).doesNotContain("sk-secret");
    }

    @Test
    void updateKeepsApiKeyWhenBlank() {
        LlmProvider created = providerService.create(new LlmProviderService.CreateProviderRequest(
                "P1", "https://example.com/v1", null, "sk-old", true, false));

        LlmProvider updated = providerService.update(created.id(), new LlmProviderService.UpdateProviderRequest(
                "P1", "https://example.com/v1", "https://example.com/anthropic", null, true, true));

        assertThat(updated.apiKeyConfigured()).isTrue();
        assertThat(updated.baseUrlAnthropic()).isEqualTo("https://example.com/anthropic");
        assertThat(updated.storeBodyDefault()).isTrue();
        assertThat(providerService.requireApiKey(created.id())).isEqualTo("sk-old");
    }

    @Test
    void modelDefaultsToOpenAiAndAllowsSameNameAcrossProviders() {
        LlmProvider a = providerService.create(new LlmProviderService.CreateProviderRequest(
                "A", "https://a.example/v1", null, "sk-a", true, false));
        LlmProvider b = providerService.create(new LlmProviderService.CreateProviderRequest(
                "B", "https://b.example/v1", null, "sk-b", true, false));

        LlmModel ma = modelService.create(new LlmModelService.CreateModelRequest(
                a.id(), "deepseek-v4-flash", null, null, true));
        LlmModel mb = modelService.create(new LlmModelService.CreateModelRequest(
                b.id(), "deepseek-v4-flash", "Flash", LlmApiType.ANTHROPIC, true));

        assertThat(ma.apiTypes()).containsExactly(LlmApiType.OPENAI);
        assertThat(mb.apiTypes()).containsExactly(LlmApiType.ANTHROPIC);
        assertThat(ma.id()).isNotEqualTo(mb.id());
    }

    @Test
    void modelCanSupportBothProtocolsAndImportMerges() {
        LlmProvider provider = providerService.create(new LlmProviderService.CreateProviderRequest(
                "P", "https://p.example/v1", null, "sk", true, false));
        LlmModel created = modelService.create(new LlmModelService.CreateModelRequest(
                provider.id(),
                "dual-model",
                null,
                null,
                List.of(LlmApiType.OPENAI, LlmApiType.ANTHROPIC),
                true
        ));
        assertThat(created.apiTypes()).containsExactly(LlmApiType.OPENAI, LlmApiType.ANTHROPIC);

        modelService.importModels(provider.id(), LlmApiType.ANTHROPIC, List.of(
                new LlmModelService.ImportModelItem("only-openai", null)
        ));
        modelService.importModels(provider.id(), LlmApiType.OPENAI, List.of(
                new LlmModelService.ImportModelItem("only-openai", null)
        ));
        LlmModel merged = modelService.list(provider.id()).stream()
                .filter(m -> m.modelName().equals("only-openai"))
                .findFirst()
                .orElseThrow();
        assertThat(merged.apiTypes()).containsExactly(LlmApiType.ANTHROPIC, LlmApiType.OPENAI);
    }

    @Test
    void settingDefaultClearsPreviousDefault() {
        LlmProvider provider = providerService.create(new LlmProviderService.CreateProviderRequest(
                "P", "https://p.example/v1", null, "sk", true, false));
        LlmModel first = modelService.create(new LlmModelService.CreateModelRequest(
                provider.id(), "m1", null, LlmApiType.OPENAI, true));
        LlmModel second = modelService.create(new LlmModelService.CreateModelRequest(
                provider.id(), "m2", null, LlmApiType.OPENAI, true));

        modelService.setDefault(first.id());
        modelService.setDefault(second.id());

        assertThat(modelService.get(first.id()).isDefault()).isFalse();
        assertThat(modelService.get(second.id()).isDefault()).isTrue();
    }

    @Test
    void deleteProviderWithoutCascadeConflictsWhenModelsExist() {
        LlmProvider provider = providerService.create(new LlmProviderService.CreateProviderRequest(
                "P", "https://p.example/v1", null, "sk", true, false));
        modelService.create(new LlmModelService.CreateModelRequest(
                provider.id(), "m1", null, LlmApiType.OPENAI, true));

        assertThatThrownBy(() -> providerService.delete(provider.id(), false))
                .isInstanceOf(LlmConflictException.class);

        providerService.delete(provider.id(), true);
        assertThat(modelService.list(provider.id())).isEmpty();
        assertThatThrownBy(() -> providerService.get(provider.id()))
                .isInstanceOf(LlmValidationException.class);
    }
}
