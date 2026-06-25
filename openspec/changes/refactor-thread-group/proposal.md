## Why

脚本编辑器中的线程组模块存在架构腐化：线程组配置（线程数、Ramp-Up、循环次数、持续时间）在后端使用无类型的 `Map<String, Object>` 存储，前端则通过字符串字面量 `"THREAD_GROUP"` 做类型判断，且 `threadGroups[]` 作为冗余派生数据与步骤树并行存在。这导致类型不安全、数据不一致风险，以及新增线程组类型（如 ConcurrencyThreadGroup）时扩展困难。随着脚本编辑功能持续演进，现在重构可以为后续扩展奠定基础。

## What Changes

- 引入后端线程组专用模型 `ThreadGroupConfig`，替代 `Map<String, Object>` 的无类型配置，提供 `threads`、`rampUp`、`loops`、`duration` 的强类型访问
- 引入步骤类型枚举 `ScriptStepType`，消除散落在 Parser/Renderer/Service 中的字符串字面量比较
- 消除前端 `ScriptAsset.threadGroups[]` 冗余字段，统一从 `steps` 实时派生，避免编辑后数据不同步
- 重构 `TaskConfigDialog`，支持从脚本中选择任意线程组（而非硬编码取 `threadGroups[0]`），为多线程组场景做好准备
- 将线程组相关的 UI 逻辑（编辑面板、步骤树描述、摘要展示）抽取为独立组件/hook，降低 `StepDetail.vue`、`StepSidebar.vue` 的复杂度

## Capabilities

### New Capabilities
- `thread-group-model`: 后端线程组强类型模型与步骤类型枚举，替代无类型的 Map 和字符串比较
- `thread-group-ui`: 前端线程组 UI 重构，消除冗余数据，抽取独立组件，支持多线程组选择

### Modified Capabilities

（无现有 spec 需要修改）

## Impact

- **后端代码**：`ScriptStepDefinition`、`JmeterScriptParser`、`JmeterScriptRenderer`、`ScriptService`、`ExecutionConfig` 需要适配新模型
- **前端代码**：`types/index.ts`、`api/scripts.ts`、`utils/script-steps.ts`、`composables/useScriptEditor.ts`、`components/editor/StepDetail.vue`、`components/editor/StepSidebar.vue`、`components/tasks/TaskConfigDialog.vue`、`components/scripts/ScriptWorkspace.vue`
- **API 兼容性**：REST API 的 JSON 结构不变（`config` 字段仍为 JSON 对象），但后端内部类型从 Map 变为强类型 record，对前端透明
- **依赖**：无新增外部依赖
- **测试**：需要更新 Parser/Renderer 单元测试以验证新模型，前端需要验证步骤编辑和任务配置的正确性
