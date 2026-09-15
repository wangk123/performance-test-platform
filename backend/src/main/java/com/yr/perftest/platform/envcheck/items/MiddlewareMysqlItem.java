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

/** REMOTE 项「MySQL 连接数上限」：仅适用于 mysql 模块；null=客户端不在机内告警，低于 500 告警；HIGH 修复 SET GLOBAL 500 可回滚。 */
@Component
public class MiddlewareMysqlItem implements RemoteCheckItem {

    private static final Pattern MAX_CONNECTIONS = Pattern.compile("\"max_connections\"\\s*:\\s*(null|\\d+)");
    private static final int MIN_MAX_CONNECTIONS = 500;

    @Override public String key() { return "middleware.mysql"; }
    @Override public String label() { return "MySQL 连接数上限"; }
    @Override public String description() { return "MySQL max_connections 不低于 500（需在 MySQL 机器配置本地客户端）"; }
    @Override public EnvCheckCategory category() { return EnvCheckCategory.MIDDLEWARE; }
    @Override public EnvCheckKind kind() { return EnvCheckKind.REMOTE; }
    @Override public Set<String> appliesTo() { return Set.of("mysql"); }
    @Override public int sortOrder() { return 1; }
    @Override public EnvCheckRisk risk() { return EnvCheckRisk.HIGH; }
    @Override public boolean fixable() { return true; }

    @Override
    public ProbeSpec probe(TargetHost host) {
        return new ProbeSpec(ProbeLocation.TARGET,
                "mysql -N -e \"select @@max_connections\" 2>/dev/null"
                        + " | awk '{print \"{\\\"max_connections\\\":\"($1+0)\"}\"}'"
                        + " || echo '{\"max_connections\":null}'");
    }

    @Override
    public ProbeVerdict judge(ProbeOutput output) {
        String stdout = output.stdout() == null ? "" : output.stdout();
        if (stdout.isBlank()) {
            return new ProbeVerdict(false, "探测输出为空（exitCode=" + output.exitCode() + "）",
                    "MySQL 客户端未安装或不可用，需在 MySQL 机器配置本地客户端后重试", null);
        }
        var matcher = MAX_CONNECTIONS.matcher(stdout);
        if (output.exitCode() != 0 || !matcher.find()) {
            return new ProbeVerdict(false, "探测输出非法（exitCode=" + output.exitCode() + "）", null, null);
        }
        if ("null".equals(matcher.group(1))) {
            return new ProbeVerdict(false, "目标机无可用 mysql 客户端",
                    "需在 MySQL 机器配置本地客户端", null);
        }
        long maxConnections;
        try {
            maxConnections = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return new ProbeVerdict(false, "探测输出非法（exitCode=" + output.exitCode() + "）", null, null);
        }
        if (maxConnections < MIN_MAX_CONNECTIONS) {
            return new ProbeVerdict(false, "当前 max_connections=" + maxConnections,
                    "建议不低于 " + MIN_MAX_CONNECTIONS, "SET GLOBAL max_connections");
        }
        return new ProbeVerdict(true, "max_connections=" + maxConnections, null, null);
    }

    @Override
    public Optional<FixSpec> fix(TargetHost host, ProbeOutput output) {
        return Optional.of(new FixSpec(EnvCheckRisk.HIGH,
                "mysql -N -e \"select @@max_connections\"",
                "mysql -e \"SET GLOBAL max_connections=500\"",
                "mysql -e \"SET GLOBAL max_connections={backupRef}\"",
                "将 MySQL 全局 max_connections 调至 500（立即生效，重启失效），可回滚"));
    }
}
