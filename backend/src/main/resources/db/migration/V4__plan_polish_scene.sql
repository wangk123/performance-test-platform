-- 章节 AI 润色：model_call_record.scene 扩展 PLAN_POLISH 取值
ALTER TABLE `model_call_record` MODIFY COLUMN `scene` enum('REPORT_ANALYSIS','TEST_CONNECTION','PLAN_POLISH') NOT NULL;
