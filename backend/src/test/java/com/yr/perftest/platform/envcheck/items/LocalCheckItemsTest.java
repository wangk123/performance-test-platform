package com.yr.perftest.platform.envcheck.items;

import com.yr.perftest.platform.envcheck.LocalCheckItem;
import com.yr.perftest.platform.envcheck.LocalCheckContext;
import com.yr.perftest.platform.envcheck.LocalCheckContext.ScenarioRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** LOCAL 项判定（迁移自 PlanWorkflowService runPrecheck 的 switch-case，逻辑不变）。 */
class LocalCheckItemsTest {

    private static final String BODY = """
            ## 三、测试指标

            | 指标 | 目标 |
            |---|---|
            | TPS | 1000 |
            """;

    @Test
    void metricsDefinedRequiresTableInSection() {
        var item = new DocMetricsDefinedItem();
        assertThat(item.check(new LocalCheckContext(BODY, List.of())).ok()).isTrue();
        assertThat(item.check(new LocalCheckContext("## 三、测试指标\n\n文字无表", List.of())).ok()).isFalse();
        assertThat(item.check(new LocalCheckContext("", List.of())).ok()).isFalse();
    }

    @Test
    void scenariosConfiguredRequiresNonEmpty() {
        LocalCheckItem item = new DocScenariosConfiguredItem();
        assertThat(item.check(new LocalCheckContext("", List.of())).ok()).isFalse();
        assertThat(item.check(new LocalCheckContext("", List.of(new ScenarioRow("s1", null)))).ok()).isTrue();
    }

    @Test
    void scriptBoundRequiresAllScenariosBound() {
        var item = new DocScriptBoundItem();
        assertThat(item.check(new LocalCheckContext("", List.of(new ScenarioRow("s1", 9L)))).ok()).isTrue();
        assertThat(item.check(new LocalCheckContext("", List.of(new ScenarioRow("s1", 9L), new ScenarioRow("s2", null)))).ok()).isFalse();
    }
}
