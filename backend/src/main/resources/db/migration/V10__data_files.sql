CREATE TABLE data_files (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id  BIGINT NOT NULL,
  name        VARCHAR(200) NOT NULL,
  remark      VARCHAR(500) NULL,
  created_by  VARCHAR(64) NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_data_file_name UNIQUE (project_id, name)
);

CREATE TABLE data_file_versions (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  data_file_id         BIGINT NOT NULL,
  version_no           INT NOT NULL,
  original_filename    VARCHAR(255) NOT NULL,
  stored_path          VARCHAR(500) NOT NULL,
  size_bytes           BIGINT NOT NULL,
  row_count            BIGINT NULL,
  header_columns_json  TEXT NULL,
  sha256               CHAR(64) NOT NULL,
  uploaded_by          VARCHAR(64) NULL,
  uploaded_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  remark               VARCHAR(500) NULL,
  CONSTRAINT uk_data_file_version UNIQUE (data_file_id, version_no)
);

ALTER TABLE task_scenarios ADD COLUMN data_file_bindings_json TEXT NULL;
