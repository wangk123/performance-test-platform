## Why

`redesign-frontend-design-system` 已落地 token 与双轨壳，并完成登录、首页、项目列表/概览、配置外壳、编辑器顶栏与执行监控密度等入口页；但项目内高频工作区（脚本、任务计划、造数、函数库、成员、弹层/抽屉）仍保留旧 eyebrow/编号/布局，与新壳气质不一致。需要按同一页面模式把剩余模块补齐，并完成暗色与效果图目视验收。

## What Changes

- 按标准业务页模式（density B：page-head + panel/toolbar，去掉 Module 0x / 装饰 eyebrow）重做：
  - 脚本管理 `ScriptWorkspace`
  - 任务计划列表/详情/场景详情及相关 Dialog
  - 造数工厂与 Seed Capture 全套面板
  - 函数库、成员权限
- 统一 Dialog / Drawer 外壳与内边距到新 token（导入脚本、参数抽屉、场景配置等）
- 脚本编辑器内部（Step 树 / StepDetail）去掉旧装饰，对齐工作台间距
- 执行详情与报告预览结构细节收口（在已有色板基础上）
- 完成暗色主题可读性扫读，并对照 `design-mockup-redesign.html` 做壳与关键页目视验收
- 不改变 API、路由语义、权限与业务字段

## Capabilities

### New Capabilities

- `frontend-workspace-page-restyle`：项目工作区内未完成页面与弹层的呈现契约（脚本、计划、造数、函数库、成员、Dialog/Drawer、编辑器内部、暗色/目视验收）

### Modified Capabilities

- （无主库 delta）本变更延续 `redesign-frontend-design-system` 中 `frontend-page-patterns` 的约定，不修改已归档主规格业务能力。

## Impact

- **代码**：`frontend/src/components/scripts/*`、`task-plans/*`、`views/Seed*`、`FunctionLibraryView`、成员相关视图、`dialogs/*`、`drawers/*`、`editor/*` 局部样式；可能继续收敛 `pages.css` / `script-editor.css`
- **依赖**：无新依赖；沿用既有 token / Ant / 双轨壳
- **API / 后端**：无
- **关联变更**：依赖 `redesign-frontend-design-system` 的 token 与壳；其未勾任务 7.2/7.3 并入本变更验收
