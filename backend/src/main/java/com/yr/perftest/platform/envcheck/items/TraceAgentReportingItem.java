package com.yr.perftest.platform.envcheck.items;

import com.yr.perftest.platform.envcheck.EnvCheckCategory;
import com.yr.perftest.platform.envcheck.EnvCheckKind;
import com.yr.perftest.platform.envcheck.LocalCheckContext;
import com.yr.perftest.platform.envcheck.LocalCheckItem;
import com.yr.perftest.platform.envcheck.LocalVerdict;
import com.yr.perftest.platform.evidence.deep.DeepEvidenceKind;
import com.yr.perftest.platform.evidence.deep.DeepEvidenceProperties;
import com.yr.perftest.platform.evidence.deep.SkyWalkingGraphqlClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 链路上报探测（诊断轮前置软检查）：trace 深度源启用时，探测 OAP 近 10 分钟是否有 trace 上报。
 * 未上报 / OAP 不可达 → ok=false，渲染 WARNING，不阻断执行（EnvironmentCheckRunner 语义）。
 */
@Component
public class TraceAgentReportingItem implements LocalCheckItem {

    private static final Duration LOOKBACK = Duration.ofMinutes(10);

    private final DeepEvidenceProperties properties;
    private final SkyWalkingGraphqlClient client;

    public TraceAgentReportingItem(DeepEvidenceProperties properties, SkyWalkingGraphqlClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override public String key() { return "trace.agent-reporting"; }
    @Override public String label() { return "链路上报探测"; }
    @Override public String description() { return "探测 SkyWalking OAP 近 10 分钟是否收到 agent trace 上报（诊断轮前置）"; }
    @Override public EnvCheckCategory category() { return EnvCheckCategory.OBSERVABILITY; }
    @Override public EnvCheckKind kind() { return EnvCheckKind.LOCAL; }
    @Override public Set<String> appliesTo() { return Set.of(); }
    @Override public int sortOrder() { return 104; }

    @Override
    public LocalVerdict check(LocalCheckContext ctx) {
        DeepEvidenceProperties.KindConfig trace = properties.forKind(DeepEvidenceKind.TRACE);
        if (!trace.isEnabled()) {
            return new LocalVerdict(true, "trace 深度源未启用，跳过");
        }
        Instant now = Instant.now();
        try {
            boolean reported = !client.queryBasicTraces(
                    null, null, now.minus(LOOKBACK), now, null, null, true, 1, 1).traces().isEmpty();
            if (!reported) {
                return new LocalVerdict(false, "未检测到近 10 分钟 trace 上报：agent 未挂载或采样率为 0");
            }
            return new LocalVerdict(true, null);
        } catch (RuntimeException exception) {
            return new LocalVerdict(false, "OAP 不可达：" + exception.getMessage());
        }
    }
}
