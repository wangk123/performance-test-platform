-- V7__plan_status_simplify.sql
-- 状态机简化（spec 2026-09-11 §5）：phase×status 双枚举合并为单一 status。
-- 先放宽列类型再改写值，避免旧 ENUM 不含新值导致写入失败；最终保持 varchar 与 plan_versions.plan_phase 同风格。
ALTER TABLE `task_plans` MODIFY COLUMN `status` varchar(20) NOT NULL;

UPDATE `task_plans` SET `status` = CASE
    WHEN `phase` = 'DRAFT'     AND `status` = 'DRAFT'                    THEN 'PLANNING'
    WHEN `phase` = 'REVIEW'    AND `status` IN ('PENDING', 'IN_REVIEW')  THEN 'IN_REVIEW'
    WHEN `phase` = 'REVIEW'    AND `status` = 'APPROVED'                 THEN 'EXECUTING'
    WHEN `phase` = 'EXECUTION' AND `status` IN ('PENDING', 'RUNNING')    THEN 'EXECUTING'
    WHEN `phase` = 'EXECUTION' AND `status` = 'DONE'                     THEN 'REPORTING'
    WHEN `phase` = 'REPORT'                                              THEN 'REPORTING'
    WHEN `phase` = 'PUBLISH'   AND `status` = 'PUBLISHED'                THEN 'PUBLISHED'
    ELSE 'PLANNING'
  END;

ALTER TABLE `task_plans` DROP COLUMN `phase`;
