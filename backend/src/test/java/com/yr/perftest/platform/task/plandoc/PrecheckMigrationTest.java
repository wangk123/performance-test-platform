package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 惰性迁移（spec §3.4）：旧中文 → key；人工项丢弃；已是 key 原样。 */
class PrecheckMigrationTest {

    @Test
    void legacyItemsMigratedAndManualDropped() {
        PrecheckSettings raw = new PrecheckSettings(true,
                List.of("指标已定义", "场景已配置", "脚本已关联", "环境就绪", "数据就绪", "人员到位", "接口人明确"));
        PrecheckSettings migrated = PrecheckSettings.migrate(raw);
        assertThat(migrated.items()).containsExactly("doc.metrics-defined", "doc.scenarios-configured", "doc.script-bound");
        assertThat(migrated.enabled()).isTrue();
    }

    @Test
    void keyItemsAndUnknownsUntouched() {
        PrecheckSettings raw = new PrecheckSettings(true, List.of("doc.metrics-defined", "os.ulimit"));
        assertThat(PrecheckSettings.migrate(raw).items()).containsExactly("doc.metrics-defined", "os.ulimit");
    }
}
