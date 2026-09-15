package com.yr.perftest.platform.envcheck.items;

import com.yr.perftest.platform.envcheck.EnvCheckCategory;
import com.yr.perftest.platform.envcheck.EnvCheckKind;
import com.yr.perftest.platform.envcheck.LocalCheckContext;
import com.yr.perftest.platform.envcheck.LocalCheckItem;
import com.yr.perftest.platform.envcheck.LocalVerdict;
import com.yr.perftest.platform.task.plandoc.PlanMarkdownSupport;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 文档检查项「指标已定义」：测试指标章节须含指标表（判定逻辑迁移自 runPrecheck，语义不变）。 */
@Component
public class DocMetricsDefinedItem implements LocalCheckItem {

    @Override public String key() { return "doc.metrics-defined"; }
    @Override public String label() { return "指标已定义"; }
    @Override public String description() { return "文档「测试指标」章节含指标表"; }
    @Override public EnvCheckCategory category() { return EnvCheckCategory.DOC; }
    @Override public EnvCheckKind kind() { return EnvCheckKind.LOCAL; }
    @Override public Set<String> appliesTo() { return Set.of(); }
    @Override public int sortOrder() { return 1; }

    @Override
    public LocalVerdict check(LocalCheckContext ctx) {
        String section = PlanMarkdownSupport.extractSection(ctx.body(), "三、测试指标");
        boolean pass = section != null && section.contains("|---");
        return new LocalVerdict(pass, pass ? null : "文档「三、测试指标」章节缺失或无指标表");
    }
}
