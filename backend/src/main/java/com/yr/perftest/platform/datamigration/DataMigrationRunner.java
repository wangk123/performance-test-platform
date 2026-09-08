package com.yr.perftest.platform.datamigration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.sql.DataSource;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * H2→主库表驱动一次性拷贝（spec §5）：固定有序表清单、按原主键显式插入、
 * 批 500 提交、目标非空拒跑（overwrite 显式覆盖）、旧库只读。无 ORM 参与。
 */
@Configuration
@ConditionalOnProperty("app.data-migration.h2-source")
@EnableConfigurationProperties(DataMigrationProperties.class)
public class DataMigrationRunner {

    /** 每批提交行数。 */
    private static final int BATCH_SIZE = 500;

    /** 源库连接账号（历史 H2 文件库默认账号）。 */
    private static final String SOURCE_USER = "sa";

    /** 迁移表清单：与 V1 基线表集合一致（由 DataMigrationRunnerTableListTest 强制）；当前按字母序排列，V1 无外键约束故顺序无关；未来 V2+ 引入外键的表加入时须确保父表先于子表。 */
    static final List<String> MIGRATION_TABLES = List.of(
            "agent_api_keys",
            "aggregate_report",
            "ai_analysis_jobs",
            "auth_tokens",
            "aux_script_bindings",
            "aux_script_executions",
            "aux_script_versions",
            "aux_scripts",
            "change_records",
            "evidence_captures",
            "execution_audit",
            "execution_metric_series",
            "execution_monitor_binding",
            "execution_nodes",
            "execution_target_metrics_snapshot",
            "git_commit_snapshots",
            "git_repositories",
            "idempotency_keys",
            "log_artifacts",
            "model_call_record",
            "model_definition",
            "model_provider",
            "monitor_target",
            "plan_comments",
            "plan_publish_snapshots",
            "plan_share_tokens",
            "plan_templates",
            "project_members",
            "projects",
            "report_compares",
            "request_audit",
            "scenario_executions",
            "script_versions",
            "seed_capture_analysis",
            "seed_capture_analysis_input_lock",
            "seed_capture_analysis_result",
            "seed_capture_chunk",
            "seed_capture_datasource_lease",
            "seed_capture_sample",
            "seed_capture_session",
            "seed_capture_strategy",
            "seed_capture_table",
            "seed_clone_job",
            "seed_datasource",
            "seed_template",
            "task_code_bindings",
            "task_plans",
            "task_scenarios",
            "user_accounts",
            "verification_records");

    private final DataSource targetDataSource;
    private final DataMigrationProperties properties;

    DataMigrationRunner(DataSource targetDataSource, DataMigrationProperties properties) {
        this.targetDataSource = targetDataSource;
        this.properties = properties;
    }

    /** 汇总：tablesCopied=成功处理的表数，rowsCopied=拷贝总行数。 */
    public record MigrationSummary(int tablesCopied, long rowsCopied) {
    }

    /** Spring 启动钩子：Flyway 已在 bean 初始化阶段完成建表，runner 在其后执行（spec §5 时序）。 */
    @Bean
    ApplicationRunner dataMigrationApplicationRunner() {
        return new StartupMigration();
    }

    /** 最高优先级：先于 demoUserSeeder/planTemplateSeed 等内置 seed 执行，避免 seed 行触发“目标非空拒跑”。 */
    private final class StartupMigration implements ApplicationRunner, Ordered {
        @Override
        public void run(ApplicationArguments args) {
            migrate();
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }

    /** 按属性 h2Source/overwrite 执行迁移。 */
    public MigrationSummary migrate() {
        return migrate(sourceUrlFor(properties.h2Source()), properties.overwrite());
    }

    /** 直调入口：sourceUrl 为完整 JDBC 串（生产 = H2 文件只读库，测试 = 独立 H2 mem 库）。 */
    public MigrationSummary migrate(String sourceUrl, boolean overwrite) {
        try (Connection target = targetDataSource.getConnection()) {
            if (!overwrite) {
                // 仅非覆盖模式拒跑非空目标；覆盖模式由单表 DELETE 先清空
                List<String> nonEmptyTables = new ArrayList<>();
                for (String table : MIGRATION_TABLES) {
                    if (countRows(target, table) > 0) {
                        nonEmptyTables.add(table);
                    }
                }
                if (!nonEmptyTables.isEmpty()) {
                    throw new IllegalStateException("目标表非空，拒绝迁移（确认覆盖请设 app.data-migration.overwrite=true）：" + nonEmptyTables);
                }
            }
            try (Connection source = DriverManager.getConnection(sourceUrl, SOURCE_USER, "")) {
                long rowsCopied = 0;
                for (String table : MIGRATION_TABLES) {
                    rowsCopied += copyTable(source, target, table, overwrite);
                }
                return new MigrationSummary(MIGRATION_TABLES.size(), rowsCopied);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("数据迁移失败：" + e.getMessage(), e);
        }
    }

    /** H2 文件库路径 → 只读 JDBC 串（IFEXISTS 保证旧库必须已存在）。 */
    static String sourceUrlFor(String h2SourcePath) {
        return "jdbc:h2:file:" + h2SourcePath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;IFEXISTS=TRUE;ACCESS_MODE_DATA=r";
    }

    /** 单表一事务：overwrite 先清空，再按列交集逐批拷贝；失败回滚并带表名+行号上下文。 */
    private long copyTable(Connection source, Connection target, String table, boolean overwrite) throws SQLException {
        List<String> columns = intersectedColumns(source, target, table);
        if (columns.isEmpty()) {
            return 0;
        }
        long copied = 0;
        boolean previousAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try {
            if (overwrite) {
                try (Statement delete = target.createStatement()) {
                    delete.executeUpdate("DELETE FROM " + table);
                }
            }
            String insert = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES ("
                    + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
            String select = "SELECT " + String.join(", ", columns) + " FROM " + table;
            try (Statement reader = source.createStatement();
                 PreparedStatement ps = target.prepareStatement(insert)) {
                reader.setFetchSize(BATCH_SIZE);
                try (ResultSet rs = reader.executeQuery(select)) {
                    int inBatch = 0;
                    while (rs.next()) {
                        for (int i = 1; i <= columns.size(); i++) {
                            ps.setObject(i, normalize(rs.getObject(i)));
                        }
                        ps.addBatch();
                        copied++;
                        if (++inBatch >= BATCH_SIZE) {
                            ps.executeBatch();
                            inBatch = 0;
                        }
                    }
                    if (inBatch > 0) {
                        ps.executeBatch();
                    }
                }
            }
            target.commit();
            return copied;
        } catch (SQLException e) {
            target.rollback();
            throw new IllegalStateException("表 " + table + " 拷贝失败（批大小=" + BATCH_SIZE
                    + "，已拷贝 " + copied + " 行后中断，事务已回滚）：" + e.getMessage(), e);
        } finally {
            target.setAutoCommit(previousAutoCommit);
        }
    }

    /** 源/目标列交集，保留源列顺序（SELECT 与 INSERT 列一一对应）。 */
    private List<String> intersectedColumns(Connection source, Connection target, String table) throws SQLException {
        LinkedHashSet<String> targetColumns = new LinkedHashSet<>();
        try (Statement st = target.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table + " WHERE 1=0")) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                targetColumns.add(meta.getColumnLabel(i).toLowerCase(Locale.ROOT));
            }
        }
        List<String> common = new ArrayList<>();
        try (Statement st = source.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table + " WHERE 1=0")) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String column = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
                if (targetColumns.contains(column)) {
                    common.add(column);
                }
            }
        }
        return common;
    }

    /** 目标行数。 */
    private long countRows(Connection target, String table) throws SQLException {
        try (Statement st = target.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** LOB 对象降为 String/byte[]，保证跨驱动 setObject 兼容（H2 CLOB→MySQL longtext 等）。 */
    private Object normalize(Object value) throws SQLException {
        if (value instanceof Clob clob) {
            return clob.getSubString(1, (int) clob.length());
        }
        if (value instanceof Blob blob) {
            return blob.getBytes(1, (int) blob.length());
        }
        return value;
    }
}
