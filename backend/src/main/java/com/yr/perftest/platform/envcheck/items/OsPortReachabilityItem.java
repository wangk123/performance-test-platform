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

/**
 * REMOTE 项「端口可达性」：平台本机 nc 拨测目标机端口，模块列形如「订单服务:8080」取冒号后端口，无端口默认 22。
 * PLATFORM 通道命令按纯空白分词（无引号/管道），nc 命令形态天然合规。
 */
@Component
public class OsPortReachabilityItem implements RemoteCheckItem {

    private static final Pattern TRAILING_PORT = Pattern.compile(":(\\d+)$");
    private static final int DEFAULT_PORT = 22;

    @Override public String key() { return "os.port-reachability"; }
    @Override public String label() { return "端口可达性"; }
    @Override public String description() { return "平台可拨通目标机端口（模块列带端口时探该端口，否则探 SSH 22）"; }
    @Override public EnvCheckCategory category() { return EnvCheckCategory.OS; }
    @Override public EnvCheckKind kind() { return EnvCheckKind.REMOTE; }
    @Override public Set<String> appliesTo() { return Set.of(); }
    @Override public int sortOrder() { return 4; }

    @Override
    public ProbeSpec probe(TargetHost host) {
        return new ProbeSpec(ProbeLocation.PLATFORM, "nc -z -w 3 " + host.host() + " " + portOf(host));
    }

    @Override
    public ProbeVerdict judge(ProbeOutput output) {
        if (output.exitCode() == 0) {
            return new ProbeVerdict(true, "端口可达", null, null);
        }
        return new ProbeVerdict(false, "端口不可达或探测失败（exitCode=" + output.exitCode() + "）",
                "检查目标机端口监听与防火墙策略", null);
    }

    @Override
    public Optional<FixSpec> fix(TargetHost host, ProbeOutput output) {
        return Optional.empty();
    }

    private static int portOf(TargetHost host) {
        String module = host.module();
        if (module != null) {
            var matcher = TRAILING_PORT.matcher(module);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return DEFAULT_PORT;
    }
}
