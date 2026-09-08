package com.yr.perftest.platform.datamigration;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * H2→主库迁移测试共用造数夹具（T5 单测 + T6 真 MySQL IT 同源）：
 * 源库用同一 V1（${lob_type}→clob 文本替换后执行）建表，样例行覆盖三要素——
 * 显式主键（projects id=7 / report_compares id=9）、长文本（summary_json 1 万字符）、时间戳。
 */
final class DataMigrationTestSupport {

    private DataMigrationTestSupport() {
    }

    /** 在给定 H2 连接上执行 V1 建表（Flyway 占位符不进 ScriptUtils，直接文本替换）。 */
    static void applyV1Schema(Connection c) throws Exception {
        String script;
        try (var in = new ClassPathResource("db/migration/V1__baseline.sql").getInputStream()) {
            script = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
        String resolved = script.replace("${lob_type}", "clob");
        ScriptUtils.executeSqlScript(c, new ByteArrayResource(resolved.getBytes(StandardCharsets.UTF_8)));
    }

    /** 插入三要素样例行：显式主键 + 长文本(summary_json) + 时间戳。 */
    static void insertSampleRows(Connection c) throws Exception {
        try (Statement s = c.createStatement()) {
            s.execute("insert into projects (id, code, name, owner_username, status) "
                    + "values (7, 'MIG-7', '迁移项目', 'admin', 'ACTIVE')");
            s.execute("insert into user_accounts (username, display_name, password, enabled, role) "
                    + "values ('migrated-user', '迁移用户', 'h', true, 'ADMIN')");
            s.execute("insert into report_compares (id, base_plan_id, target_plan_id, created_at, created_by, summary_json) "
                    + "values (9, 1, 2, now(), 'admin', repeat('x', 10000))");
        }
    }

    /** 造一个 V1 结构 + 样例数据的独立 H2 mem 源库，返回其 JDBC 串（DB_CLOSE_DELAY=-1：断连后库存活供迁移工具重连）。 */
    static String seedPopulatedH2Source() throws Exception {
        String url = "jdbc:h2:mem:migsrc" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            applyV1Schema(c);
            insertSampleRows(c);
        }
        return url;
    }
}
