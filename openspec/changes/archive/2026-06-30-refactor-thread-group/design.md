## Context

当前脚本编辑器的线程组模块存在以下问题：

1. **后端类型不安全**：`ScriptStepDefinition` 使用 `String type` 和 `Map<String, Object> config`，线程组的 `threads`、`rampUp`、`loops`、`duration` 四个参数通过字符串键访问，无编译期检查
2. **前端数据冗余**：`ScriptAsset.threadGroups[]` 从 `steps` 派生后独立存储，编辑器修改 config 后不会自动同步，任务配置只取 `threadGroups[0]`
3. **逻辑分散**：线程组相关判断（类型检查、拖拽约束、UI 渲染）散落在 `useScriptEditor`、`StepDetail`、`StepSidebar`、`script-steps.ts` 等多处，使用字符串字面量 `"THREAD_GROUP"` 硬编码

项目已有基础：步骤树结构（`ScriptStepDefinition`）作为权威数据源，REST API 通过 JSON 传递步骤定义，JMeter 执行通过 `-J` 参数覆盖线程组参数。

## Goals / Non-Goals

**Goals:**
- 后端线程组配置使用强类型 record，消除 Map 的无类型访问
- 引入步骤类型枚举，替换所有字符串字面量比较
- 前端消除 `threadGroups[]` 冗余字段，统一从 `steps` 实时派生
- 任务配置支持选择任意线程组，而非硬编码取第一个
- 线程组 UI 逻辑抽取为独立组件/hook

**Non-Goals:**
- 不支持新增线程组类型（如 ConcurrencyThreadGroup）— 本次只做重构，不做功能扩展，但架构应为未来扩展留空间
- 不改变 REST API 的 JSON 结构 — 保持前后端接口兼容
- 不重构 JMeter 执行引擎的参数传递机制
- 不改变脚本版本管理和文件存储机制

## Decisions

### 决策 1：后端使用 record 定义 ThreadGroupConfig

**选择**：创建 `ThreadGroupConfig` record，包含 `int threads, int rampUp, int loops, int duration`，在 `ScriptStepDefinition` 中为 THREAD_GROUP 类型步骤使用此 record 替代 `Map<String, Object>`。

**理由**：
- record 是不可变的、类型安全的，编译期即可发现字段名拼写错误
- 与现有 `ScriptStepDefinition` record 风格一致
- JSON 序列化时仍输出为对象，前端无需修改

**备选方案**：
- 继续使用 Map + 常量键名 → 仍无类型安全，不采纳
- 使用泛型 `ScriptStepDefinition<T>` → 改动过大，Jackson 反序列化复杂，不采纳

**实现方式**：
- `ScriptStepDefinition` 的 `config` 字段保持 `Map<String, Object>` 以兼容 JSON 序列化
- 新增 `ThreadGroupConfig` record 和 `toThreadGroupConfig()` / `fromThreadGroupConfig()` 转换方法
- Parser 解析时直接构造 `ThreadGroupConfig`，Renderer 渲染时通过转换方法获取配置
- 服务层通过 `step.threadGroupConfig()` 方法安全获取线程组配置

### 决策 2：后端引入 ScriptStepType 枚举

**选择**：创建 `ScriptStepType` 枚举，包含 `THREAD_GROUP, HTTP_REQUEST, CSV_DATA_SET, HTTP_HEADER_MANAGER, HTTP_COOKIE_MANAGER, RESPONSE_ASSERTION, JSR223_PRE_PROCESSOR, JSR223_POST_PROCESSOR`，`ScriptStepDefinition.type` 仍为 `String`（JSON 兼容），但通过 `stepType()` 方法返回枚举值。

**理由**：
- 保持 JSON 序列化兼容（String → String）
- 服务层和 Parser/Renderer 使用枚举比较，避免拼写错误
- 枚举可携带元数据（如 `displayName`、`isRootOnly`）

### 决策 3：前端消除 threadGroups 冗余字段

**选择**：从 `ScriptAsset` 中移除 `threadGroups` 字段，所有需要线程组信息的地方通过 composable `useThreadGroups(steps)` 从 `steps` 实时计算。

**理由**：
- 单一数据源原则：`steps` 是权威数据，`threadGroups` 是其视图
- 消除同步问题的根源
- computed 响应式确保编辑器修改 config 后自动更新

**备选方案**：
- 保留 threadGroups 但在编辑时同步更新 → 增加复杂度，容易遗漏，不采纳

### 决策 4：任务配置支持多线程组选择

**选择**：`TaskConfigDialog` 新增线程组下拉选择器，从脚本的所有线程组中选择。选定后预填 `threads`、`rampUp`、`duration`、`loops`。若无线程组则禁用执行。

**理由**：
- 当前硬编码 `threadGroups[0]` 无法处理多线程组脚本
- 下拉选择简单直观，无需复杂 UI

### 决策 5：抽取 ThreadGroupEditor 组件

**选择**：将 `StepDetail.vue` 中 THREAD_GROUP 的 4 个字段编辑 UI 抽取为独立的 `ThreadGroupEditor.vue` 组件，将 `StepSidebar.vue` 中线程组的描述文本抽取为 `ThreadGroupSummary.vue`。

**理由**：
- 降低 StepDetail 和 StepSidebar 的复杂度
- 线程组编辑逻辑（字段校验、默认值）集中管理
- 便于未来扩展新的线程组类型时复用

## Risks / Trade-offs

- **[风险] Jackson 序列化兼容性** → `ThreadGroupConfig` record 需要确认 Jackson 能正确序列化/反序列化为 `Map<String, Object>` 格式。缓解：在 Parser 单元测试中覆盖往返序列化。
- **[风险] 前端移除 threadGroups 影响面广** → 多个组件和 API 层引用了 `threadGroups`。缓解：先用 grep 确认所有引用点，创建 `useThreadGroups` composable 后逐一替换，确保编译通过。
- **[取舍] config 保持 Map + 转换方法** → 不是完全的类型安全（config 仍是 Map），但平衡了兼容性和安全性。完全泛型化改动过大，留作后续优化。
- **[取舍] 枚举值仍通过 String 传输** → JSON 协议不变，前端无感知，但后端内部获得类型安全。
