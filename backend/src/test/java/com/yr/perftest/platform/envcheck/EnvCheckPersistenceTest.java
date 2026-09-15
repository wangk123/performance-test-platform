package com.yr.perftest.platform.envcheck;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:envcheck-repo-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class EnvCheckPersistenceTest {

    @Autowired PersistentEnvCheckCredentialRepository credentials;
    @Autowired PersistentEnvCheckRunRepository runs;
    @Autowired PersistentEnvCheckFixRepository fixes;

    @Test
    void credentialPlanOverrideBeatsProjectPool() {
        credentials.save(new PersistentEnvCheckCredentialRecord(1L, null, "10.1.1.10", 22, "deploy", "enc:x", "PASSWORD", null, "alice"));
        credentials.save(new PersistentEnvCheckCredentialRecord(1L, 7L, "10.1.1.10", 22, "perftest", "enc:y", "PASSWORD", null, "bob"));
        assertThat(credentials.findByProjectIdAndPlanIdAndHost(1L, 7L, "10.1.1.10").orElseThrow().getUsername())
                .isEqualTo("perftest");
        assertThat(credentials.findByProjectIdAndPlanIdIsNullAndHost(1L, "10.1.1.10").orElseThrow().getUsername())
                .isEqualTo("deploy");
    }

    @Test
    void runFinishAndFixRollbackLifecycle() {
        PersistentEnvCheckRunRecord run = runs.save(new PersistentEnvCheckRunRecord(7L, "alice"));
        run.markFinished(8, 2, "{}");
        runs.save(run);
        PersistentEnvCheckFixRecord fix = fixes.save(new PersistentEnvCheckFixRecord(
                run.getId(), "10.1.1.10", "os.ulimit", "MEDIUM", "/backup/limits.conf.bak.1",
                "- nofile 1024\n+ nofile 65535", "ulimit 1024 → 65535", "bob"));
        fix.markRolledBack(Instant.now());
        fixes.save(fix);
        assertThat(runs.findFirstByPlanIdOrderByStartedAtDesc(7L).orElseThrow().getWarned()).isEqualTo(2);
        assertThat(fixes.findByRunIdOrderByIdDesc(run.getId()).get(0).getRolledBackAt()).isNotNull();
    }
}
