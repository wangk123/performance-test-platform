package com.yr.perftest.platform.identity;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrincipalTest {
    @Test
    void humanPrincipalExposesUsernameAndRoles() {
        Set<SystemRole> roles = EnumSet.of(SystemRole.ADMIN, SystemRole.PROJECT_OWNER);
        HumanPrincipal human = new HumanPrincipal("admin", roles);

        assertThat(human.username()).isEqualTo("admin");
        assertThat(human.roles()).containsExactlyInAnyOrder(SystemRole.ADMIN, SystemRole.PROJECT_OWNER);
        assertThat(human).isInstanceOf(Principal.class);
    }

    @Test
    void machinePrincipalExposesApiKeyIdAndReservedScope() {
        MachinePrincipal machine = new MachinePrincipal(42L, "reserved");

        assertThat(machine.apiKeyId()).isEqualTo(42L);
        assertThat(machine.scope()).isEqualTo("reserved");
        assertThat(machine).isInstanceOf(Principal.class);
    }
}
