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

/** REMOTE 项「JVM 进程发现」：ps -ef 摘要 java 进程供报告引用（发现型）；无 java 进程时告警，无修复。 */
@Component
public class JvmDiscoveryItem implements RemoteCheckItem {

    private static final int MAX_DETAIL = 500;

    @Override public String key() { return "jvm.discovery"; }
    @Override public String label() { return "JVM 进程发现"; }
    @Override public String description() { return "目标机上存在 Java 进程并输出前 3 条进程摘要"; }
    @Override public EnvCheckCategory category() { return EnvCheckCategory.JVM; }
    @Override public EnvCheckKind kind() { return EnvCheckKind.REMOTE; }
    @Override public Set<String> appliesTo() { return Set.of(); }
    @Override public int sortOrder() { return 1; }

    @Override
    public ProbeSpec probe(TargetHost host) {
        return new ProbeSpec(ProbeLocation.TARGET, "ps -ef | grep [j]ava | head -3");
    }

    @Override
    public ProbeVerdict judge(ProbeOutput output) {
        String stdout = output.stdout() == null ? "" : output.stdout().trim();
        if (stdout.isEmpty()) {
            return new ProbeVerdict(false, "未发现 JVM 进程", null, null);
        }
        if (output.exitCode() != 0) {
            return new ProbeVerdict(false, "进程探测失败（exitCode=" + output.exitCode() + "）", null, null);
        }
        return new ProbeVerdict(true, summarize(stdout), null, null);
    }

    @Override
    public Optional<FixSpec> fix(TargetHost host, ProbeOutput output) {
        return Optional.empty();
    }

    private static String summarize(String stdout) {
        String flat = stdout.replaceAll("\\s+", " ");
        return flat.length() > MAX_DETAIL ? flat.substring(0, MAX_DETAIL) : flat;
    }
}
