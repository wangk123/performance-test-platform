package com.yr.perftest.platform.datamigration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1 基线表集合 drift-guard（贡献者约定机械化）：{@link DataMigrationRunner#MIGRATION_TABLES}
 * 必须与 V1__baseline.sql 的 CREATE TABLE 集合一致（集合与数量相等，顺序无关——V1 无外键约束），
 * 且不含 flyway_schema_history。新增/删表时二者须同步，否则存量 H2 迁移会漏表。
 */
class DataMigrationRunnerTableListTest {

    /** 匹配 V1 中的 CREATE TABLE `name`（反引号/IF NOT EXISTS 可选）。 */
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z0-9_]+)`?",
            Pattern.CASE_INSENSITIVE);

    @Test
    void migrationTablesMatchV1BaselineTableSet() throws IOException {
        Set<String> v1Tables = new HashSet<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/migration/V1__baseline.sql")) {
            assertThat(in).as("db/migration/V1__baseline.sql 须在 classpath（main resources 对测试可见）").isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = CREATE_TABLE.matcher(sql);
            while (matcher.find()) {
                v1Tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        assertThat(v1Tables).as("V1 应解析出 CREATE TABLE 清单").isNotEmpty();
        assertThat(Set.copyOf(DataMigrationRunner.MIGRATION_TABLES))
                .as("MIGRATION_TABLES 与 V1 基线表集合须一致（新增表须同步清单）")
                .containsExactlyInAnyOrderElementsOf(v1Tables);
        assertThat(DataMigrationRunner.MIGRATION_TABLES).hasSameSizeAs(v1Tables);
        assertThat(DataMigrationRunner.MIGRATION_TABLES).doesNotContain("flyway_schema_history");
    }
}
