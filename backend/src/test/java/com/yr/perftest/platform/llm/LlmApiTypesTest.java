package com.yr.perftest.platform.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmApiTypesTest {
    @Test
    void encodesAndDecodesMultipleTypes() {
        String encoded = LlmApiTypes.encode(List.of(LlmApiType.OPENAI, LlmApiType.ANTHROPIC));
        assertThat(encoded).isEqualTo("OPENAI,ANTHROPIC");
        assertThat(LlmApiTypes.decode(encoded)).containsExactly(LlmApiType.OPENAI, LlmApiType.ANTHROPIC);
    }

    @Test
    void defaultsToOpenAiAndAcceptsLegacySingle() {
        assertThat(LlmApiTypes.normalize(null, null)).containsExactly(LlmApiType.OPENAI);
        assertThat(LlmApiTypes.normalize(null, LlmApiType.ANTHROPIC)).containsExactly(LlmApiType.ANTHROPIC);
    }

    @Test
    void resolvePrefersOpenAiWhenBothSupported() {
        List<LlmApiType> both = List.of(LlmApiType.OPENAI, LlmApiType.ANTHROPIC);
        assertThat(LlmApiTypes.resolve(both, null)).isEqualTo(LlmApiType.OPENAI);
        assertThat(LlmApiTypes.resolve(both, LlmApiType.ANTHROPIC)).isEqualTo(LlmApiType.ANTHROPIC);
        assertThatThrownBy(() -> LlmApiTypes.resolve(List.of(LlmApiType.OPENAI), LlmApiType.ANTHROPIC))
                .isInstanceOf(LlmValidationException.class);
    }

    @Test
    void mergeAddsProtocol() {
        assertThat(LlmApiTypes.merge(List.of(LlmApiType.OPENAI), LlmApiType.ANTHROPIC))
                .containsExactly(LlmApiType.OPENAI, LlmApiType.ANTHROPIC);
    }
}
