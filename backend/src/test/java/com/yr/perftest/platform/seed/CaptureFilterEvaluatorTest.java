package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureFilterEvaluatorTest {

    @Test
    void rejectsEmptyInclude() {
        assertThatThrownBy(() -> CaptureFilterEvaluator.evaluate(
                List.of("app.users", "app.orders", "app.order_items", "app.order_audit"),
                List.of(),
                List.of()
        )).isInstanceOf(SeedValidationException.class)
                .hasMessageContaining("include");
    }

    @Test
    void hybridExactAndWildcardExcludeWins() {
        Set<String> result = CaptureFilterEvaluator.evaluate(
                List.of("app.users", "app.orders", "app.order_items", "app.order_audit", "app.other"),
                List.of("app.users", "app.order*"),
                List.of("app.order_audit")
        );
        assertThat(result).containsExactlyInAnyOrder("app.users", "app.orders", "app.order_items");
    }

    @Test
    void regexExpressionMatches() {
        Set<String> result = CaptureFilterEvaluator.evaluate(
                List.of("app.t_biz_a", "app.t_biz_b", "app.t_tmp"),
                List.of("regex:app\\.t_biz_.*"),
                List.of()
        );
        assertThat(result).containsExactlyInAnyOrder("app.t_biz_a", "app.t_biz_b");
    }
}
