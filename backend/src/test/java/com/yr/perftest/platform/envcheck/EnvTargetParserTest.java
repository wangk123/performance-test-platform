package com.yr.perftest.platform.envcheck;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 环境部署信息表解析（spec E3）。 */
class EnvTargetParserTest {

    private static final String BODY = """
            ## 四、测试资源

            ### 环境部署信息

            | 地址 | 模块 | 配置/版本 |
            |---|---|---|
            | 10.1.1.10 | 订单服务 | 4C8G/JDK17 |
            | 10.1.1.20 | MySQL 8.0 | 8C16G |

            ### 执行节点与监控目标
            """;

    @Test
    void parsesAddressAndModuleColumns() {
        List<TargetHost> targets = new EnvTargetParser().parse(BODY);
        assertThat(targets).hasSize(2);
        assertThat(targets.get(0).host()).isEqualTo("10.1.1.10");
        assertThat(targets.get(0).module()).isEqualTo("订单服务");
        assertThat(targets.get(1).module()).isEqualTo("MySQL 8.0");
    }

    @Test
    void missingSectionYieldsEmptyAndBlankModuleKept() {
        assertThat(new EnvTargetParser().parse("## 其他\n")).isEmpty();
        List<TargetHost> one = new EnvTargetParser().parse(
                "## 四、测试资源\n\n### 环境部署信息\n\n| 地址 | 模块 | 配置/版本 |\n|---|---|---|\n| 10.9.9.9 |  | x |\n");
        assertThat(one).hasSize(1);
        assertThat(one.get(0).module()).isEmpty();
    }
}
