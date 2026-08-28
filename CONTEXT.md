# CONTEXT.md — 性能测试平台领域词汇与模块地图

> 用途：给架构评审、AI 导航与新人提供统一的领域语言。变更领域概念时优先更新本文件。

## 领域概念

- **Project（项目）**：平台资产归属入口。脚本、计划、场景、执行、报告、监控目标、造数资产都挂靠项目；归档不物理删除。
- **Script / ScriptVersion（脚本/脚本版本）**：JMeter JMX 可执行资产；版本不可变，`storage/scripts/{projectId}/` 存储。`ScriptDefinition` 是解析后的步骤树视图。
- **TaskPlan（测试计划）**：场景容器，含默认控制器/工作节点/监控目标。
- **TaskScenario（测试场景）**：绑定脚本版本 + 线程组参数（预设行）的执行配置。
- **ScenarioExecution（执行记录）**：一次压测运行；配置 JSON 快照固化；状态机 QUEUED → RUNNING → SUCCESS/FAILED/INTERRUPTED，RUNNING → STOPPING → CANCELLED。
- **ThreadGroupConfig / Preset（线程组配置/预设）**：场景内可复用的线程组参数行（threads/rampUp/duration/sortOrder）。
- **ExecutionNode（执行节点）**：SSH 远程节点，角色 CONTROLLER/WORKER/BOTH；远端 Docker 容器跑 JMeter。
- **MonitorTarget（监控目标）**：被测服务器，Prometheus 资源指标通道 + SSH 一键部署 Exporter。
- **Seed（造数工厂）**：数据源 → 采集策略 → 快照样本 → 相邻 Diff 分析 → 模板确认 → 克隆写库。
- **Agent 面 / Facade**：`/api/agent/**` 机器入口，唯一业务入口是 facade 层；人类 UI 面走 `/api/**`。
- **确定性分析（Analysis）**：算法产出的带版本事实（趋势/异常/错误聚类/资源饱和/执行对比），不做 LLM 根因。
- **证据链（Evidence）**：以 CorrelationKey（executionId+时间窗+目标实例+标签+traceId）跨源对齐执行/聚合/秒级指标/失败样本/Prometheus。

## 后端模块地图（2025-08 重构后）

| 模块 | 职责 | 备注 |
|---|---|---|
| `task/ExecutionControlService` | 执行控制唯一 seam：start/stop/cancel + 幂等 + 审计 + 预检 | UI 与 agent 面共用（C1） |
| `task/ExecutionQueryService` | 执行读模型：结果/采样/失败样本/指标/实时流 | 从 ScenarioExecutionService 拆出（C2） |
| `task/ScenarioExecutionService` | 执行写模型：触发/停止/删除 | 仅被控制模块与控制器调用（C2） |
| `execution/distributed/ExecutionScriptAssembler` | 执行脚本装配：装载→线程组预设补丁→监听器注入→写出 | 从 Runner 拆出（C4） |
| `execution/distributed/DistributedJmeterExecutionRunner` | 远程编排：节点校验、payload、远程启停、结果回收 | 不再含脚本加工（C4） |
| `script/JmeterScriptParser·Renderer·Patcher` | JMX 解析/渲染/补丁，各自为深模块 | 编排语义在 Assembler 一处 |
| `facade/FacadeGuard` | agent 面主体校验 | 审计在控制模块内（C5） |
| `governance` | 脱敏/限流/请求审计/执行审计 | AuditFacade 走有界查询（C5） |

## 关键决策记录（非正式 ADR）

- 执行启停的幂等、审计、冲突语义只在 `ExecutionControlService` 一处实现；任何新入口（UI/agent/MCP）必须走这条 seam。
- 脚本装配（预设补丁、监听器注入）只允许在 `ExecutionScriptAssembler` 一处发生。
- 能力探测（如步进线程组）以实际执行环境（注入容器的 `jmeter-runtime/*.jar`）为准，不探测平台本机 JMeter。
- 造数工厂行级数据保持 schema-less Map（表结构天然动态），顶层操作结果用类型化记录。
