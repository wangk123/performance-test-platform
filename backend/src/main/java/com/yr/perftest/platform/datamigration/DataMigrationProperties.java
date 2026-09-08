package com.yr.perftest.platform.datamigration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 一次性迁移开关；h2Source = H2 文件库路径（如 ./storage/perftest）。
 */
@ConfigurationProperties(prefix = "app.data-migration")
public record DataMigrationProperties(String h2Source, boolean overwrite) {
}
