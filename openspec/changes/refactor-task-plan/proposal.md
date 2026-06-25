## Why

当前任务模块将「任务计划」「测试场景」「执行记录」混为单层 Task，创建即执行，且 API 只暴露最新一次执行结果，无法满足多场景压测计划、同场景多次试跑与历史记录对比的实际使用方式。

## What Changes

- 引入三层模型：TaskPlan（任务计划）→ TaskScenario（场景）→ ScenarioExecution（执行记录）
- 计划级共享默认 controller/workers/monitorTargets，场景可覆盖
- 仅支持按场景触发执行，不支持整计划一键执行
- 执行记录全量保留，支持查看任意历史记录与手动删除
- 执行中支持主动停止（STOPPING → INTERRUPTED）
- 前端导航：计划列表 → 计划详情（场景列表）→ 场景详情（执行历史）→ 执行详情（原任务详情页）
- **BREAKING**：删除 `test_tasks` / `task_executions` 表及 `/api/tasks` 相关接口

## Capabilities

### New Capabilities
- `task-plan-model`: 计划/场景/执行三层数据模型与配置继承
- `task-plan-api`: 新 REST API 契约
- `scenario-execution-control`: 执行中断与单条记录删除

### Modified Capabilities
（无）

## Impact

- 后端：`execution` 包任务相关实体与服务迁移至 `task` 包；`TaskController` 拆分为三个 Controller
- 前端：任务相关页面、API、路由全面替换
- 数据库：重建 `task_plans` / `task_scenarios` / `scenario_executions`
- 报告生成：预留，本次不实现
