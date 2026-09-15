package com.yr.perftest.platform.envcheck;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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

    @Test
    void globalSwitchOffRejects() {
        assertThatThrownBy(() -> fixService.apply(1L, List.of(new EnvCheckFixService.FixRequest("h", "os.ulimit")), "alice"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("修复已被平台关闭");
    }
}
