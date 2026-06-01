# 辅助脚本模块

## 1. 模块定位

辅助脚本用于在压测前后执行环境准备、数据清理、服务检查等自动化动作。该模块属于 Phase 2 增强，只有在核心执行闭环稳定后再接入。

## 2. 职责边界

| 负责 | 不负责 |
|------|--------|
| 前置/后置脚本管理 | JMeter 脚本版本管理 |
| 脚本执行日志和退出码记录 | 压测结果指标计算 |
| 失败策略配置 | 远程主机资产治理 |
| 任务与辅助脚本绑定 | 通用 CI/CD 流水线 |

## 3. 功能范围

1. Shell 或 Python 辅助脚本管理。
2. 测试任务配置前置脚本和后置脚本。
3. 支持失败策略：停止任务、继续任务、人工确认后继续。
4. 记录脚本执行日志、退出码、开始时间、结束时间。
5. 支持项目私有脚本和系统公共脚本。

## 4. 关键实体

| 实体 | 关键字段 | 说明 |
|------|----------|------|
| `aux_script` | `id`, `project_id`, `name`, `type`, `scope`, `status` | 辅助脚本 |
| `aux_script_version` | `id`, `script_id`, `version_no`, `source_code`, `remark` | 脚本版本 |
| `task_aux_script_binding` | `task_id`, `script_version_id`, `phase`, `failure_policy` | 任务绑定 |
| `aux_script_execution` | `id`, `execution_id`, `script_version_id`, `status`, `exit_code`, `log_path` | 脚本执行记录 |

## 5. 接口草案

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/projects/{projectId}/aux-scripts` | 辅助脚本列表 |
| `POST` | `/api/projects/{projectId}/aux-scripts` | 创建辅助脚本 |
| `POST` | `/api/aux-scripts/{scriptId}/versions` | 新增脚本版本 |
| `PUT` | `/api/tasks/{taskId}/aux-scripts` | 配置任务前后置脚本 |
| `GET` | `/api/executions/{executionId}/aux-script-logs` | 查看辅助脚本日志 |

## 6. 详细设计调整点

1. 辅助脚本执行权限必须严格限制，不允许任意读取平台敏感目录。
2. 脚本执行应有超时配置，超时后记录失败并按失败策略处理任务。
3. 远程执行能力后续通过 SSH 适配器提供，MVP 不提前引入。
4. 脚本版本不可变，任务绑定的是版本号而不是可变脚本主记录。
5. 后置脚本默认即使压测失败也可执行，用于清理环境，但必须可配置。
