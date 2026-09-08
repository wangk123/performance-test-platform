-- P0-4 V1 baseline: generated from Hibernate ddl-auto=update on MySQL 8.0 via deploy/mysql compose; dual-dialect (H2 MODE=MySQL / MySQL)。禁止手改，变更走 V2+。

CREATE TABLE `agent_api_keys` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `key_hash` varchar(64) NOT NULL,
  `prefix` varchar(16) NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `scope` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`key_hash`)
);
CREATE TABLE `aggregate_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `accuracy` varchar(16) NOT NULL,
  `builder_version` varchar(32) DEFAULT NULL,
  `duration_seconds` double DEFAULT NULL,
  `end_ms` bigint DEFAULT NULL,
  `execution_id` bigint NOT NULL,
  `generated_at` datetime(6) NOT NULL,
  `rows_json` ${lob_type} NOT NULL,
  `snapshot_blob` tinyblob,
  `start_ms` bigint DEFAULT NULL,
  `summary_json` ${lob_type} NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`execution_id`)
);
CREATE TABLE `ai_analysis_jobs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `execution_id` bigint NOT NULL,
  `input_facts` ${lob_type},
  `model_id` bigint NOT NULL,
  `prompt_version` varchar(16) NOT NULL,
  `requested_by` varchar(64) NOT NULL,
  `result` ${lob_type},
  `status` varchar(16) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `auth_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  `username` varchar(80) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`token_hash`)
);
CREATE TABLE `aux_script_bindings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(64) NOT NULL,
  `failure_policy` enum('CONTINUE','MANUAL_CONFIRM','STOP_TASK') NOT NULL,
  `phase` enum('POST','PRE') NOT NULL,
  `scenario_id` bigint NOT NULL,
  `script_version_id` bigint NOT NULL,
  `sort_order` int NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `aux_script_executions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `binding_id` bigint NOT NULL,
  `ended_at` datetime(6) DEFAULT NULL,
  `execution_id` bigint NOT NULL,
  `exit_code` int NOT NULL,
  `log_path` varchar(512) DEFAULT NULL,
  `phase` enum('POST','PRE') NOT NULL,
  `script_version_id` bigint NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `status` enum('AWAITING_CONFIRMATION','FAILED','SKIPPED','SUCCESS','TIMEOUT') NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `aux_script_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(64) NOT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `script_id` bigint NOT NULL,
  `source_code` ${lob_type} NOT NULL,
  `version_no` int NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `aux_scripts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(64) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `project_id` bigint NOT NULL,
  `scope` enum('PROJECT','SYSTEM') NOT NULL,
  `type` enum('PYTHON','SHELL') NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `change_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `change_ref` varchar(128) NOT NULL,
  `change_type` enum('CODE','CONFIG') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `registered_by_name` varchar(128) NOT NULL,
  `registered_by_type` varchar(16) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `evidence_captures` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_by_name` varchar(128) DEFAULT NULL,
  `approved_by_type` varchar(16) DEFAULT NULL,
  `bundle_path` varchar(512) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `cost_note` varchar(512) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `execution_id` bigint NOT NULL,
  `impact_level` enum('HIGH','LOW','MEDIUM','NONE') NOT NULL,
  `kind` varchar(48) NOT NULL,
  `purpose` varchar(512) NOT NULL,
  `requested_by_name` varchar(128) NOT NULL,
  `requested_by_type` varchar(16) NOT NULL,
  `status` enum('APPROVED','COMPLETED','PENDING_APPROVAL','REJECTED') NOT NULL,
  `summary_json` varchar(4096) DEFAULT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `execution_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `execution_id` bigint NOT NULL,
  `principal_name` varchar(128) NOT NULL,
  `principal_type` varchar(16) NOT NULL,
  `replayed` bit(1) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `execution_metric_series` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `avg_rt_ms` bigint NOT NULL,
  `bucket_time_ms` bigint NOT NULL,
  `error_samples` bigint NOT NULL,
  `execution_id` bigint NOT NULL,
  `label` varchar(256) NOT NULL,
  `p95_rt_ms` bigint NOT NULL,
  `samples` bigint NOT NULL,
  `throughput` double NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE INDEX `idx_metric_series_exec_label_time` ON `execution_metric_series` (`execution_id`,`label`,`bucket_time_ms`);
CREATE INDEX `idx_metric_series_exec_time` ON `execution_metric_series` (`execution_id`,`bucket_time_ms`);
CREATE TABLE `execution_monitor_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `end_time` datetime(6) DEFAULT NULL,
  `execution_id` bigint NOT NULL,
  `start_time` datetime(6) DEFAULT NULL,
  `target_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `execution_nodes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `host` varchar(160) NOT NULL,
  `last_checked_at` datetime(6) DEFAULT NULL,
  `last_message` varchar(2000) DEFAULT NULL,
  `name` varchar(120) NOT NULL,
  `remote_work_dir` varchar(1000) NOT NULL,
  `role` enum('BOTH','CONTROLLER','WORKER') NOT NULL,
  `ssh_key_path` varchar(1000) NOT NULL,
  `ssh_port` int NOT NULL,
  `ssh_username` varchar(80) NOT NULL,
  `status` enum('AVAILABLE','OFFLINE','UNKNOWN') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `execution_target_metrics_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `captured_at` datetime(6) NOT NULL,
  `execution_id` bigint NOT NULL,
  `kind` varchar(32) NOT NULL,
  `series_json` ${lob_type} NOT NULL,
  `unit` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`execution_id`,`kind`)
);
CREATE TABLE `git_commit_snapshots` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author` varchar(128) DEFAULT NULL,
  `author_time` datetime(6) NOT NULL,
  `branch` varchar(128) NOT NULL,
  `commit_id` varchar(64) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `message` varchar(512) DEFAULT NULL,
  `repository_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `git_repositories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `auth_type` varchar(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(64) NOT NULL,
  `credential` varchar(256) DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `project_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL,
  `url` varchar(512) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `idempotency_keys` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `execution_id` bigint NOT NULL,
  `idem_key` varchar(128) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`idem_key`)
);
CREATE TABLE `log_artifacts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `execution_id` bigint NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_path` varchar(512) NOT NULL,
  `index_status` varchar(16) NOT NULL,
  `uploaded_by` varchar(64) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `model_call_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_type` enum('ANTHROPIC','OPENAI') NOT NULL,
  `completion_tokens` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `error_message` varchar(2000) DEFAULT NULL,
  `latency_ms` bigint DEFAULT NULL,
  `model_id` bigint DEFAULT NULL,
  `model_name_snapshot` varchar(200) DEFAULT NULL,
  `prompt_tokens` int DEFAULT NULL,
  `provider_id` bigint DEFAULT NULL,
  `provider_name_snapshot` varchar(120) DEFAULT NULL,
  `request_body` ${lob_type},
  `response_body` ${lob_type},
  `scene` enum('REPORT_ANALYSIS','TEST_CONNECTION') NOT NULL,
  `status` enum('FAILED','SUCCESS') NOT NULL,
  `total_tokens` int DEFAULT NULL,
  `triggered_by` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `model_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_type` varchar(40) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `display_name` varchar(200) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `is_default` bit(1) NOT NULL,
  `model_name` varchar(200) NOT NULL,
  `provider_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`provider_id`,`model_name`)
);
CREATE TABLE `model_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_key` varchar(500) NOT NULL,
  `base_url` varchar(500) NOT NULL,
  `base_url_anthropic` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `name` varchar(120) NOT NULL,
  `store_body_default` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`name`)
);
CREATE TABLE `monitor_target` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `env` varchar(40) NOT NULL,
  `host` varchar(160) NOT NULL,
  `items_json` ${lob_type},
  `labels_json` ${lob_type},
  `last_check_message` varchar(1000) DEFAULT NULL,
  `last_check_status` enum('FAILED','SUCCESS','UNKNOWN') NOT NULL,
  `last_checked_at` datetime(6) DEFAULT NULL,
  `metrics_path` varchar(120) NOT NULL,
  `name` varchar(120) NOT NULL,
  `plugin_dir` varchar(500) DEFAULT NULL,
  `port` int NOT NULL,
  `project_id` bigint NOT NULL,
  `service_name` varchar(120) NOT NULL,
  `ssh_password` varchar(200) DEFAULT NULL,
  `ssh_port` int DEFAULT NULL,
  `ssh_username` varchar(80) DEFAULT NULL,
  `type` varchar(40) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `plan_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author` varchar(80) NOT NULL,
  `content` ${lob_type} NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `kind` enum('REVIEW','SYSTEM') NOT NULL,
  `plan_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `plan_publish_snapshots` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_json` ${lob_type} NOT NULL,
  `plan_id` bigint NOT NULL,
  `published_at` datetime(6) NOT NULL,
  `published_by` varchar(80) NOT NULL,
  `revision` int NOT NULL,
  `scenario_json` ${lob_type} NOT NULL,
  `summary_json` ${lob_type},
  PRIMARY KEY (`id`),
  UNIQUE (`plan_id`,`revision`)
);
CREATE TABLE `plan_share_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(80) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `plan_id` bigint NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `token` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`token`)
);
CREATE TABLE `plan_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `builtin` bit(1) NOT NULL,
  `content` ${lob_type} NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(80) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `name` varchar(160) NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `project_members` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `role` enum('MEMBER','OWNER') NOT NULL,
  `username` varchar(80) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`project_id`,`username`)
);
CREATE TABLE `projects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(80) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `name` varchar(120) NOT NULL,
  `owner_username` varchar(80) NOT NULL,
  `status` enum('ACTIVE','ARCHIVED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`code`)
);
CREATE TABLE `report_compares` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `base_plan_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(64) NOT NULL,
  `summary_json` ${lob_type} NOT NULL,
  `target_plan_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `request_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `duration_ms` bigint NOT NULL,
  `method` varchar(16) NOT NULL,
  `path` varchar(512) NOT NULL,
  `principal_name` varchar(128) NOT NULL,
  `principal_type` varchar(16) NOT NULL,
  `query` varchar(1024) DEFAULT NULL,
  `request_id` varchar(36) NOT NULL,
  `status_code` int NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `scenario_executions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_json` ${lob_type} NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `end_time` datetime(6) DEFAULT NULL,
  `error_message` varchar(2000) DEFAULT NULL,
  `execution_name` varchar(400) DEFAULT NULL,
  `exit_code` int DEFAULT NULL,
  `log_file_path` varchar(1000) DEFAULT NULL,
  `result_file_path` varchar(1000) DEFAULT NULL,
  `scenario_id` bigint NOT NULL,
  `start_time` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','FAILED','INTERRUPTED','QUEUED','RUNNING','STOPPING','SUCCESS') NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `script_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `original_filename` varchar(255) NOT NULL,
  `project_id` bigint NOT NULL,
  `stored_path` varchar(1000) NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `uploaded_by` varchar(80) NOT NULL,
  `version_no` int NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `seed_capture_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `candidate_operation_count` int NOT NULL,
  `compared_rows` bigint NOT NULL,
  `completed_tables` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `current_tables_json` ${lob_type} NOT NULL,
  `error_message` varchar(2000) DEFAULT NULL,
  `fine_screened_chunks` int NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `heartbeat_at` datetime(6) DEFAULT NULL,
  `input_manifest_json` ${lob_type} NOT NULL,
  `input_sample_ids_json` ${lob_type} NOT NULL,
  `phase` varchar(32) NOT NULL,
  `project_id` bigint NOT NULL,
  `skipped_tables` int NOT NULL,
  `status` varchar(32) NOT NULL,
  `strategy_id` bigint NOT NULL,
  `summary_json` ${lob_type} NOT NULL,
  `template_id` bigint DEFAULT NULL,
  `total_tables` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE INDEX `idx_seed_capture_analysis_project_status` ON `seed_capture_analysis` (`project_id`,`status`);
CREATE INDEX `idx_seed_capture_analysis_strategy_status` ON `seed_capture_analysis` (`strategy_id`,`status`);
CREATE TABLE `seed_capture_analysis_input_lock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `analysis_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `sample_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`sample_id`)
);
CREATE INDEX `idx_seed_capture_analysis_lock_analysis` ON `seed_capture_analysis_input_lock` (`analysis_id`);
CREATE TABLE `seed_capture_analysis_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `analysis_id` bigint NOT NULL,
  `chunk_seq` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `file_checksum` varchar(128) DEFAULT NULL,
  `relative_path` varchar(1000) DEFAULT NULL,
  `result_type` varchar(64) NOT NULL,
  `row_count` bigint NOT NULL,
  `summary_json` ${lob_type},
  `table_name` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE INDEX `idx_seed_capture_analysis_result_analysis` ON `seed_capture_analysis_result` (`analysis_id`);
CREATE TABLE `seed_capture_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `byte_size` bigint NOT NULL,
  `chunk_seq` int NOT NULL,
  `content_hash` varchar(128) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `file_checksum` varchar(128) DEFAULT NULL,
  `pk_range_end` varchar(1000) DEFAULT NULL,
  `pk_range_start` varchar(1000) DEFAULT NULL,
  `relative_path` varchar(1000) DEFAULT NULL,
  `row_count` bigint NOT NULL,
  `sample_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL,
  `table_name` varchar(255) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`sample_id`,`table_name`,`chunk_seq`)
);
CREATE INDEX `idx_seed_capture_chunk_sample_table_status` ON `seed_capture_chunk` (`sample_id`,`table_name`,`status`);
CREATE TABLE `seed_capture_datasource_lease` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `acquired_at` datetime(6) NOT NULL,
  `datasource_id` bigint NOT NULL,
  `heartbeat_at` datetime(6) NOT NULL,
  `sample_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`datasource_id`)
);
CREATE INDEX `idx_seed_capture_datasource_lease_heartbeat` ON `seed_capture_datasource_lease` (`heartbeat_at`);
CREATE TABLE `seed_capture_sample` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active_workers` int NOT NULL,
  `capture_finished_at` datetime(6) DEFAULT NULL,
  `capture_started_at` datetime(6) NOT NULL,
  `captured_rows` bigint NOT NULL,
  `completed_tables` int NOT NULL,
  `config_snapshot_json` ${lob_type} NOT NULL,
  `config_version` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `current_tables_json` ${lob_type} NOT NULL,
  `datasource_id` bigint NOT NULL,
  `error_message` varchar(2000) DEFAULT NULL,
  `heartbeat_at` datetime(6) DEFAULT NULL,
  `incomplete` bit(1) NOT NULL,
  `phase` varchar(32) NOT NULL,
  `project_id` bigint NOT NULL,
  `sample_seq` int NOT NULL,
  `status` varchar(32) NOT NULL,
  `strategy_id` bigint NOT NULL,
  `total_tables` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `written_bytes` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`strategy_id`,`sample_seq`)
);
CREATE INDEX `idx_seed_capture_sample_strategy_time` ON `seed_capture_sample` (`strategy_id`,`capture_started_at`,`sample_seq`);
CREATE INDEX `idx_seed_capture_sample_datasource_status` ON `seed_capture_sample` (`datasource_id`,`status`);
CREATE TABLE `seed_capture_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `baseline_json` ${lob_type},
  `created_at` datetime(6) NOT NULL,
  `datasource_id` bigint NOT NULL,
  `exclude_json` ${lob_type} NOT NULL,
  `include_json` ${lob_type} NOT NULL,
  `project_id` bigint NOT NULL,
  `provider` varchar(32) NOT NULL,
  `samples_json` ${lob_type},
  `status` varchar(32) NOT NULL,
  `table_set_json` ${lob_type},
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `seed_capture_strategy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_rows` int NOT NULL,
  `config_version` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `datasource_id` bigint NOT NULL,
  `exclude_json` ${lob_type} NOT NULL,
  `include_json` ${lob_type} NOT NULL,
  `name` varchar(160) NOT NULL,
  `project_id` bigint NOT NULL,
  `thread_count` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE INDEX `idx_seed_capture_strategy_project_updated` ON `seed_capture_strategy` (`project_id`,`updated_at`);
CREATE INDEX `idx_seed_capture_strategy_datasource` ON `seed_capture_strategy` (`project_id`,`datasource_id`);
CREATE TABLE `seed_capture_table` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content_hash` varchar(128) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `error_message` varchar(2000) DEFAULT NULL,
  `incomplete` bit(1) NOT NULL,
  `risky_no_pk` bit(1) NOT NULL,
  `row_count` bigint NOT NULL,
  `sample_id` bigint NOT NULL,
  `schema_hash` varchar(128) DEFAULT NULL,
  `schema_json` ${lob_type} NOT NULL,
  `status` varchar(32) NOT NULL,
  `table_name` varchar(255) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`sample_id`,`table_name`)
);
CREATE INDEX `idx_seed_capture_table_sample_status` ON `seed_capture_table` (`sample_id`,`status`);
CREATE TABLE `seed_clone_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `clone_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(120) NOT NULL,
  `datasource_id` bigint NOT NULL,
  `error_json` ${lob_type},
  `failed_batches` int NOT NULL,
  `failure_policy` varchar(32) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `project_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL,
  `success_batches` int NOT NULL,
  `template_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `seed_datasource` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `database_name` varchar(120) NOT NULL,
  `host` varchar(255) NOT NULL,
  `name` varchar(120) NOT NULL,
  `password_enc` varchar(1000) NOT NULL,
  `port` int NOT NULL,
  `project_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `username` varchar(120) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `seed_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `capture_session_id` bigint NOT NULL,
  `body_json` ${lob_type} NOT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `confirmed_by` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `project_id` bigint NOT NULL,
  `seed_rows_json` ${lob_type},
  `status` varchar(32) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version_no` int NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `task_code_bindings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `branch` varchar(128) NOT NULL,
  `commit_id` varchar(64) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(64) NOT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `repository_id` bigint NOT NULL,
  `scenario_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `task_plans` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body` ${lob_type},
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(80) NOT NULL,
  `default_controller_node_id` bigint DEFAULT NULL,
  `default_monitor_target_ids_json` ${lob_type},
  `default_worker_node_ids_json` ${lob_type},
  `name` varchar(160) NOT NULL,
  `phase` enum('DRAFT','EXECUTION','PUBLISH','REPORT','REVIEW') NOT NULL,
  `precheck_executed_at` datetime(6) DEFAULT NULL,
  `precheck_json` ${lob_type},
  `project_id` bigint NOT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `remark` varchar(1000) DEFAULT NULL,
  `revision` int NOT NULL,
  `status` enum('APPROVED','DONE','DRAFT','GENERATING','IN_REVIEW','PENDING','PUBLISHED','RUNNING') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE `task_scenarios` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `controller_node_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `duration` int NOT NULL,
  `jmeter_properties_json` ${lob_type} NOT NULL,
  `loops` int NOT NULL,
  `monitor_target_ids_json` ${lob_type},
  `name` varchar(160) NOT NULL,
  `plan_id` bigint NOT NULL,
  `purpose` ${lob_type},
  `ramp_up` int NOT NULL,
  `script_version_id` bigint DEFAULT NULL,
  `sort_order` int NOT NULL,
  `test_type` enum('BENCHMARK','COMPOSITE','SINGLE_TXN','STABILITY') DEFAULT NULL,
  `thread_group_configs_json` ${lob_type},
  `threads` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `worker_node_ids_json` ${lob_type},
  PRIMARY KEY (`id`)
);
CREATE TABLE `user_accounts` (
  `username` varchar(80) NOT NULL,
  `display_name` varchar(120) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `password` varchar(120) NOT NULL,
  `role` enum('ADMIN','PROJECT_MEMBER','PROJECT_OWNER') NOT NULL,
  PRIMARY KEY (`username`)
);
CREATE TABLE `verification_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `baseline_execution_id` bigint NOT NULL,
  `candidate_execution_id` bigint NOT NULL,
  `change_record_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `details_json` varchar(8192) NOT NULL,
  `reasons_json` varchar(1024) NOT NULL,
  `requested_by_name` varchar(128) NOT NULL,
  `requested_by_type` varchar(16) NOT NULL,
  `verdict` varchar(16) NOT NULL,
  PRIMARY KEY (`id`)
);
