## 1. Script workspace & editor internals

- [x] 1.1 重做 `ScriptWorkspace`：page-head + panel，去掉 `Module 03` / 装饰 eyebrow
- [x] 1.2 对齐脚本列表/详情分栏间距与 token
- [x] 1.3 收敛 `StepSidebar` / `StepDetail` 旧装饰与间距，保留保存/导入/步骤编辑入口
- [x] 1.4 确认脚本导入/创建 Dialog、参数 Drawer 外壳跟齐 token

## 2. Task plans

- [x] 2.1 重做 `TaskPlanList` 为标准列表页模式
- [x] 2.2 重做 `TaskPlanDetail` / `ScenarioDetail` 头区与面板层级
- [x] 2.3 统一 `ScenarioDialog` / `TaskPlanDialog` / `ExecuteConfirmDialog` / `ScenarioRowPanel` / 线程组配置编辑器外观
- [x] 2.4 回归：计划、场景、执行跳转与操作可用

## 3. Seed factory suite

- [x] 3.1 重做 `SeedFactoryView` 入口外壳为 page-head + panel
- [x] 3.2 统一 `SeedCapturePanel` / Strategy / Sample / Datasource 面板头与工具条
- [x] 3.3 统一 Analysis 列表与 Detail 页层级（Sample/Analysis Detail）
- [x] 3.4 确认造数相关操作按钮与表单字段未改行为

## 4. Function library & members

- [x] 4.1 重做 `FunctionLibraryView` 标准头/表，去掉装饰 eyebrow
- [x] 4.2 重做项目成员权限页（或 ProjectDetail 内成员区）为标准 panel 模式
- [x] 4.3 扫尾项目内其余仍带旧 hero/编号的碎片 UI

## 5. Dialogs, drawers & residual chrome

- [x] 5.1 扫 `components/dialogs/*` 与 `drawers/*`：边框/间距/标题跟 token
- [x] 5.2 去掉残余 `nav-index` / 无信息量英文 eyebrow（保留真正分区标题）
- [x] 5.3 抽查硬编码旧色并收敛到 CSS 变量

## 6. Dark theme & visual acceptance

- [x] 6.1 暗色下走查：脚本、计划、造数、函数库、成员、弹层
- [x] 6.2 暗色下走查：编辑器内部与执行详情
- [x] 6.3 对照 `design-mockup-redesign.html` 验收双轨壳 + 关键工作区密度
- [x] 6.4 `frontend` build / 类型检查通过
