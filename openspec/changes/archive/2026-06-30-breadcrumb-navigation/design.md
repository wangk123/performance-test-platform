## Context

当前 TopBar 显示两行：eyebrow "Phase 2 Prototype"（原型遗留）和 `pageTitle`（如 "测试 · 任务计划"）。`pageTitle` 由 `useNavigation` 计算，仅映射路由名到 Tab 名，不感知导航层级深度。任务计划体系（列表→计划详情→执行详情）的导航依赖页面内 `task-back-button` 逐级回退。

目标：TopBar 改为面包屑导航，全局统一展示页面层级，每段可点击跳转。

约束：
- Vue 3 + Ant Design Vue + TypeScript
- `useTaskPlans` 使用模块级 `ref`（状态全局共享）
- `useWorkspace` 管理项目和脚本上下文
- 脚本编辑页 (`/projects/:id/scripts/:scriptId/edit`) 是独立路由，无 MainLayout，不在此改动范围

## Goals / Non-Goals

**Goals:**
- TopBar 展示可点击面包屑，覆盖平台所有页面
- 面包屑末段表示当前页（不可点击），前面各段可点击跳转
- 支持任务计划体系最深 5 级嵌套（首页 > 项目列表 > 项目 > 任务计划 > 计划名 > 执行名）
- 移除旧的返回按钮和 "Phase 2 Prototype" eyebrow
- TopBar 高度从 76px 降至 56px

**Non-Goals:**
- 不改动路由结构或 API
- 不改动脚本编辑页的独立 TopBar
- 不添加动画或过渡效果（保持简洁）

## Decisions

### 1. 面包屑数据源：新建 `useBreadcrumb()` composable

**选择**：创建独立的 `useBreadcrumb()` 负责构建面包屑数组，TopBar 只负责渲染。

**替代方案考虑**：
- 直接在 TopBar 内计算 → 会被拒，TopBar 变得臃肿，无法复用
- 扩展 `useNavigation().pageTitle` → 语义不对，pageTitle 是字符串，面包屑是结构数组

`useBreadcrumb()` 内部：
```typescript
interface BreadcrumbSegment {
  label: string    // 显示文本
  to?: string      // 跳转路径，末段无 to 表示不可点击
}

// 返回 computed<BreadcrumbSegment[]>
```

数据来源优先级：项目名来自 `useWorkspace()`，计划名/执行名来自 `useTaskPlans()`，均使用路由 params 作为 key。

### 2. 面包屑段构建规则

**选择**：基于路由名 (`route.name`) 和路由参数 (`route.params`) 逐级构建。

| 路由 | 面包屑段 |
|------|---------|
| `/` (home) | `[🏠 首页]` |
| `/projects` | `[🏠 首页, 项目列表]` |
| `/projects/:id/overview` | `[🏠 首页, 项目列表, 项目名, 项目概览]` |
| `/projects/:id/scripts` | `[🏠 首页, 项目列表, 项目名, 脚本管理]` |
| `/projects/:id/task-plans` | `[🏠 首页, 项目列表, 项目名, 任务计划]` |
| `/projects/:id/task-plans/:planId` | `[🏠 首页, 项目列表, 项目名, 任务计划, 计划名]` |
| `/projects/:id/executions/:executionId` | `[🏠 首页, 项目列表, 项目名, 任务计划, 计划名, 执行名]` |
| `/projects/:id/monitoring` | `[🏠 首页, 项目列表, 项目名, 监控配置]` |
| … 其余 tab 同理 | … |
| `/settings` | `[🏠 首页, 系统配置]` |
| `/execution-nodes` | `[🏠 首页, 执行器配置]` |

**替代方案考虑**：
- 用 path segments 自动推导 → 无法获得项目名、计划名等业务名称
- 每个页面自己提供面包屑 → 不一致，重复代码多

### 3. 任务计划名称的加载时机

**选择**：`useBreadcrumb()` 调用 `useTaskPlans()` 获取 `activePlan` 和 `executionDetail`。当数据未加载时，显示 ID 占位（如 `计划 #42`），数据就绪后自动更新。

**原因**：`useTaskPlans` 的 `activePlan` 和 `executionDetail` 是模块级 ref，数据由 `TaskPlanList` 组件加载后全局可读。TopBar 中使用 `useTaskPlans()` 不会触发重复请求——请求由 `TaskPlanList` 的 `watch` 驱动。

### 4. 面包屑替代返回按钮

**选择**：移除以下组件中的 `task-back-button`：
- `TaskPlanDetail.vue`：移除"返回计划列表"
- `ExecutionDetailView.vue`：移除"返回场景详情"
- `ScenarioDetail.vue`：移除"返回计划详情"（当前已被 git status 标记为 modified）

保留各组件中 `task-detail-nav` 的 eyebrow 和右侧操作区（如执行详情的"查看历史记录"下拉），仅移除返回按钮。

**替代方案考虑**：
- 保留返回按钮与面包屑共存 → 冗余，且返回按钮只能回退一级而面包屑可跨级跳转

### 5. TopBar 视觉设计

**选择**：
-高度从 76px → 56px（单行面包屑不需要双行空间）
- 面包屑水平排列，用 `>` 分隔
- 可点击段：`var(--muted)` 色，hover 变蓝色 + 浅蓝底
- 当前段（末段）：深色加粗
- 首页图标用文字 `🏠`（简单直接，无需引入图标库）
- 移除 "Phase 2 Prototype" eyebrow

### 6. 不使用 Ant Design Vue 的 Breadcrumb 组件

**选择**：手写面包屑 HTML/CSS，不使用 `<a-breadcrumb>`。

**原因**：
- Ant Design 面包屑样式与 TopBar 设计不匹配
- 手写更轻量，无需额外 import
- 完全控制 hover、分隔符、截断行为

## Risks / Trade-offs

- **[低风险] 执行详情页的面包屑"执行名"依赖 `executionDetail` 加载完成** → 加载期间显示 `执行 #id`，加载完成后自动更新。不影响导航功能。
- **[低风险] `ScenarioDetail` 组件当前未被路由直接使用（scenarioId 重定向到 plan detail）** → 该组件的改动保持一致即可，实际渲染的是 TaskPlanDetail。
- **[低风险] TopBar 调用 `useTaskPlans()` 引入业务耦合** → 耦合仅体现在 composable 层（共享模块级 ref），TopBar 不直接调用 API。如果未来需要解耦，可在 `useBreadcrumb` 中做条件判断。
- **[可回滚] 移除返回按钮后，用户需要一个适应期** → 面包屑比返回按钮更灵活（可跨级跳转），长期体验更好。
