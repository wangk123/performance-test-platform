package com.yr.perftest.platform.monitoring;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
public class MonitoringSchemaInitializer {
    @Bean
    public ApplicationRunner monitorTargetSchemaInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        return args -> {
            try (Connection connection = dataSource.getConnection()) {
                String database = connection.getMetaData().getDatabaseProductName();
                if (database.contains("H2")) {
                    jdbcTemplate.execute("alter table monitor_target alter column type varchar(40) not null");
                    addColumnIfMissing(jdbcTemplate, "monitor_target", "ssh_username", "varchar(80)");
                    addColumnIfMissing(jdbcTemplate, "monitor_target", "ssh_password", "varchar(200)");
                    addColumnIfMissing(jdbcTemplate, "monitor_target", "ssh_port", "integer");
                    addColumnIfMissing(jdbcTemplate, "monitor_target", "plugin_dir", "varchar(500)");
                } else if (database.contains("MySQL")) {
                    jdbcTemplate.execute("alter table monitor_target modify column type varchar(40) not null");
                    addColumnIfMissing(jdbcTemplate, "monitor_target", "ssh_username", "varchar(80)");
                    addColumnIfMissing(jdbcTemplate, "monitor_target", "ssh_password", "varchar(200)");
                    addColumnIfMissing(jdbcTemplate, "monitor_target", "ssh_port", "int");
                    addColumnIfMissing(jdbcTemplate, "monitor_target", "plugin_dir", "varchar(500)");
                }
            }
        };
    }

    private void addColumnIfMissing(JdbcTemplate jdbcTemplate, String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where lower(table_name) = ? and lower(column_name) = ?",
                Integer.class,
                table.toLowerCase(),
                column.toLowerCase()
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute("alter table " + table + " add column " + column + " " + definition);
        }
    }
}
