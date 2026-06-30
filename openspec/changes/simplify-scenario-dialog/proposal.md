## Why

当前场景(Scenario)创建/编辑弹窗中存在线程数、Ramp-Up、持续时间、循环次数等执行参数配置，然而脚本(脚本编辑器中的 ThreadGroup)已经包含完的线程配置。这造成了双重数据源：执行时场景级别的线程参数通过 `-J` 属性覆盖脚本中配置，导致用户困惑——修改线程配置需要在两个地方操作。此外，弹窗中的脚本选择列表缺少直达脚本编辑器的入口，用户需要先关闭弹窗再去脚本页面操作，流程割裂。

## What Changes

- 从场景创建/编辑弹窗中移除线程数、Ramp-Up、持续时间、循环次数四个表单字段
- 场景弹窗中的脚本选择列表增加编辑按钮，点击在新标签页打开对应的脚本编辑器
- 后端场景 API 移除线程相关参数，执行时不再通过 `-J` 属性覆盖脚本中的 ThreadGroup 配置
- **BREAKING**: POST/PUT `/api/task-plans/{planId}/scenarios` 和 PUT `/api/scenarios/{scenarioId}` 不再接受 threads/rampUp/duration/loops 字段

## Capabilities

### New Capabilities
- `scenario-dialog`: 场景创建和编辑弹窗，提供脚本选择与执行节点/监控配置，不再包含线程等执行参数

### Modified Capabilities
- `task-plan-api`: 场景创建和更新 API 移除 threads/rampUp/duration/loops 参数
- `task-plan-model`: 场景模型不再承载线程相关执行参数，线程配置以脚本中的 ThreadGroup 为唯一数据源

## Impact

- 前端：`ScenarioDialog.vue`、`ScenarioDetail.vue`、`api/task-plans.ts`、`types/index.ts`、`useTaskPlans.ts`
- 后端：`TaskPlanController.java` (CreateScenarioRequest/UpdateScenarioRequest)、`TaskScenarioService.java`、`PersistentTaskScenarioRecord.java`、`ExecutionConfigMerger.java`、`JmeterCommandExecutor.java`、`ScenarioExecutionService.java`
- 数据库：`task_scenarios` 表的 threads/rampUp/duration/loops 列保留但不再写入新值（向后兼容）
