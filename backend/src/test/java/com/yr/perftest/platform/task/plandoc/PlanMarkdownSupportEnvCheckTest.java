package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** appendEnvCheckRecord 回填幂等（锚点查重）、章节缺失原样返回、追加位置在「五、测试资源」章节末尾。 */
class PlanMarkdownSupportEnvCheckTest {

    private static final String BODY = """
            ## 五、测试资源

            | 机器 | 用途 |
            |---|---|
            | 10.1.1.10 | 压测执行机 |

            ## 六、测试约束

            - [ ] 环境就绪（人工）
            """;

    private static final String LINE = "- 环境检查：2 通过 / 1 待处理 · 10.1.1.10 · alice · <!--envcheck:9-->";

    @Test
    void sameRunIdAppendedTwiceIsIdempotentByAnchor() {
        String first = PlanMarkdownSupport.appendEnvCheckRecord(BODY, LINE);
        assertThat(first).isNotEqualTo(BODY);
        String second = PlanMarkdownSupport.appendEnvCheckRecord(first, LINE);
        assertThat(second).isEqualTo(first);
        assertThat(second.split("<!--envcheck:9-->", -1).length - 1).isEqualTo(1);
    }

    @Test
    void missingResourceSectionReturnsBodyUnchanged() {
        String body = "## 一、背景\n\n系统升级后需评估容量。\n";
        assertThat(PlanMarkdownSupport.appendEnvCheckRecord(body, LINE)).isEqualTo(body);
    }

    @Test
    void appendedLineSitsAtEndOfResourceSectionBeforeNextSection() {
        String appended = PlanMarkdownSupport.appendEnvCheckRecord(BODY, LINE);
        int lineStart = appended.indexOf(LINE);
        assertThat(lineStart).isGreaterThan(appended.indexOf("## 五、测试资源"));
        assertThat(lineStart).isLessThan(appended.indexOf("## 六、测试约束"));
        // 行落在资源表之后、下一章节之前（章节末尾），原表格内容保留
        assertThat(appended.indexOf("## 六、测试约束")).isGreaterThan(appended.indexOf("| 10.1.1.10 | 压测执行机 |"));
    }
}
