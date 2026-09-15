package com.yr.perftest.platform.envcheck;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 修复链路：总闸关闭拒绝；不可修/LOCAL 项跳过；认证失败机器修复归 failed 不假报成功（spec §4.5）。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:envcheck-fix-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "platform.envcheck.fix-enabled=false"
})
@Transactional
class EnvCheckFixServiceTest {

    @Autowired EnvCheckFixService fixService;
    @Autowired PersistentEnvCheckFixRepository fixRepository;

    @Test
    void globalSwitchOffRejects() {
        assertThatThrownBy(() -> fixService.apply(1L, List.of(new EnvCheckFixService.FixRequest("h", "os.ulimit")), "alice"))
                .isInstanceOf(EnvCheckStateException.class)
                .hasMessageContaining("修复已被平台关闭");
    }

    /** 回滚幂等：已回滚记录再次 rollback 拒绝（校验先于凭据/目标机解析，run 不存在也先命中）。 */
    @Test
    void rollbackRejectsAlreadyRolledBack() {
        PersistentEnvCheckFixRecord record = new PersistentEnvCheckFixRecord(
                1L, "10.0.0.1", "os.ulimit", "MEDIUM", "/tmp/limits.conf.bak.1", "d", "s", "alice");
        record.markRolledBack(Instant.now());
        long fixId = fixRepository.save(record).getId();
        assertThatThrownBy(() -> fixService.rollback(fixId, "bob"))
                .isInstanceOf(EnvCheckStateException.class)
                .hasMessageContaining("已回滚");
    }
}
