-- V2：aggregate_report.snapshot_blob 扩容 tinyblob(255B) → longblob。
-- V1 由 Hibernate ddl-auto 在 MySQL 上生成，@Lob byte[] 被 Hibernate 6.6 MySQL 方言默认渲染为
-- tinyblob（同 CLOB→tinytext 行为）；H2 侧 TINYBLOB 为无上限别名故 H2 验证网从未暴露。
-- 存量 H2 数据迁入 MySQL 时聚合快照二进制超长被拒（P0-4 售后修复，双方言实测 H2 认 MODIFY longblob）。
ALTER TABLE aggregate_report MODIFY snapshot_blob longblob;
