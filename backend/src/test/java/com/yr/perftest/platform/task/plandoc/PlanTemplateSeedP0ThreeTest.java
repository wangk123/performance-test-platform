package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTemplateSeedP0ThreeTest {

    @Test
    void builtinTemplateUsesObjectHeaderAndRedrawShapedAchievementTable() {
        String template = PlanTemplateSeeder.BUILTIN_TEMPLATE;
        assertThat(template).contains("| 对象 | 指标 | 目标值 | 口径 |");
        assertThat(template).doesNotContain("| 交易 | 指标 | 目标值 | 口径 |");
        // 模板渲染后的文档必须能通过保存校验并解析出指标行
        String rendered = PlanMarkdownSupport.renderTemplate(template, "示例计划");
        PlanAcceptanceParser.AcceptanceSection section = PlanAcceptanceParser.parse(rendered);
        assertThat(section.present()).isTrue();
        assertThat(section.rows().get(0).objectName()).isEqualTo("（示例）查询交易");
        // 结论占位表为重绘后六列形态
        assertThat(template).contains("| 对象 | 指标 | 目标 | 实际 | 状态 | 说明 |");
    }
}
