package com.yr.perftest.platform.envcheck.items;

import com.yr.perftest.platform.envcheck.EnvCheckCategory;
import com.yr.perftest.platform.envcheck.EnvCheckKind;
import com.yr.perftest.platform.envcheck.LocalCheckContext;
import com.yr.perftest.platform.envcheck.LocalCheckItem;
import com.yr.perftest.platform.envcheck.LocalVerdict;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 文档检查项「脚本已关联」：所有场景须关联脚本版本（判定逻辑迁移自 runPrecheck，语义不变）。 */
@Component
public class DocScriptBoundItem implements LocalCheckItem {

    @Override public String key() { return "doc.script-bound"; }
    @Override public String label() { return "脚本已关联"; }
    @Override public String description() { return "所有场景均已关联脚本版本"; }
    @Override public EnvCheckCategory category() { return EnvCheckCategory.DOC; }
    @Override public EnvCheckKind kind() { return EnvCheckKind.LOCAL; }
    @Override public Set<String> appliesTo() { return Set.of(); }
    @Override public int sortOrder() { return 3; }

    @Override
    public LocalVerdict check(LocalCheckContext ctx) {
        boolean pass = ctx.scenarios().stream().allMatch(s -> s.scriptVersionId() != null);
        return new LocalVerdict(pass, pass ? null : "存在未关联脚本版本的压测场景");
    }
}
