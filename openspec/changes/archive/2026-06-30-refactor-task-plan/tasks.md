## 1. OpenSpec & Backend Domain

- [x] 1.1 创建 task 包实体：PersistentTaskPlanRecord、PersistentTaskScenarioRecord、PersistentScenarioExecutionRecord 及 Repository
- [x] 1.2 创建领域 record：TaskPlan、TaskScenario、ScenarioExecution
- [x] 1.3 新增 ExecutionConfigMerger、ScenarioExecutionRuntime

## 2. Backend Services

- [x] 2.1 实现 TaskPlanService（CRUD）
- [x] 2.2 实现 TaskScenarioService（CRUD + 配置覆盖）
- [x] 2.3 实现 ScenarioExecutionService（触发执行、列表、详情、停止、删除、结果查询）

## 3. Backend API & Runner

- [x] 3.1 新增 TaskPlanController、TaskScenarioController、ScenarioExecutionController
- [x] 3.2 改写 DistributedJmeterExecutionRunner 支持取消与 INTERRUPTED
- [x] 3.3 改写 ExecutionMonitorBindingService、TargetMetricsService 按 executionId 查询
- [x] 3.4 删除旧 TaskController、TestExecutionService、PersistentTestTask* 等

## 4. Backend Tests

- [x] 4.1 ExecutionConfigMerger 单测
- [x] 4.2 ScenarioExecutionRuntime 单测

## 5. Frontend

- [x] 5.1 更新 types、api（task-plans）
- [x] 5.2 新增 composables 与 task-plans 组件
- [x] 5.3 更新路由与 ProjectDetail 导航
- [x] 5.4 删除旧 tasks 相关文件

## 6. Verification

- [x] 6.1 gradle :backend:test
- [x] 6.2 npm run build

## ToDo（后续）

- [ ] 报告模板完成后：支持按计划选择各场景任意执行记录生成报告
