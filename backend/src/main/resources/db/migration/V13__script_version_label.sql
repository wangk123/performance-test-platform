-- V13__script_version_label.sql
-- 版本号规则语义化（x.x.x）：version_no 保留为内部发布序号与排序键，
-- version_label 承载用户可见的语义化版本，递增校验按 label 逐段比较。
ALTER TABLE script_versions ADD COLUMN version_label VARCHAR(20) NULL;
ALTER TABLE scripts ADD COLUMN latest_version_label VARCHAR(20) NULL;

UPDATE script_versions SET version_label = CONCAT(version_no, '.0.0') WHERE status = 'PUBLISHED';
UPDATE scripts s
  SET s.latest_version_label = (
    SELECT CONCAT(MAX(v.version_no), '.0.0')
    FROM script_versions v
    WHERE v.script_id = s.id AND v.status = 'PUBLISHED'
  );
