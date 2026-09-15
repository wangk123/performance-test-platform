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

/** REMOTE 项「文件句柄上限」：ulimit -n 探测（单行 JSON），低于 65535 告警；MEDIUM 修复追加 limits.conf nofile 65535。 */
@Component
public class OsUlimitItem implements RemoteCheckItem {

    private static final Pattern OPEN_FILES = Pattern.compile("\"open_files\"\\s*:\\s*(\\d+)");
    private static final int THRESHOLD = 65535;

    @Override public String key() { return "os.ulimit"; }
    @Override public String label() { return "文件句柄上限"; }
    @Override public String description() { return "目标机文件句柄上限（ulimit -n）不低于 65535"; }
    @Override public EnvCheckCategory category() { return EnvCheckCategory.OS; }
    @Override public EnvCheckKind kind() { return EnvCheckKind.REMOTE; }
    @Override public Set<String> appliesTo() { return Set.of(); }
    @Override public int sortOrder() { return 1; }
    @Override public EnvCheckRisk risk() { return EnvCheckRisk.MEDIUM; }

    @Override
    public ProbeSpec probe(TargetHost host) {
        return new ProbeSpec(ProbeLocation.TARGET,
                "ulimit -n | awk '{print \"{\\\"open_files\\\":\"$1\"}\"}'");
    }

    @Override
    public ProbeVerdict judge(ProbeOutput output) {
        var matcher = OPEN_FILES.matcher(output.stdout() == null ? "" : output.stdout());
        if (output.exitCode() != 0 || !matcher.find()) {
            return new ProbeVerdict(false, "探测输出非法（exitCode=" + output.exitCode() + "）", null, null);
        }
        long openFiles;
        try {
            openFiles = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return new ProbeVerdict(false, "探测输出非法（exitCode=" + output.exitCode() + "）", null, null);
        }
        if (openFiles < THRESHOLD) {
            return new ProbeVerdict(false, "当前 open_files=" + openFiles,
                    "建议不低于 " + THRESHOLD, "limits.conf");
        }
        return new ProbeVerdict(true, "open_files=" + openFiles, null, null);
    }

    @Override
    public Optional<FixSpec> fix(TargetHost host, ProbeOutput output) {
        return Optional.of(new FixSpec(EnvCheckRisk.MEDIUM,
                "cp -f /etc/security/limits.conf /tmp/limits.conf.bak.$(date +%s) && echo /tmp/limits.conf.bak.*|tail -1",
                "printf '\\n* soft nofile 65535\\n* hard nofile 65535\\n' >> /etc/security/limits.conf",
                "cp -f {backupRef} /etc/security/limits.conf",
                "向 /etc/security/limits.conf 追加 nofile 65535（soft/hard），可回滚"));
    }
}
