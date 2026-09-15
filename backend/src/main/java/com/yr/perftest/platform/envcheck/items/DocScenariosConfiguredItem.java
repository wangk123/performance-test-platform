package com.yr.perftest.platform.envcheck.items;

import com.yr.perftest.platform.envcheck.EnvCheckCategory;
import com.yr.perftest.platform.envcheck.EnvCheckKind;
import com.yr.perftest.platform.envcheck.LocalCheckContext;
import com.yr.perftest.platform.envcheck.LocalCheckItem;
import com.yr.perftest.platform.envcheck.LocalVerdict;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 文档检查项「场景已配置」：计划须至少配置一个压测场景（判定逻辑迁移自 runPrecheck，语义不变）。 */
@Component
public class DocScenariosConfiguredItem implements LocalCheckItem {

    @Override public String key() { return "doc.scenarios-configured"; }
    @Override public String label() { return "场景已配置"; }
    @Override public String description() { return "计划至少配置一个压测场景"; }
    @Override public EnvCheckCategory category() { return EnvCheckCategory.DOC; }
    @Override public EnvCheckKind kind() { return EnvCheckKind.LOCAL; }
    @Override public Set<String> appliesTo() { return Set.of(); }
    @Override public int sortOrder() { return 2; }

    @Override
    public LocalVerdict check(LocalCheckContext ctx) {
        boolean pass = !ctx.scenarios().isEmpty();
        return new LocalVerdict(pass, pass ? null : "计划未配置任何压测场景");
    }
}
