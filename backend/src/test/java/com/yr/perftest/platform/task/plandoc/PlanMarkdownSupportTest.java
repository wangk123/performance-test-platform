package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanMarkdownSupportTest {
    private static final String BODY = """
            ## 一、背景

            系统升级后需评估容量。

            ## 二、测试目的与指标

            | 交易 | 指标 | 目标值 | 口径 |
            |---|---|---|---|
            | 查询 | TPS | 200 | 5分钟均值 |

            ## 五、测试约束

            ### 入口准则

            - [ ] 指标已定义（自动）
            - [ ] 环境就绪（人工）

            ### 出口准则

            - [ ] 全部场景通过（人工）

            ## 七、场景设计

            ### S1 登录场景 · SINGLE_TXN

            **场景目的**：验证登录并发

            **测试方法**：逐步加压观察拐点

            **场景设置**（由场景执行配置生成，勿手改）：

            | 用户数 | 持续时长 | 加载方式 | 退出方式 |
            |---|---|---|---|
            | 50 | 300 秒 | 匀速加载 30 秒 | 同时退出 |

            #### 执行记录
            """;

    @Test
    void splitsSectionsByCanonicalHeading() {
        List<PlanMarkdownSupport.Section> sections = PlanMarkdownSupport.splitSections(BODY);
        assertThat(sections).extracting(PlanMarkdownSupport.Section::title)
                .containsExactly("一、背景", "二、测试目的与指标", "五、测试约束", "七、场景设计");
        assertThat(sections.get(0).content()).contains("系统升级后需评估容量");
    }

    @Test
    void extractsAndReplacesSectionContent() {
        String replaced = PlanMarkdownSupport.replaceSection(BODY, "二、测试目的与指标", "\n| 交易 | 指标 | 目标值 |\n|---|---|---|\n| 转账 | TPS | 300 |\n");
        assertThat(PlanMarkdownSupport.extractSection(replaced, "二、测试目的与指标")).contains("转账");
        assertThat(PlanMarkdownSupport.extractSection(replaced, "一、背景")).contains("系统升级");
        assertThatThrownBy(() -> PlanMarkdownSupport.replaceSection(BODY, "十二、不存在", "x"))
                .isInstanceOf(PlanValidationException.class);
    }

    @Test
    void appendsExecutionRecordIdempotentlyInsideScenarioBlock() {
        String first = PlanMarkdownSupport.appendExecutionRecord(BODY, "登录场景", 101L,
                "- 2026-09-04 14:30 · 50 并发 · SUCCESS · 吞吐 158.3 TPS · P95 96 ms · 错误率 0.42%");
        String second = PlanMarkdownSupport.appendExecutionRecord(first, "登录场景", 101L, "- 重复条目");
        assertThat(second).isEqualTo(first);
        assertThat(PlanMarkdownSupport.parseExecutionRecords(first, "登录场景")).hasSize(1);
        String other = PlanMarkdownSupport.appendExecutionRecord(first, "登录场景", 102L, "- 2026-09-04 15:00 · SUCCESS");
        assertThat(PlanMarkdownSupport.parseExecutionRecords(other, "登录场景")).hasSize(2);
        // 条目必须落在场景块内的 #### 执行记录 下，而不是章节末尾之外
        int blockStart = other.indexOf("### S1 登录场景");
        int marker = other.indexOf("<!-- backfill:execution:102 -->");
        assertThat(blockStart).isLessThan(marker);
        assertThat(other.indexOf("## 八、")).isEqualTo(-1); // 本文档无八章节
        assertThat(marker).isGreaterThan(other.indexOf("#### 执行记录", blockStart));
    }

    @Test
    void parsesChecklistItems() {
        List<String> items = PlanMarkdownSupport.parseChecklistItems(PlanMarkdownSupport.extractSection(BODY, "五、测试约束"));
        assertThat(items).containsExactly("指标已定义（自动）", "环境就绪（人工）", "全部场景通过（人工）");
    }

    @Test
    void rendersTemplatePlaceholder() {
        assertThat(PlanMarkdownSupport.renderTemplate("# {{planName}} 计划", "零售3.1")).isEqualTo("# 零售3.1 计划");
    }

    @Test
    void upsertsScenarioFactsKeepingFreeTextAndExecutionRecords() {
        String generated = """
                ### S1 登录场景 · BENCHMARK

                **场景目的**：改后的目的

                **测试方法**：（实体同步不触碰此处）

                **场景设置**（由场景执行配置生成，勿手改）：

                | 用户数 | 持续时长 | 加载方式 | 退出方式 |
                |---|---|---|---|
                | 100 | 300 秒 | 匀速加载 30 秒 | 同时退出 |
                """;
        String replaced = PlanMarkdownSupport.upsertScenarioFacts(BODY, "登录场景", generated);
        assertThat(replaced).contains("### S1 登录场景 · BENCHMARK");
        assertThat(replaced).contains("**场景目的**：改后的目的");
        assertThat(replaced).contains("| 100 | 300 秒 |");          // 设置表已更新
        assertThat(replaced).contains("**测试方法**：逐步加压观察拐点"); // 自由文本保留
        assertThat(replaced).doesNotContain("SINGLE_TXN");           // 旧标题被替换
        assertThat(replaced).contains("#### 执行记录");
    }

    @Test
    void upsertAppendsNewBlockWhenScenarioMissing() {
        String generated = "### S2 新场景 · STABILITY\n\n**场景目的**：新\n\n**场景设置**（由场景执行配置生成，勿手改）：\n\n| 用户数 | 持续时长 | 加载方式 | 退出方式 |\n|---|---|---|---|\n| 30 | 1800 秒 | 同时加载 | 同时退出 |\n";
        String replaced = PlanMarkdownSupport.upsertScenarioFacts(BODY, "新场景", generated);
        assertThat(replaced).contains("### S2 新场景 · STABILITY");
        assertThat(replaced).contains("#### 执行记录");
    }

    @Test
    void removesScenarioBlockEntirely() {
        String removed = PlanMarkdownSupport.removeScenarioBlock(BODY, "登录场景");
        assertThat(removed).doesNotContain("登录场景");
        assertThat(removed).contains("## 七、场景设计");
    }
}
