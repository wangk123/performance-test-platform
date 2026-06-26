## 1. Backend — 强类型模型

- [x] 1.1 创建 `ScriptStepType` 枚举，包含 THREAD_GROUP、HTTP_REQUEST、CSV_DATA_SET、HTTP_HEADER_MANAGER、HTTP_COOKIE_MANAGER、RESPONSE_ASSERTION、JSR223_PRE_PROCESSOR、JSR223_POST_PROCESSOR，每个枚举值携带 `code`（String）和 `displayName`（String）属性
- [x] 1.2 在 `ScriptStepDefinition` 中新增 `stepType()` 方法，将 String type 转换为 `ScriptStepType` 枚举值
- [x] 1.3 创建 `ThreadGroupConfig` record（int threads, int rampUp, int loops, int duration），包含 `fromMap(Map)` 和 `toMap()` 转换方法，以及 `DEFAULT` 常量（threads=1, rampUp=0, loops=1, duration=0）
- [x] 1.4 在 `ScriptStepDefinition` 中新增 `threadGroupConfig()` 方法，当 type 为 THREAD_GROUP 时返回 `ThreadGroupConfig`，否则抛出异常

## 2. Backend — 适配 Parser/Renderer/Service

- [x] 2.1 重构 `JmeterScriptParser.parseThreadGroup()`：使用 `ThreadGroupConfig.fromMap()` 构建配置，使用 `ScriptStepType.THREAD_GROUP.code()` 替代字符串字面量
- [x] 2.2 重构 `JmeterScriptRenderer.appendThreadGroup()`：通过 `step.threadGroupConfig()` 获取配置，使用枚举比较替代字符串比较
- [x] 2.3 检查并更新 `ScriptService` 和其他引用线程组类型字符串的后端代码，统一使用 `ScriptStepType` 枚举
- [x] 2.4 更新 `ExecutionConfig` 中对线程组参数的引用，确保从 `ThreadGroupConfig` 获取值

## 3. Backend — 测试验证

- [x] 3.1 编写 `ThreadGroupConfig` 单元测试：验证 fromMap/toMap 往返转换、默认值、边界值
- [x] 3.2 编写 `ScriptStepType` 单元测试：验证枚举值与字符串的对应关系、stepType() 方法
- [x] 3.3 更新 `JmeterScriptParser` 测试：验证解析后步骤的 threadGroupConfig() 返回正确值
- [x] 3.4 更新 `JmeterScriptRenderer` 测试：验证渲染输出与重构前一致（往返测试）

## 4. Frontend — useThreadGroups composable

- [x] 4.1 创建 `composables/useThreadGroups.ts`，接收 `steps` 参数，返回 computed 的 `threadGroups` 数组和 `threadGroupCount`
- [x] 4.2 编写 `useThreadGroups` 的单元测试或手动验证：无线程组、单线程组、多线程组场景

## 5. Frontend — 独立组件

- [x] 5.1 创建 `ThreadGroupEditor.vue`：接收 `config` prop（threads/rampUp/loops/duration），emit `update:config` 事件，包含 4 个字段的 el-input-number 和标签
- [x] 5.2 创建 `ThreadGroupSummary.vue`：接收 `threadGroup` prop，渲染简洁描述文本（如 "100 线程 · Ramp 60s · 600s"）
- [x] 5.3 在 `ThreadGroupEditor.vue` 中添加字段校验：threads > 0，rampUp >= 0，loops >= 0，duration >= 0

## 6. Frontend — 适配现有组件

- [x] 6.1 重构 `StepDetail.vue`：THREAD_GROUP 类型时使用 `ThreadGroupEditor` 组件替代内联字段
- [x] 6.2 重构 `StepSidebar.vue`：THREAD_GROUP 步骤的描述文本改用 `ThreadGroupSummary` 组件
- [x] 6.3 重构 `ScriptWorkspace.vue`：线程组摘要改用 `useThreadGroups` composable 获取数据
- [x] 6.4 重构 `api/scripts.ts` 的 `mapScriptDefinition()`：移除 threadGroups 字段的生成逻辑
- [x] 6.5 重构 `types/index.ts`：从 `ScriptAsset` 接口中移除 `threadGroups` 字段
- [x] 6.6 更新 `utils/script-steps.ts`、`utils/jmeter.ts`、`utils/seed.ts`、`utils/task-mock.ts`：移除对 threadGroups 的直接引用，改用 steps 派生

## 7. Frontend — 任务配置多线程组选择

- [x] 7.1 重构 `TaskConfigDialog.vue`：新增线程组下拉选择器（el-select），选项来自 `useThreadGroups(steps).threadGroups`
- [x] 7.2 实现选择线程组后自动填充 threads/rampUp/duration/loops 字段
- [x] 7.3 处理无线程组场景：禁用执行参数区域，阻止提交

## 8. 端到端验证

- [x] 8.1 运行后端测试：`./gradlew :backend:test`，确认所有测试通过
- [x] 8.2 前端编译检查：`cd frontend && npm run build`，确认无类型错误
- [ ] 8.3 手动测试完整流程：上传 JMX → 解析步骤 → 编辑线程组参数 → 保存 → 创建任务 → 选择线程组 → 执行
