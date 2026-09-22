-- V14__execution_trace_snapshot.sql
-- 执行终态 trace 摘要快照（Task 6）：执行收尾时按耗时 Top500 拉取 queryBasicTraces
-- 摘要落库，供执行详情链路面板在 OAP 数据过期后仍可回看。
-- traces_json 沿用 V1 的 ${lob_type} 约定（测试=clob/生产=longtext）。
CREATE TABLE `execution_trace_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `execution_id` bigint NOT NULL,
  `traces_json` ${lob_type} NOT NULL,
  `total` int NOT NULL,
  `captured_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`execution_id`)
);
