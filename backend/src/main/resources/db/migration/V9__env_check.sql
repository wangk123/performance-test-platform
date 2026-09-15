CREATE TABLE env_check_credential (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id   BIGINT       NOT NULL,
  plan_id      BIGINT       NULL,               -- 空=项目级；非空=该计划覆盖
  host         VARCHAR(255) NOT NULL,
  ssh_port     INT          NOT NULL DEFAULT 22,
  username     VARCHAR(128) NOT NULL,
  secret_cipher VARCHAR(4096) NOT NULL,          -- AES-GCM 密文（密码或 RSA PEM 私钥内容）
  auth_type    VARCHAR(20)  NOT NULL,            -- PASSWORD / KEY
  remark       VARCHAR(255) NULL,
  created_by   VARCHAR(64)  NOT NULL,
  created_at   DATETIME     NOT NULL,
  updated_at   DATETIME     NOT NULL,
  KEY idx_proj_host (project_id, host)
);

CREATE TABLE env_check_run (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id      BIGINT       NOT NULL,
  triggered_by VARCHAR(64)  NOT NULL,
  started_at   DATETIME     NOT NULL,
  finished_at  DATETIME     NULL,
  passed       INT          NOT NULL DEFAULT 0,  -- OK+FIXED 数
  warned       INT          NOT NULL DEFAULT 0,  -- WARNING 数
  detail_json  ${lob_type}  NULL,                -- 完整矩阵：targets[] + results[]{host,itemKey,state,detail,suggestion,method,risk,fixable}。H2 侧 LONGTEXT=varchar 无法过 @Lob validate，沿用 V1 的 ${lob_type} 约定（测试=clob/生产=longtext）
  KEY idx_run_plan (plan_id)
);

CREATE TABLE env_check_fix (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id        BIGINT       NOT NULL,
  host          VARCHAR(255) NOT NULL,
  item_key      VARCHAR(64)  NOT NULL,
  risk_level    VARCHAR(10)  NOT NULL,
  backup_ref    VARCHAR(512) NOT NULL,           -- 备份标识（目标机备份路径或旧值）
  diff_text     ${lob_type}  NULL,               -- 改动差异（参数：旧→新；文件：统一 diff）。H2 侧 TEXT=varchar 无法过 @Lob validate，沿用 V1 的 ${lob_type} 约定（测试=clob/生产=longtext）
  summary       VARCHAR(512) NULL,
  applied_by    VARCHAR(64)  NOT NULL,
  applied_at    DATETIME     NOT NULL,
  rolled_back_at DATETIME    NULL,
  KEY idx_fix_run (run_id)
);
