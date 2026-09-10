CREATE TABLE `plan_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_id` bigint NOT NULL,
  `version_no` varchar(32) NOT NULL,
  `change_note` varchar(1000) NOT NULL,
  `created_by` varchar(80) NOT NULL,
  `author` varchar(80) NOT NULL,
  `snapshot_body` ${lob_type} NOT NULL,
  `plan_phase` varchar(20) NOT NULL,
  `plan_revision` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`plan_id`,`version_no`)
);
