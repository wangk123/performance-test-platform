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
                } else if (database.contains("MySQL")) {
                    jdbcTemplate.execute("alter table monitor_target modify column type varchar(40) not null");
                }
            }
        };
    }
}
