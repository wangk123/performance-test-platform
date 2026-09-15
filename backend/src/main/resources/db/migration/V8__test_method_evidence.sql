ALTER TABLE `scenario_executions` ADD COLUMN `method_hidden` boolean NOT NULL DEFAULT FALSE;
CREATE TABLE `plan_evidence_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_id` bigint NOT NULL,
  `scenario_id` bigint NOT NULL,
  `execution_id` bigint NULL,
  `caption` varchar(200) NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `stored_path` varchar(500) NOT NULL,
  `content_type` varchar(100) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `uploaded_by` varchar(100) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);
CREATE INDEX `idx_evidence_scenario` ON `plan_evidence_images` (`scenario_id`);
CREATE INDEX `idx_evidence_execution` ON `plan_evidence_images` (`execution_id`);
