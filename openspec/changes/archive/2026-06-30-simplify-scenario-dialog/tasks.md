## 1. 后端：移除场景 API 线程参数

- [x] 1.1 修改 `CreateScenarioRequest` 和 `UpdateScenarioRequest`，移除 threads/rampUp/duration/loops 字段
- [x] 1.2 修改 `TaskScenarioService.createScenario()` 和 `updateScenario()`，移除线程参数，使用 0 作为默认值传入 entity
- [x] 1.3 修改 `PersistentTaskScenarioRecord.updateProfile()`，移除 threads/rampUp/duration/loops 参数，保持字段不变（写入 0）
- [x] 1.4 修改 `ExecutionConfigMerger.merge()`，使用 0 作为 threads/rampUp/duration/loops 的默认值
- [x] 1.5 修改 `JmeterCommandExecutor.jmeterProperties()`，只在值 > 0 时才添加 -Jthreads/-Jloops/-Jduration/-JrampUp 属性
- [x] 1.6 修改 `ScenarioExecutionService.normalizeConfig()`，移除 threads > 0 校验

## 2. 前端：更新 API 和类型定义

- [x] 2.1 修改 `api/task-plans.ts` 中 `createScenarioApi` 和 `updateScenarioApi` 的 payload 类型，移除 threads/rampUp/duration/loops
- [x] 2.2 修改 `useTaskPlans.ts` 中 `saveScenario()` 方法，移除线程参数传递

## 3. 前端：改造场景弹窗 UI

- [x] 3.1 修改 `ScenarioDialog.vue` 表单模板，移除线程数/Ramp-Up/持续时间/循环次数四个 `<a-form-item>`
- [x] 3.2 修改 `ScenarioDialog.vue` 中 `form` 响应式对象，移除 threads/rampUp/duration/loops 字段
- [x] 3.3 修改 `ScenarioDialog.vue` 中 watch 初始化逻辑，移除线程字段的回填
- [x] 3.4 修改 `ScenarioDialog.vue` 中 `onSave()` 方法，移除线程参数传递
- [x] 3.5 在脚本选择列表中为每个 `.script-choice` 添加编辑按钮，点击通过 `router.resolve()` + `window.open()` 在新标签页打开脚本编辑器

## 4. 前端：更新场景详情显示

- [x] 4.1 修改 `ScenarioDetail.vue`，移除 `{{ scenario.threads }} 线程` 显示文案

## 5. 验证

- [ ] 5.1 启动后端服务，验证场景创建/更新 API 调用成功
- [ ] 5.2 验证场景弹窗表单正常（无线程字段、有编辑按钮）
- [ ] 5.3 验证脚本编辑按钮在新标签页正确打开脚本编辑器
- [ ] 5.4 验证场景详情页不再显示线程数
- [ ] 5.5 触发一次场景执行，确认 JMeter 使用脚本原生 ThreadGroup 配置执行成功
