package com.yr.perftest.platform.config;

import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

/**
 * 平台 MySQL 运行时方言（application.yml → spring.jpa.properties.hibernate.dialect）。
 *
 * <p>Hibernate 6.6 的 MySQLDialect 把 {@code @Lob String} 映射为 CLOB 并把 CLOB 渲染成
 * tinytext（≤255 字节），ddl-auto=validate 对 V1 基线的 longtext 列（${lob_type}=longtext，
 * 规避 tinytext 截断的 P0-4 实证定论）一律报 wrong column type。本方言把提取到的 longtext
 * 列解析为 CLOB，使 validate 判等与 longtext 运行时语义并存（已由 testMysql 真 MySQL IT
 * 与 216 真库默认配置 boot 实证）。
 *
 * <p>注：测试侧 application.yml 整体遮蔽主配置（H2 验证网不用 MySQL 方言），故真 MySQL IT
 * 仍以内联 properties 显式指向本方言。
 */
public class MysqlLongtextDialect extends MySQLDialect {

    public MysqlLongtextDialect(DialectResolutionInfo resolutionInfo) {
        super(resolutionInfo);
    }

    @Override
    public JdbcType resolveSqlTypeDescriptor(String columnTypeName, int jdbcTypeCode, int precision, int scale,
            JdbcTypeRegistry jdbcTypeRegistry) {
        if ("longtext".equalsIgnoreCase(columnTypeName)) {
            // validate 判等通道：found longtext → CLOB，与 @Lob String 期望的 CLOB 匹配
            return jdbcTypeRegistry.getDescriptor(SqlTypes.CLOB);
        }
        return super.resolveSqlTypeDescriptor(columnTypeName, jdbcTypeCode, precision, scale, jdbcTypeRegistry);
    }
}
