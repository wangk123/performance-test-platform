package com.yr.perftest.platform.datamigration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2→H2 迁移往返测试：源库用同一 V1（${lob_type}→clob 文本替换后执行）造数，
 * 目标库为测试上下文库（Flyway V1 已建），验证显式主键/长文本/时间戳拷贝语义。
 */
@SpringBootTest(properties = {
        // 不带 DB_CLOSE_DELAY=-1：每个 @DirtiesContext 重建时连接全关、库即销毁，保证测试间目标库干净
        "spring.datasource.url=jdbc:h2:mem:data-migration-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DataMigrationRunnerTest {

    @Autowired
    private DataSource targetDataSource;
    @Autowired
    private DataMigrationRunner runner;

    /** 清掉上下文启动期 seeder 写入的内置行（demo 用户/内置模板），恢复迁移工具要求的“全空目标”前提。 */
    @BeforeEach
    void clearSeedRows() throws Exception {
        try (Connection c = targetDataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("delete from user_accounts");
            s.executeUpdate("delete from plan_templates");
        }
    }

    /**
     * SpringBootContextLoader 会真实执行 ApplicationRunner：先在磁盘造一个仅含 V1 结构的
     * H2 文件源库，令启动期迁移空跑（0 行）成功；三个用例再直调 migrate 覆盖拷贝/拒跑/覆盖语义。
     */
    @DynamicPropertySource
    static void registerStartupProperties(DynamicPropertyRegistry registry) throws Exception {
        Path dir = Files.createTempDirectory("data-migration-startup-src");
        String url = "jdbc:h2:file:" + dir.resolve("source") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            DataMigrationTestSupport.applyV1Schema(c);
        }
        registry.add("app.data-migration.h2-source", () -> dir.resolve("source").toString());
    }

    /** 造一个带数据的独立 H2 mem 源库（跑同一 V1 建表后插三要素样例行），造数逻辑见共用夹具。 */
    private String seedSourceDb() throws Exception {
        return DataMigrationTestSupport.seedPopulatedH2Source();
    }

    @Test
    void copiesRowsWithExplicitPrimaryKeysIntoFlywaySchema() throws Exception {
        String sourceUrl = seedSourceDb();
        DataMigrationRunner.MigrationSummary summary = runner.migrate(sourceUrl, false);
        assertThat(summary.tablesCopied()).isEqualTo(DataMigrationRunner.MIGRATION_TABLES.size());
        assertThat(summary.rowsCopied()).isGreaterThanOrEqualTo(3);
        try (Connection c = targetDataSource.getConnection(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("select count(*) from projects where id = 7");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
            ResultSet rs2 = s.executeQuery("select length(summary_json) from report_compares where id = 9");
            rs2.next();
            assertThat(rs2.getInt(1)).isEqualTo(10000);
            ResultSet rs3 = s.executeQuery("select count(*) from user_accounts where username = 'migrated-user'");
            rs3.next();
            assertThat(rs3.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void refusesWhenTargetTableNotEmpty() throws Exception {
        String sourceUrl = seedSourceDb();
        runner.migrate(sourceUrl, false);
        assertThatThrownBy(() -> runner.migrate(seedSourceDb(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非空")
                .hasMessageContaining("projects");
    }

    @Test
    void overwriteClearsThenCopies() throws Exception {
        runner.migrate(seedSourceDb(), false);
        DataMigrationRunner.MigrationSummary second = runner.migrate(seedSourceDb(), true);
        assertThat(second.rowsCopied()).isGreaterThanOrEqualTo(3);
        try (Connection c = targetDataSource.getConnection(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("select count(*) from projects");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }
}
