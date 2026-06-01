# Git、日志与 AI 分析模块

## 1. 模块定位

该模块用于把压测结果与代码变更、运行日志和 AI 分析关联起来，从执行报告平台演进为性能问题辅助定位平台。该模块属于远期扩展。

## 2. 职责边界

| 负责 | 不负责 |
|------|--------|
| Git 仓库配置和提交记录查看 | 代码托管平台权限治理 |
| 测试任务关联分支和提交号 | 自动修复业务代码 |
| 日志上传、检索、错误摘要 | 全量日志平台替代 |
| AI 模型配置和分析记录 | 无依据自动下结论 |

## 3. 功能范围

1. Git 仓库配置、代码拉取、提交记录查看。
2. 测试任务关联代码分支、提交号、版本备注。
3. 日志上传、索引、检索和错误摘要。
4. AI 模型配置、Prompt 模板和分析任务。
5. 基于报告、监控、日志和代码变更生成优化建议。

## 4. 关键实体

| 实体 | 关键字段 | 说明 |
|------|----------|------|
| `git_repository` | `id`, `project_id`, `name`, `url`, `auth_type`, `status` | Git 仓库 |
| `git_commit_snapshot` | `id`, `repository_id`, `branch`, `commit_id`, `message`, `author_time` | 提交快照 |
| `task_code_binding` | `task_id`, `repository_id`, `branch`, `commit_id`, `remark` | 任务代码关联 |
| `log_artifact` | `id`, `execution_id`, `file_name`, `file_path`, `index_status` | 日志文件 |
| `ai_analysis_job` | `id`, `report_id`, `model`, `prompt_version`, `status`, `result` | AI 分析任务 |

## 5. 接口草案

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/projects/{projectId}/git-repositories` | Git 仓库列表 |
| `POST` | `/api/projects/{projectId}/git-repositories` | 配置 Git 仓库 |
| `GET` | `/api/git-repositories/{repositoryId}/commits` | 查询提交记录 |
| `PUT` | `/api/tasks/{taskId}/code-binding` | 绑定任务代码版本 |
| `POST` | `/api/executions/{executionId}/logs` | 上传日志 |
| `GET` | `/api/executions/{executionId}/logs/search` | 检索日志 |
| `POST` | `/api/reports/{reportId}/ai-analysis` | 创建 AI 分析任务 |

## 6. 详细设计调整点

1. Git 客户端默认用 JGit，后续保留 GitHub/GitLab API 适配器。
2. 日志检索先采用 Lucene 或本地索引，数据量增长后再考虑 Elasticsearch。
3. AI 分析必须保留输入数据、模型、Prompt 版本和输出结果，便于追溯。
4. 发送到 AI 前需要脱敏或显式确认，日志和代码片段不能默认全量外发。
5. AI 输出只作为辅助建议，报告中的人工结论仍由用户确认。
