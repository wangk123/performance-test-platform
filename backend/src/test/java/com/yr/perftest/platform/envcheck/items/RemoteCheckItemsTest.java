package com.yr.perftest.platform.envcheck.items;

import com.yr.perftest.platform.envcheck.EnvCheckKind;
import com.yr.perftest.platform.envcheck.EnvCheckRisk;
import com.yr.perftest.platform.envcheck.ProbeLocation;
import com.yr.perftest.platform.envcheck.ProbeOutput;
import com.yr.perftest.platform.envcheck.TargetHost;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** REMOTE 项判定：样例 stdout → 三态（spec §9 内置项）。 */
class RemoteCheckItemsTest {

    private static final TargetHost HOST = new TargetHost("10.1.1.10", "订单服务");

    @Test
    void ulimitBelowThresholdWarns() {
        var item = new OsUlimitItem();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "{\"open_files\":1024}\n")).ok()).isFalse();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "{\"open_files\":65535}\n")).ok()).isTrue();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 1, "garbage")).ok()).isFalse(); // 非法输出=失败
    }

    @Test
    void ulimitFixSpecPresent() {
        var item = new OsUlimitItem();
        var fix = item.fix(HOST, new ProbeOutput("10.1.1.10", 0, "{\"open_files\":1024}\n")).orElseThrow();
        assertThat(fix.risk()).isEqualTo(EnvCheckRisk.MEDIUM);
        assertThat(fix.backupScript()).isNotBlank();
        assertThat(fix.applyScript()).isNotBlank();
        assertThat(fix.rollbackScript()).isNotBlank();
        assertThat(fix.rollbackScript()).contains("{backupRef}");
    }

    @Test
    void kernelParamsKeyValuesParse() {
        var item = new OsKernelParamsItem();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "tw=1 somax=4096\n")).ok()).isTrue();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "tw=0 somax=128\n")).ok()).isFalse();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "tw=1 somax=4095\n")).ok()).isFalse();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "sysctl: unknown oid")).ok()).isFalse();
    }

    @Test
    void diskOverCountWarns() {
        var item = new OsDiskUsageItem();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "{\"over\":2}\n")).ok()).isFalse();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "{\"over\":0}\n")).ok()).isTrue();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "{\"over\":x}")).ok()).isFalse();
    }

    @Test
    void portReachabilityByExitCode() {
        var item = new OsPortReachabilityItem();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "")).ok()).isTrue();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 1, "")).ok()).isFalse();
        var spec = item.probe(new TargetHost("10.1.1.10", "订单服务:8080"));
        assertThat(spec.location()).isEqualTo(ProbeLocation.PLATFORM);
        assertThat(spec.script()).isEqualTo("nc -z -w 3 10.1.1.10 8080");
        assertThat(item.probe(HOST).script()).isEqualTo("nc -z -w 3 10.1.1.10 22");
    }

    @Test
    void jvmNoProcessWarns() {
        var item = new JvmDiscoveryItem();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "root 123 1 0 app java -jar demo.jar\n")).ok()).isTrue();
        assertThat(item.judge(new ProbeOutput("10.1.1.10", 0, "")).ok()).isFalse();
    }

    @Test
    void mysqlNullClientWarnsOnly() {
        var item = new MiddlewareMysqlItem();
        var verdict = item.judge(new ProbeOutput("10.1.1.20", 0, "{\"max_connections\":null}\n"));
        assertThat(verdict.ok()).isFalse();
        assertThat(item.fix(HOST, new ProbeOutput("10.1.1.20", 0, "{\"max_connections\":151}\n"))).isPresent();
    }

    @Test
    void mysqlBlankStdoutMapsToClientMissing() {
        var item = new MiddlewareMysqlItem();
        var verdict = item.judge(new ProbeOutput("h", 0, ""));
        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.suggestion()).contains("本地客户端");
    }

    @Test
    void numericOverflowTreatedAsInvalidOutput() {
        assertThat(new OsUlimitItem().judge(
                new ProbeOutput("h", 0, "{\"open_files\":99999999999999999999999}\n")).ok()).isFalse();
        assertThat(new OsDiskUsageItem().judge(
                new ProbeOutput("h", 0, "{\"over\":99999999999999999999999}\n")).ok()).isFalse();
        assertThat(new OsKernelParamsItem().judge(
                new ProbeOutput("h", 0, "tw=1 somax=99999999999999999999999\n")).ok()).isFalse();
        assertThat(new MiddlewareMysqlItem().judge(
                new ProbeOutput("h", 0, "{\"max_connections\":99999999999999999999999}\n")).ok()).isFalse();
    }

    @Test
    void mysqlThresholdAndHighRiskFix() {
        var item = new MiddlewareMysqlItem();
        assertThat(item.judge(new ProbeOutput("10.1.1.20", 0, "{\"max_connections\":500}\n")).ok()).isTrue();
        assertThat(item.judge(new ProbeOutput("10.1.1.20", 0, "{\"max_connections\":499}\n")).ok()).isFalse();
        var fix = item.fix(HOST, new ProbeOutput("10.1.1.20", 0, "{\"max_connections\":151}\n")).orElseThrow();
        assertThat(fix.risk()).isEqualTo(EnvCheckRisk.HIGH);
        assertThat(fix.applyScript()).contains("500");
        assertThat(fix.rollbackScript()).contains("{backupRef}");
    }

    @Test
    void metadataAndRiskLevels() {
        assertThat(new OsUlimitItem().risk()).isEqualTo(EnvCheckRisk.MEDIUM);
        assertThat(new OsKernelParamsItem().risk()).isEqualTo(EnvCheckRisk.MEDIUM);
        assertThat(new OsDiskUsageItem().risk()).isEqualTo(EnvCheckRisk.LOW);
        assertThat(new OsPortReachabilityItem().risk()).isEqualTo(EnvCheckRisk.LOW);
        assertThat(new JvmDiscoveryItem().risk()).isEqualTo(EnvCheckRisk.LOW);
        assertThat(new MiddlewareMysqlItem().risk()).isEqualTo(EnvCheckRisk.HIGH);
        assertThat(new MiddlewareMysqlItem().appliesTo()).containsExactly("mysql");
        assertThat(new OsUlimitItem().kind()).isEqualTo(EnvCheckKind.REMOTE);
    }
}
