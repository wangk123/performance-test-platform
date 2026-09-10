package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 存量 11 章文档 → 12 章迁移规则单测：重命名、指标章拆分、幂等、无标记跳过。 */
class PlanSectionSplitMigratorTest {

    @Test
    void renumbersAndSplitsOldDocument() {
        String old = """
                ## 一、背景

                背景。

                ## 二、测试目的与指标

                ### 测试目的

                验证容量。

                ### 通过指标

                | 对象 | 指标 | 目标值 | 准出标准 |
                |---|---|---|---|
                | 查询 | TPS | ≥ 200 | 达标持续 30 分钟 |

                ## 三、测试范围

                范围。

                ## 十一、结论

                结论。
                """;
        String migrated = PlanSectionSplitMigrator.migrate(old);
        assertThat(migrated).isNotNull();
        // 章节号整体后移且顺序保持
        assertThat(migrated).contains("## 四、测试范围").contains("## 十二、结论");
        // 指标表随「### 通过指标」小节拆入「三、测试指标」，目的叙述留在「二、测试目的」
        assertThat(migrated).contains("## 二、测试目的");
        int purpose = migrated.indexOf("## 二、测试目的");
        int metrics = migrated.indexOf("## 三、测试指标");
        int table = migrated.indexOf("| 对象 | 指标 | 目标值 |");
        int scope = migrated.indexOf("## 四、测试范围");
        assertThat(purpose).isLessThan(metrics).isLessThan(table);
        assertThat(metrics).isLessThan(table).isLessThan(scope);
        assertThat(migrated.indexOf("验证容量")).isBetween(purpose, metrics);
        // 迁移结果在新规范下可完整解析出 12 章体系内的全部原章节
        assertThat(PlanMarkdownSupport.extractSection(migrated, "三、测试指标")).contains("TPS");
        assertThat(PlanMarkdownSupport.extractSection(migrated, "二、测试目的")).contains("验证容量");
        assertThat(PlanMarkdownSupport.extractSection(migrated, "十二、结论")).contains("结论");
    }

    @Test
    void splitsWithoutSubheadingBeforeTable() {
        String old = "## 二、测试目的与指标\n\n| 对象 | 指标 | 目标值 |\n|---|---|---|\n| 查询 | TPS | 200 |\n\n## 十一、结论\n";
        String migrated = PlanSectionSplitMigrator.migrate(old);
        assertThat(PlanMarkdownSupport.extractSection(migrated, "三、测试指标")).isNotNull();
        assertThat(PlanMarkdownSupport.extractSection(migrated, "三、测试指标")).contains("TPS");
        assertThat(PlanMarkdownSupport.extractSection(migrated, "二、测试目的")).doesNotContain("|");
    }

    @Test
    void appendsEmptyMetricSectionWhenNoTable() {
        String old = "## 二、测试目的与指标\n\n只写目的，没列指标表。\n\n## 十一、结论\n";
        String migrated = PlanSectionSplitMigrator.migrate(old);
        assertThat(migrated).contains("## 三、测试指标");
        assertThat(PlanMarkdownSupport.extractSection(migrated, "二、测试目的")).contains("只写目的");
        assertThat(migrated.indexOf("## 三、测试指标")).isLessThan(migrated.indexOf("## 十二、结论"));
    }

    @Test
    void isIdempotentOnAlreadyMigratedDocument() {
        String migratedOnce = PlanSectionSplitMigrator.migrate("""
                ## 二、测试目的

                目的。

                ## 三、测试指标

                | 对象 | 指标 | 目标值 |
                |---|---|---|
                | 查询 | TPS | 200 |

                ## 十二、结论
                """);
        assertThat(PlanSectionSplitMigrator.migrate(migratedOnce)).isNull();
        assertThat(PlanSectionSplitMigrator.migrate("## 一、背景\n\n新结构文档\n")).isNull();
        assertThat(PlanSectionSplitMigrator.migrate(null)).isNull();
    }
}
