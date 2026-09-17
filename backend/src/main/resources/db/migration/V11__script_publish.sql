-- V11__script_publish.sql
-- 脚本版本发布模型（spec 2026-09-17 §4）：scripts 主表 + script_versions 挂靠与状态列。
-- status 用 varchar 而非 ENUM，沿用 V7 的列风格决策（放宽与改值无痛）。
CREATE TABLE scripts (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  project_id        BIGINT NOT NULL,
  name              VARCHAR(200) NOT NULL,
  latest_version_no INT NOT NULL DEFAULT 0,
  created_by        VARCHAR(80) NULL,
  created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_script_name UNIQUE (project_id, name)
);

ALTER TABLE script_versions ADD COLUMN script_id BIGINT NULL;
ALTER TABLE script_versions ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE script_versions ADD COLUMN remark VARCHAR(512) NULL;
ALTER TABLE script_versions ADD COLUMN updated_at DATETIME(6) NULL;

ALTER TABLE script_versions
  ADD CONSTRAINT fk_sv_script FOREIGN KEY (script_id) REFERENCES scripts (id);

-- MySQL/H2(MODE=MySQL) 均允许多行 NULL，存量行 script_id 为 NULL 不冲突；
-- V12 回填后每行都有 script_id，(script_id, version_no) 唯一生效。
CREATE UNIQUE INDEX uk_script_version ON script_versions (script_id, version_no);
