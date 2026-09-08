package com.yr.perftest.platform.datamigration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spec §7 用例 2：迁移工具 E2E——H2 造数源库 → runner.migrate → 真 MySQL 目标库：
 * 显式主键原值落位、长文本完整拷贝，迁移后不带主键插入不撞键（自增计数器已越过显式值）。
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
class DataMigrationMysqlIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Autowired
    private DataMigrationRunner runner;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * runner bean 由 @ConditionalOnProperty("app.data-migration.h2-source") 门控：给一个仅含
     * V1 空结构的 H2 文件源，令启动期迁移 0 行空跑成功（bean 即存在；启动迁移是最高优先级
     * ApplicationRunner，先于内置 seeder，且用例直调 migrate 而非依赖自动执行）。
     */
    @DynamicPropertySource
    static void registerStartupProperties(DynamicPropertyRegistry registry) throws Exception {
        Path dir = Files.createTempDirectory("data-migration-mysql-startup-src");
        String url = "jdbc:h2:file:" + dir.resolve("source") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            DataMigrationTestSupport.applyV1Schema(c);
        }
        registry.add("app.data-migration.h2-source", () -> dir.resolve("source").toString());
    }

    /** 清掉上下文启动期 seeder 写入的内置行（demo 用户/内置模板），恢复迁移工具要求的“全空目标”前提。 */
    @BeforeEach
    void clearSeedRows() {
        jdbcTemplate.update("delete from user_accounts");
        jdbcTemplate.update("delete from plan_templates");
    }

    @Test
    void migratesExplicitKeysAndAutoIncrementContinues() throws Exception {
        String sourceUrl = DataMigrationTestSupport.seedPopulatedH2Source(); // 与 T5 单测同款造数夹具
        DataMigrationRunner.MigrationSummary summary = runner.migrate(sourceUrl, false);
        assertThat(summary.tablesCopied()).isEqualTo(DataMigrationRunner.MIGRATION_TABLES.size());
        assertThat(summary.rowsCopied()).isGreaterThanOrEqualTo(3);
        Integer copied = jdbcTemplate.queryForObject("select count(*) from projects where id = 7", Integer.class);
        assertThat(copied).isEqualTo(1); // 显式主键原值落位
        // 迁移后再插入不撞键：MySQL 显式插主键后自增计数器自动顶上（spec §7 用例 2）
        jdbcTemplate.update("insert into projects (code, name, description, owner_username, status) "
                + "values ('MIG-NEW', '迁移后新增', '', 'admin', 'ACTIVE')");
        Long newId = jdbcTemplate.queryForObject("select max(id) from projects", Long.class);
        assertThat(newId).isGreaterThan(7);
    }
}
