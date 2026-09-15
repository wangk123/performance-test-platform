package com.yr.perftest.platform.envcheck;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 探测通道：TARGET 走 env-probe（本机无 ssh 服务时 ok=false 结构化失败）；PLATFORM 本机直跑。 */
class EnvProbeClientTest {

    private final EnvProbeClient client = new EnvProbeClient(new EnvCheckProperties());

    @Test
    void platformProbeRunsEcho() {
        var out = client.probePlatform("127.0.0.1", 0, "echo hello");
        assertThat(out.code()).isZero();
        assertThat(out.output().trim()).isEqualTo("hello");
    }

    @Test
    void targetProbeAgainstUnreachableHostFailsStructured() {
        var cred = new EnvCheckCredentialService.ResolvedCredential("127.0.0.1", 22, "nobody", "bad", null);
        try {
            client.probeTarget(cred, List.of(new ProbeCommand("t", "echo x")));
            org.junit.jupiter.api.Assertions.fail("expected structured failure");
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage()).isNotBlank();
        }
    }
}
