package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanAcceptanceParserTest {

    private static String doc(String metricSection) {
        return "# 计划\n\n## 一、背景\n\nb\n\n## 二、测试目的与指标\n\n" + metricSection
                + "\n\n## 三、测试范围\n\nt\n";
    }

    @Test
    void parsesMetricRowsWithAliasAndDirection() {
        PlanAcceptanceParser.AcceptanceSection section = PlanAcceptanceParser.parse(doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 200 | 5 分钟均值 |
                | 下单交易 | p95响应时间 | ≤ 300 ms | 全量样本 |
                | 下单交易 | 错误率 | ≤ 0.5% | 全量样本 |
                | 系统容量 | 容量 | 1万在线用户 | 自由口径 |
                | GC 观察 | GC 次数 | 少于 10 次 | 人工 |"""));
        assertThat(section.present()).isTrue();
        List<PlanAcceptanceParser.AcceptanceMetricRow> rows = section.rows();
        assertThat(rows).hasSize(5);
        assertThat(rows.get(0).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.TPS);
        assertThat(rows.get(0).targetValue()).isEqualTo(200d);
        assertThat(rows.get(1).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.P95);
        assertThat(rows.get(1).targetValue()).isEqualTo(300d);
        assertThat(rows.get(2).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.ERROR_RATE);
        assertThat(rows.get(2).targetValue()).isEqualTo(0.5d);
        assertThat(rows.get(3).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.CAPACITY);
        assertThat(rows.get(3).targetValue()).isNull(); // 自由文本目标
        assertThat(rows.get(4).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.OTHER);
        assertThat(rows.get(4).targetValue()).isNull();
        assertThat(rows.get(0).objectName()).isEqualTo("登录场景");
        assertThat(rows.get(0).lineNumber()).isEqualTo(1); // 数据行序号（1 基）
    }

    @Test
    void acceptsLegacyTradingHeaderForBackwardCompatibility() {
        PlanAcceptanceParser.AcceptanceSection section = PlanAcceptanceParser.parse(doc("""
                | 交易 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 查询交易 | TPS | ≥ 200 | 5 分钟均值 |"""));
        assertThat(section.present()).isTrue();
        assertThat(section.rows().get(0).objectName()).isEqualTo("查询交易");
    }

    @Test
    void missingSectionOrTableOrEmptyTableFallsToNoMetricPath() {
        assertThat(PlanAcceptanceParser.parse("# 无章节\n").present()).isFalse();
        assertThat(PlanAcceptanceParser.parse(doc("（纯文字，无表格）")).present()).isFalse();
        assertThat(PlanAcceptanceParser.parse(doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|""")).present()).isFalse(); // 空表（仅表头）
    }

    @Test
    void headerMissingRequiredColumnsRejected() {
        assertThatThrownBy(() -> PlanAcceptanceParser.parse(doc("""
                | 指标名 | 数值 |
                |---|---|
                | TPS | 200 |""")))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("表头缺少列")
                .hasMessageContaining("对象");
    }

    @Test
    void shortDataRowRejectedWithLineNumber() {
        assertThatThrownBy(() -> PlanAcceptanceParser.parse(doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS |""")))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("第 1 行")
                .hasMessageContaining("单元格数不足");
    }

    @Test
    void nonNumericTargetForKnownTypeRejected() {
        assertThatThrownBy(() -> PlanAcceptanceParser.parse(doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | 两百 | 5 分钟均值 |""")))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("第 1 行")
                .hasMessageContaining("目标值");
    }

    @Test
    void sectionTitleOrderingTolerated() {
        // 序号容错：标题写成「二、xxx」别名仍能定位（extractSection 的序号容错）
        String body = "# p\n\n## 二、目的与指标（修订）\n\n| 对象 | 指标 | 目标值 |\n|---|---|---|\n| S | TPS | 100 |\n";
        assertThat(PlanAcceptanceParser.parse(body).present()).isTrue();
    }

    @Test
    void lenientParseSwallowsFormatErrors() {
        assertThat(PlanAcceptanceParser.parseLeniently(doc("""
                | 指标名 | 数值 |
                |---|---|
                | TPS | 200 |""")).present()).isFalse();
    }
}
