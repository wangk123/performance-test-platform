package com.yr.perftest.platform.datamigration;

import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * spec §7 用例 1：上下文就绪本身 = Flyway V1 在真 MySQL 容器执行成功 + Hibernate validate 通过；
 * 冒烟读写验证 @Lob→LONGTEXT 与 Instant→datetime(6) 往返。
 * {@code @ServiceConnection} 连接详情覆盖配置文件数据源地址指向容器；Flyway 方言占位符
 * 覆盖说明见 IT 内 properties（测试侧 application.yml 的 clob 是 H2 方言，真库需 longtext）。
 */
@Tag("mysql")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        // 测试侧 application.yml（lob_type=clob，H2 方言）在共享 test 源集类路径上遮蔽主配置，
        // 真库必须显式指回 longtext，否则 Flyway V1 的 ${lob_type} 列在 MySQL 语法报错
        "spring.flyway.placeholders.lob_type=longtext",
        // 同因遮蔽：主配置已挂 MysqlLongtextDialect（validate 判等 longtext），IT 内联显式指向
        "spring.jpa.properties.hibernate.dialect=com.yr.perftest.platform.config.MysqlLongtextDialect"})
class MysqlV1SchemaIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Test
    void contextBootsViaFlywayV1AndRoundTripsLobAndTimestamps() {
        // 上下文就绪本身 = V1 在真 MySQL 执行成功 + validate 通过（spec §7 用例 1）
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P0-4", "真库冒烟", "", "admin"));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "长文本计划", null, "admin"));
        plan.updateBody("正文".repeat(20000)); // @Lob → LONGTEXT 往返
        planRepository.save(plan);
        PersistentTaskPlanRecord reloaded = planRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getBody().length()).isEqualTo(40000);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        // datetime(6) 精度往返：内存原值 vs 读库值相差 ≤1μs（datetime(0..5) 任一截断/舍入都会放大到 ≥10μs）
        assertThat(reloaded.getCreatedAt()).isCloseTo(plan.getCreatedAt(), within(1, ChronoUnit.MICROS));
    }
}
