## Context

现有 `test_tasks` 一对一绑定脚本，`task_executions` 虽支持多条记录但 API 仅取最新。用户需要任务计划包含多个场景，每场景可多次执行并保留历史。

## Goals / Non-Goals

**Goals:**
- 三层模型与配置继承（计划默认 + 场景覆盖 → 执行快照）
- 场景级执行、历史记录管理、执行中断
- 前端四层导航

**Non-Goals:**
- 报告模板与按记录生成报告
- 计划/场景权限、模板复制

## Decisions

### 1. 数据模型

| 表 | 职责 |
|---|---|
| `task_plans` | 计划元数据 + 默认 controller/workers/monitorTargets |
| `task_scenarios` | 场景名、脚本版本、执行参数、可选覆盖字段 |
| `scenario_executions` | 每次执行的 config 快照、状态、结果路径 |

执行触发时将合并后的 `ExecutionConfig` 写入 `scenario_executions.config_json`，后续查询与运行均用快照。

### 2. 配置继承

场景字段 `controllerNodeId` / `workerNodeIds` / `monitorTargetIds` 为 null 或空时继承计划默认值。执行参数（threads 等）存于场景，每次执行沿用场景当前值并快照。

### 3. 停止执行

`ScenarioExecutionRuntime` 维护 `ConcurrentHashMap<Long, AtomicBoolean>`。`stop()` 将状态 CAS 为 STOPPING 并置取消标志。`DistributedJmeterExecutionRunner.waitForCompletion` 轮询检测后调用已有 `stopRun`，最终标记 INTERRUPTED。

### 4. API 路径

- `/api/projects/{projectId}/task-plans`
- `/api/task-plans/{planId}/scenarios`
- `/api/scenarios/{scenarioId}/executions`
- `/api/executions/{executionId}/*`

### 5. 迁移策略

彻底重建，不迁移旧 `test_tasks` 数据（H2 ddl-auto=update，旧表实体删除后由 Hibernate 处理）。

## Risks / Trade-offs

- **BREAKING API** → 前后端同步发布
- **旧数据丢失** → 开发/测试环境可接受，生产需提前备份

## Open Questions

- 报告模块完成后，在计划层增加「每场景选定 executionId」字段
