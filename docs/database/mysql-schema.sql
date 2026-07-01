-- ============================================================
-- 性能测试平台 - MySQL 数据库初始化脚本
-- 版本: v1.0
-- 生成时间: 2026-07-01
-- 说明:
--   1. 此文件与 JPA 实体 (Hibernate ddl-auto: update) 保持同步
--   2. 实体变更后请手动更新此文件，保持与代码一致
--   3. 目标数据库: MySQL 8.0+
--   4. 存储引擎: InnoDB，字符集: utf8mb4
-- ============================================================

-- ============================================================
-- 01. 用户账户
-- 说明: 平台用户，主键 username 由应用层分配
-- ============================================================
DROP TABLE IF EXISTS `user_accounts`;
CREATE TABLE `user_accounts` (
    `username`      VARCHAR(80)   NOT NULL COMMENT '用户名（主键）',
    `password`      VARCHAR(120)  NOT NULL COMMENT '密码（明文存储，仅开发环境）',
    `display_name`  VARCHAR(120)  NOT NULL COMMENT '显示名称',
    `enabled`       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    `role`          VARCHAR(32)   NOT NULL COMMENT '系统角色：ADMIN-管理员, PROJECT_OWNER-项目负责人, PROJECT_MEMBER-项目成员',
    PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账户表';

-- ============================================================
-- 02. 项目
-- ============================================================
DROP TABLE IF EXISTS `projects`;
CREATE TABLE `projects` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code`            VARCHAR(80)  NOT NULL COMMENT '项目编码（唯一标识）',
    `name`            VARCHAR(120) NOT NULL COMMENT '项目名称',
    `description`     VARCHAR(1000)         COMMENT '项目描述',
    `owner_username`  VARCHAR(80)  NOT NULL COMMENT '项目负责人用户名',
    `status`          VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE' COMMENT '项目状态：ACTIVE-活跃, ARCHIVED-已归档',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_projects_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

-- ============================================================
-- 03. 项目成员
-- ============================================================
DROP TABLE IF EXISTS `project_members`;
CREATE TABLE `project_members` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`  BIGINT      NOT NULL COMMENT '所属项目ID',
    `username`    VARCHAR(80) NOT NULL COMMENT '成员用户名',
    `role`        VARCHAR(24) NOT NULL COMMENT '项目角色：OWNER-负责人, MEMBER-成员',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_members_project_user` (`project_id`, `username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员表';

-- ============================================================
-- 04. 脚本版本
-- 说明: .jmx 脚本文件的上传版本记录，实际文件存储在文件系统
-- ============================================================
DROP TABLE IF EXISTS `script_versions`;
CREATE TABLE `script_versions` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`         BIGINT       NOT NULL COMMENT '所属项目ID',
    `version_no`         INT          NOT NULL COMMENT '版本号（项目内自增）',
    `original_filename`  VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `stored_path`        VARCHAR(1000) NOT NULL COMMENT '文件系统存储路径',
    `uploaded_by`        VARCHAR(80)  NOT NULL COMMENT '上传者用户名',
    `uploaded_at`        DATETIME(3)  NOT NULL COMMENT '上传时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本版本表';

-- ============================================================
-- 05. 测试计划
-- 说明: 一个测试计划包含多个测试场景
-- ============================================================
DROP TABLE IF EXISTS `task_plans`;
CREATE TABLE `task_plans` (
    `id`                            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`                    BIGINT       NOT NULL COMMENT '所属项目ID',
    `name`                          VARCHAR(160) NOT NULL COMMENT '计划名称',
    `remark`                        VARCHAR(1000)         COMMENT '备注说明',
    `default_controller_node_id`    BIGINT                COMMENT '默认控制节点ID（关联 execution_nodes）',
    `default_worker_node_ids_json`  LONGTEXT              COMMENT '默认工作节点ID列表（JSON数组）',
    `default_monitor_target_ids_json` LONGTEXT            COMMENT '默认监控目标ID列表（JSON数组）',
    `created_by`                    VARCHAR(80)  NOT NULL COMMENT '创建者用户名',
    `created_at`                    DATETIME(3)  NOT NULL COMMENT '创建时间',
    `updated_at`                    DATETIME(3)  NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试计划表';

-- ============================================================
-- 06. 测试场景
-- 说明: 测试计划下的具体执行单元，配置线程数、持续时间等 JMeter 参数
-- ============================================================
DROP TABLE IF EXISTS `task_scenarios`;
CREATE TABLE `task_scenarios` (
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `plan_id`                  BIGINT       NOT NULL COMMENT '所属测试计划ID',
    `script_version_id`        BIGINT       NOT NULL COMMENT '关联脚本版本ID',
    `name`                     VARCHAR(160) NOT NULL COMMENT '场景名称',
    `sort_order`               INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `threads`                  INT          NOT NULL DEFAULT 1 COMMENT '并发线程数',
    `ramp_up`                  INT          NOT NULL DEFAULT 0 COMMENT '线程启动预热时间（秒）',
    `duration`                 INT          NOT NULL DEFAULT 0 COMMENT '持续时间（秒），0表示不限',
    `loops`                    INT          NOT NULL DEFAULT 1 COMMENT '循环次数，-1表示无限循环',
    `jmeter_properties_json`   LONGTEXT     NOT NULL COMMENT 'JMeter 扩展属性（JSON对象）',
    `controller_node_id`       BIGINT                COMMENT '指定控制节点ID（覆盖计划默认值）',
    `worker_node_ids_json`     LONGTEXT              COMMENT '指定工作节点ID列表（JSON数组，覆盖计划默认值）',
    `monitor_target_ids_json`  LONGTEXT              COMMENT '指定监控目标ID列表（JSON数组，覆盖计划默认值）',
    `created_at`               DATETIME(3)  NOT NULL COMMENT '创建时间',
    `updated_at`               DATETIME(3)  NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试场景表';

-- ============================================================
-- 07. 场景执行记录
-- 说明: 每次执行测试场景的历史记录
-- ============================================================
DROP TABLE IF EXISTS `scenario_executions`;
CREATE TABLE `scenario_executions` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `scenario_id`      BIGINT        NOT NULL COMMENT '关联测试场景ID',
    `config_json`      LONGTEXT      NOT NULL COMMENT '执行时的完整配置快照（JSON）',
    `status`           VARCHAR(24)   NOT NULL DEFAULT 'QUEUED' COMMENT '执行状态：QUEUED-排队, RUNNING-运行中, STOPPING-停止中, SUCCESS-成功, FAILED-失败, CANCELLED-已取消, INTERRUPTED-中断',
    `created_at`       DATETIME(3)   NOT NULL COMMENT '创建时间',
    `start_time`       DATETIME(3)            COMMENT '开始执行时间',
    `end_time`         DATETIME(3)            COMMENT '结束执行时间',
    `duration_ms`      BIGINT                 COMMENT '执行耗时（毫秒）',
    `result_file_path` VARCHAR(1000)          COMMENT 'JTL 结果文件路径',
    `log_file_path`    VARCHAR(1000)          COMMENT 'JMeter 日志文件路径',
    `error_message`    VARCHAR(2000)          COMMENT '错误信息（失败时记录）',
    `execution_name`   VARCHAR(400)           COMMENT '执行名称（用于展示）',
    `exit_code`        INT                    COMMENT 'JMeter 进程退出码',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景执行记录表';

-- ============================================================
-- 08. 执行节点
-- 说明: JMeter 分布式执行节点（Controller/Worker），通过 SSH 远程管理
-- ============================================================
DROP TABLE IF EXISTS `execution_nodes`;
CREATE TABLE `execution_nodes` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`             VARCHAR(120)  NOT NULL COMMENT '节点名称',
    `host`             VARCHAR(160)  NOT NULL COMMENT '主机地址（IP或域名）',
    `ssh_port`         INT           NOT NULL DEFAULT 22 COMMENT 'SSH 端口',
    `ssh_username`     VARCHAR(80)   NOT NULL COMMENT 'SSH 登录用户名',
    `ssh_key_path`     VARCHAR(1000) NOT NULL COMMENT 'SSH 私钥文件路径',
    `role`             VARCHAR(24)   NOT NULL COMMENT '节点角色：CONTROLLER-控制节点, WORKER-工作节点, BOTH-兼有两种角色',
    `status`           VARCHAR(24)   NOT NULL DEFAULT 'UNKNOWN' COMMENT '节点状态：UNKNOWN-未知, AVAILABLE-可用, OFFLINE-离线',
    `remote_work_dir`  VARCHAR(1000) NOT NULL COMMENT '远程工作目录路径',
    `last_checked_at`  DATETIME(3)            COMMENT '最后一次健康检查时间',
    `last_message`     VARCHAR(2000)          COMMENT '最后一次检查的状态消息',
    `created_at`       DATETIME(3)   NOT NULL COMMENT '创建时间',
    `updated_at`       DATETIME(3)   NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行节点表';

-- ============================================================
-- 09. 聚合报告
-- 说明: 每次执行完成后的 JMeter 聚合报告，一个执行ID对应一条记录
-- ============================================================
DROP TABLE IF EXISTS `aggregate_report`;
CREATE TABLE `aggregate_report` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `execution_id`     BIGINT       NOT NULL COMMENT '关联执行记录ID',
    `accuracy`         VARCHAR(16)  NOT NULL COMMENT '报告精度级别',
    `start_ms`         BIGINT                COMMENT '报告起始时间（毫秒时间戳）',
    `end_ms`           BIGINT                COMMENT '报告结束时间（毫秒时间戳）',
    `duration_seconds` DOUBLE                COMMENT '报告覆盖时长（秒）',
    `summary_json`     LONGTEXT     NOT NULL COMMENT '汇总统计数据（JSON）',
    `rows_json`        LONGTEXT     NOT NULL COMMENT '逐标签明细数据（JSON）',
    `snapshot_blob`    LONGBLOB              COMMENT '报告图表快照（PNG二进制数据）',
    `generated_at`     DATETIME(3)  NOT NULL COMMENT '报告生成时间',
    `builder_version`  VARCHAR(32)           COMMENT '报告构建器版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_aggregate_report_execution` (`execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聚合报告表';

-- ============================================================
-- 10. 执行时序指标
-- 说明: 按时间桶聚合的 JMeter 时序性能指标，用于绘制趋势图
-- ============================================================
DROP TABLE IF EXISTS `execution_metric_series`;
CREATE TABLE `execution_metric_series` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `execution_id`    BIGINT       NOT NULL COMMENT '关联执行记录ID',
    `bucket_time_ms`  BIGINT       NOT NULL COMMENT '时间桶起始（毫秒时间戳）',
    `label`           VARCHAR(256) NOT NULL COMMENT '采样器标签名称',
    `samples`         BIGINT       NOT NULL COMMENT '该桶内样本总数',
    `error_samples`   BIGINT       NOT NULL COMMENT '该桶内错误样本数',
    `throughput`      DOUBLE       NOT NULL COMMENT '该桶内吞吐量（请求/秒）',
    `avg_rt_ms`       BIGINT       NOT NULL COMMENT '该桶内平均响应时间（毫秒）',
    `p95_rt_ms`       BIGINT       NOT NULL COMMENT '该桶内P95响应时间（毫秒）',
    PRIMARY KEY (`id`),
    INDEX `idx_metric_series_exec_label_time` (`execution_id`, `label`, `bucket_time_ms`),
    INDEX `idx_metric_series_exec_time` (`execution_id`, `bucket_time_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行时序指标表';

-- ============================================================
-- 11. 监控目标
-- 说明: Prometheus/JMX 监控目标配置，支持 SSH 隧道连接
-- ============================================================
DROP TABLE IF EXISTS `monitor_target`;
CREATE TABLE `monitor_target` (
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`          BIGINT        NOT NULL COMMENT '所属项目ID',
    `type`                VARCHAR(40)   NOT NULL COMMENT '监控目标类型：SERVER',
    `name`                VARCHAR(120)  NOT NULL COMMENT '目标名称',
    `service_name`        VARCHAR(120)  NOT NULL COMMENT '服务名称',
    `host`                VARCHAR(160)  NOT NULL COMMENT '主机地址（IP或域名）',
    `ssh_username`        VARCHAR(80)            COMMENT 'SSH 登录用户名（SSH隧道场景）',
    `ssh_password`        VARCHAR(200)           COMMENT 'SSH 登录密码（SSH隧道场景）',
    `ssh_port`            INT                    COMMENT 'SSH 端口（SSH隧道场景，默认22）',
    `plugin_dir`          VARCHAR(500)           COMMENT 'JMX插件目录路径',
    `port`                INT           NOT NULL COMMENT 'Metrics 端口',
    `metrics_path`        VARCHAR(120)  NOT NULL COMMENT 'Metrics 路径（如 /metrics）',
    `env`                 VARCHAR(40)   NOT NULL COMMENT '环境标识（如 dev/test/prod）',
    `labels_json`         LONGTEXT               COMMENT '附加标签（JSON对象）',
    `items_json`          LONGTEXT               COMMENT '监控项配置（JSON数组）',
    `enabled`             TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    `last_check_status`   VARCHAR(24)   NOT NULL DEFAULT 'UNKNOWN' COMMENT '最后检查状态：UNKNOWN-未知, SUCCESS-成功, FAILED-失败',
    `last_check_message`  VARCHAR(1000)          COMMENT '最后检查状态消息',
    `last_checked_at`     DATETIME(3)            COMMENT '最后检查时间',
    `created_at`          DATETIME(3)   NOT NULL COMMENT '创建时间',
    `updated_at`          DATETIME(3)   NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控目标表';

-- ============================================================
-- 12. 执行监控绑定
-- 说明: 记录每次执行关联了哪些监控目标
-- ============================================================
DROP TABLE IF EXISTS `execution_monitor_binding`;
CREATE TABLE `execution_monitor_binding` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `execution_id`  BIGINT      NOT NULL COMMENT '关联执行记录ID',
    `target_id`     BIGINT      NOT NULL COMMENT '关联监控目标ID',
    `start_time`    DATETIME(3)          COMMENT '监控开始时间',
    `end_time`      DATETIME(3)          COMMENT '监控结束时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行监控绑定表';

-- ============================================================
-- 13. 目标指标快照
-- 说明: 执行期间采集的监控指标时序数据，按执行+指标类型去重
-- ============================================================
DROP TABLE IF EXISTS `execution_target_metrics_snapshot`;
CREATE TABLE `execution_target_metrics_snapshot` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `execution_id`  BIGINT       NOT NULL COMMENT '关联执行记录ID',
    `kind`          VARCHAR(32)  NOT NULL COMMENT '指标类型（如 cpu/memory/network）',
    `unit`          VARCHAR(32)           COMMENT '指标单位（如 %/MB/bytes）',
    `series_json`   LONGTEXT     NOT NULL COMMENT '时序数据点（JSON数组）',
    `captured_at`   DATETIME(3)  NOT NULL COMMENT '数据采集时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_metrics_snapshot_exec_kind` (`execution_id`, `kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='目标指标快照表';

-- ============================================================
-- 种子数据（与 PlatformServiceConfiguration#demoUserSeeder 保持一致）
-- 说明: 开发环境默认账户，生产环境请删除或修改密码
-- ============================================================

-- 管理员账户（密码: admin123）
INSERT INTO `user_accounts` (`username`, `password`, `display_name`, `enabled`, `role`)
VALUES ('admin', 'admin123', '平台管理员', 1, 'ADMIN')
ON DUPLICATE KEY UPDATE `username` = `username`;

-- 测试工程师账户（密码: tester123）
INSERT INTO `user_accounts` (`username`, `password`, `display_name`, `enabled`, `role`)
VALUES ('tester', 'tester123', '性能测试工程师', 1, 'PROJECT_MEMBER')
ON DUPLICATE KEY UPDATE `username` = `username`;
