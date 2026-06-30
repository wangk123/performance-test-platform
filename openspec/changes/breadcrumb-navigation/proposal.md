## Why

当前平台页面头部只显示静态的项目名和 Tab 名（如"测试 · 任务计划"），用户无法快速感知自己在平台中的位置层级，也无法通过点击直接跳转到上层页面。任务计划体系的深层页面（列表→计划详情→执行详情）依赖页面内的"返回"按钮逐级回退，无法跨级跳转。需要将全局 TopBar 改造为可点击的面包屑导航，提供层级感知和快速跳转能力。

## What Changes

- TopBar 中的 "Phase 2 Prototype" eyebrow 和静态 `pageTitle` 替换为面包屑导航组件
- 新增 `useBreadcrumb` composable，根据路由层级动态生成面包屑段
- 面包屑覆盖所有页面：首页、项目列表、项目详情各 Tab、任务计划深层页面、系统配置、执行器配置
- 面包屑末段为当前页（不可点击），前面各段可点击跳转
- 移除任务计划详情页内旧的返回按钮（`task-back-button`），由面包屑承接导航职责
- TopBar 高度从 76px 调整为 56px，视觉更紧凑

## Capabilities

### New Capabilities

- `breadcrumb-navigation`: 全局面包屑导航，在 TopBar 中展示当前页面的层级路径，每段可点击跳转到对应页面

### Modified Capabilities

<!-- 无-->

## Impact

- **受影响代码**: `TopBar.vue`、`useNavigation.ts`、`useBreadcrumb.ts`(新)、`TaskPlanDetail.vue`、`ScenarioDetail.vue`、`ExecutionDetailView.vue`
- **受影响样式**: `layout.css`（TopBar 高度、面包屑样式）、`pages.css`（移除 task-back-button 样式）
- **API**: 无影响
- **路由**: 无影响
- **依赖**: 面包屑数据依赖 `useWorkspace`（项目名）、`useTaskPlans`（计划名/执行名），为模块级共享状态
