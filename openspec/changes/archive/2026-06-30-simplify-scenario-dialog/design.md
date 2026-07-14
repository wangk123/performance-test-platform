## Context

当前场景(Scenario)模型携带 threads/rampUp/duration/loops 四个执行参数。创建/编辑场景弹窗(ScenarioDialog.vue)中包含这四个字段的表单输入，执行时通过 `ExecutionConfigMerger` 从场景记录读取这些值，再由 `JmeterCommandExecutor.jmeterProperties()` 将其转为 `-Jthreads`、`-Jloops`、`-Jduration`、`-JrampUp` 四个 JMeter 属性，覆盖脚本 JMX 中原有的 ThreadGroup 配置。

这造成线程配置的"双重存在"：脚本编辑器中 ThreadGroup 配一次，场景弹窗中再配一次。从业务语义看，场景只是对脚本的引用+执行器/监控绑定，不应承载线程配置——修改线程应直接编辑脚本。

## Goals / Non-Goals

**Goals:**
- 场景创建/编辑弹窗中移除线程数、Ramp-Up、持续时间、循环次数表单字段
- 脚本选择列表中每个脚本增加编辑按钮，新标签页打开脚本编辑器
- 后端场景 API 移除 threads/rampUp/duration/loops 参数
- 执行时不再通过 `-J` 属性覆盖 JMX 中的 ThreadGroup 配置

**Non-Goals:**
- 不修改 `ScriptParamDrawer.vue`（脚本级别默认执行参数）
- 不删除 `task_scenarios` 表中已有的数据库列（向后兼容）
- 不修改 `ExecutionConfig` 记录的结构（保留字段但默认值为 0）
- 不改变脚本编辑器中 ThreadGroup 配置的功能

## Decisions

### Decision 1: ExecutionConfig 保留字段但不覆盖脚本

`ExecutionConfig` record 和 `PersistentTaskScenarioRecord` 保留 threads/rampUp/duration/loops 字段，但：
- 场景创建/更新 API 不再接受这些字段
- `ExecutionConfigMerger.merge()` 中使用 0 作为默认值
- `JmeterCommandExecutor.jmeterProperties()` 只在值 > 0 时才添加对应的 `-J` 属性

**Rationale**: 保留字段避免数据库迁移复杂性；0 值语义为"使用脚本原生配置"。未来如有场景级覆盖需求可恢复。

**Alternatives considered**:
- 完全删除字段 → 需要数据库迁移，过重
- 从脚本 ThreadGroup 读取填充 → 引擎需要解析脚本步骤，耦合度高

### Decision 2: 编辑按钮使用 `router.resolve()` + `window.open()`

通过 Vue Router 解析脚本编辑页路径，使用 `window.open()` 在新标签页打开。

```typescript
function openScriptEditor(scriptId: number) {
  const route = router.resolve(`/projects/${projectId}/scripts/${scriptId}/edit`);
  window.open(route.href, '_blank');
}
```

**Rationale**: 最简单的方式，不依赖额外依赖；新标签页不打断当前弹窗操作。

### Decision 3: normalizeConfig 校验逻辑调整

移除 `threads > 0` 的校验。当 threads=0 时（未设置场景级线程覆盖），JMeter 使用脚本原生 ThreadGroup 配置。

## Risks / Trade-offs

- **旧场景的线程值不再生效**：升级后，已有场景的 threads/rampUp/duration/loops 值会被忽略，执行将使用脚本中的线程配置。→ 用户需确认脚本中 ThreadGroup 配置正确。
- **JMX 中不包含 ThreadGroup 的脚本**：如果脚本没有 ThreadGroup（如纯 HTTP 采样器），JMeter 将以默认 1 线程执行。→ 风险较低，JMX 正常都会包含 ThreadGroup。
