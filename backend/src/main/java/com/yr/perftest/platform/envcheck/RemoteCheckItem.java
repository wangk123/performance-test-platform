package com.yr.perftest.platform.envcheck;

import java.util.Optional;

/** REMOTE 型检查项：目标机探测 + 输出判定 + 可选修复（spec §3.2）。 */
public interface RemoteCheckItem extends EnvCheckItem {

    ProbeSpec probe(TargetHost host);

    ProbeVerdict judge(ProbeOutput output);

    Optional<FixSpec> fix(TargetHost host, ProbeOutput output);

    /** 风险分级，供 items API 展示；可修项按自身风险覆写。 */
    default EnvCheckRisk risk() { return EnvCheckRisk.LOW; }
}
