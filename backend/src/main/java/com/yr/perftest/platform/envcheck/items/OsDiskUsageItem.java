package com.yr.perftest.platform.envcheck.items;

import com.yr.perftest.platform.envcheck.EnvCheckCategory;
import com.yr.perftest.platform.envcheck.EnvCheckKind;
import com.yr.perftest.platform.envcheck.FixSpec;
import com.yr.perftest.platform.envcheck.ProbeLocation;
import com.yr.perftest.platform.envcheck.ProbeOutput;
import com.yr.perftest.platform.envcheck.ProbeSpec;
import com.yr.perftest.platform.envcheck.ProbeVerdict;
import com.yr.perftest.platform.envcheck.RemoteCheckItem;
import com.yr.perftest.platform.envcheck.TargetHost;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** REMOTE 项「磁盘使用率」：df -P 统计使用率超 80% 的挂载点数（awk 内嵌阈值，单行 JSON）；仅告警提示清理磁盘，无修复。 */
@Component
public class OsDiskUsageItem implements RemoteCheckItem {

    private static final Pattern OVER = Pattern.compile("\"over\"\\s*:\\s*(\\d+)");
    private static final int THRESHOLD = 80;

    @Override public String key() { return "os.disk-usage"; }
    @Override public String label() { return "磁盘使用率"; }
    @Override public String description() { return "目标机不存在使用率超过 80% 的挂载点"; }
    @Override public EnvCheckCategory category() { return EnvCheckCategory.OS; }
    @Override public EnvCheckKind kind() { return EnvCheckKind.REMOTE; }
    @Override public Set<String> appliesTo() { return Set.of(); }
    @Override public int sortOrder() { return 3; }

    @Override
    public ProbeSpec probe(TargetHost host) {
        return new ProbeSpec(ProbeLocation.TARGET,
                "df -P | awk 'NR>1 && $5+0>" + THRESHOLD + " {n++} END {print \"{\\\"over\\\":\" n+0 \"}\"}'");
    }

    @Override
    public ProbeVerdict judge(ProbeOutput output) {
        var matcher = OVER.matcher(output.stdout() == null ? "" : output.stdout());
        if (output.exitCode() != 0 || !matcher.find()) {
            return new ProbeVerdict(false, "探测输出非法（exitCode=" + output.exitCode() + "）", null, null);
        }
        long over;
        try {
            over = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return new ProbeVerdict(false, "探测输出非法（exitCode=" + output.exitCode() + "）", null, null);
        }
        if (over > 0) {
            return new ProbeVerdict(false, over + " 个挂载点使用率超过 " + THRESHOLD + "%",
                    "清理磁盘或扩容", null);
        }
        return new ProbeVerdict(true, "所有挂载点使用率不高于 " + THRESHOLD + "%", null, null);
    }

    @Override
    public Optional<FixSpec> fix(TargetHost host, ProbeOutput output) {
        return Optional.empty();
    }
}
