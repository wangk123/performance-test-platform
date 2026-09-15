package com.yr.perftest.platform.envcheck.items;

import com.yr.perftest.platform.envcheck.EnvCheckCategory;
import com.yr.perftest.platform.envcheck.EnvCheckKind;
import com.yr.perftest.platform.envcheck.EnvCheckRisk;
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

/**
 * REMOTE 项「内核参数」：tcp_tw_reuse=1 且 somaxconn≥4096；MEDIUM 修复 sysctl -w 并落盘 sysctl.conf。
 * 本项输出为键值文本（tw=… somax=…），豁免单行 JSON 契约，judge 按正则解析。
 */
@Component
public class OsKernelParamsItem implements RemoteCheckItem {

    private static final Pattern TW_REUSE = Pattern.compile("tw=(\\d+)");
    private static final Pattern SOMAXCONN = Pattern.compile("somax=(\\d+)");
    private static final int MIN_SOMAXCONN = 4096;

    @Override public String key() { return "os.kernel-params"; }
    @Override public String label() { return "内核参数"; }
    @Override public String description() { return "net.ipv4.tcp_tw_reuse=1 且 net.core.somaxconn≥4096"; }
    @Override public EnvCheckCategory category() { return EnvCheckCategory.OS; }
    @Override public EnvCheckKind kind() { return EnvCheckKind.REMOTE; }
    @Override public Set<String> appliesTo() { return Set.of(); }
    @Override public int sortOrder() { return 2; }
    @Override public EnvCheckRisk risk() { return EnvCheckRisk.MEDIUM; }
    @Override public boolean fixable() { return true; }

    @Override
    public ProbeSpec probe(TargetHost host) {
        return new ProbeSpec(ProbeLocation.TARGET,
                "echo tw=$(sysctl -n net.ipv4.tcp_tw_reuse) somax=$(sysctl -n net.core.somaxconn)");
    }

    @Override
    public ProbeVerdict judge(ProbeOutput output) {
        String stdout = output.stdout() == null ? "" : output.stdout();
        var tw = TW_REUSE.matcher(stdout);
        var somax = SOMAXCONN.matcher(stdout);
        if (output.exitCode() != 0 || !tw.find() || !somax.find()) {
            return new ProbeVerdict(false, "探测输出非法（exitCode=" + output.exitCode() + "）", null, null);
        }
        boolean twOk = "1".equals(tw.group(1));
        boolean somaxOk;
        try {
            somaxOk = Long.parseLong(somax.group(1)) >= MIN_SOMAXCONN;
        } catch (NumberFormatException exception) {
            return new ProbeVerdict(false, "探测输出非法（exitCode=" + output.exitCode() + "）", null, null);
        }
        if (twOk && somaxOk) {
            return new ProbeVerdict(true, stdout.trim(), null, null);
        }
        return new ProbeVerdict(false, "tcp_tw_reuse=" + tw.group(1) + " somaxconn=" + somax.group(1),
                "建议 net.ipv4.tcp_tw_reuse=1、net.core.somaxconn≥" + MIN_SOMAXCONN, "sysctl.conf");
    }

    @Override
    public Optional<FixSpec> fix(TargetHost host, ProbeOutput output) {
        return Optional.of(new FixSpec(EnvCheckRisk.MEDIUM,
                "sysctl -n net.core.somaxconn",
                "sysctl -w net.core.somaxconn=4096 && printf 'net.core.somaxconn=4096\\n' >> /etc/sysctl.conf",
                "sysctl -w net.core.somaxconn={backupRef}",
                "将 net.core.somaxconn 调至 4096 并持久化到 /etc/sysctl.conf，可回滚"));
    }
}
