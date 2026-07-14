## ADDED Requirements

### Requirement: Global Breadcrumb Display

系统 SHALL 在全局 TopBar 中展示面包屑导航，反映用户当前所在的页面层级路径。

- 面包屑 SHALL 从首页（`/`）开始，逐级展示到当前页面
- 面包屑 SHALL 覆盖所有页面：首页、项目列表、项目概览、脚本管理、任务计划（列表/详情）、执行详情、监控配置、报告管理、造数工厂、函数库、成员权限、系统配置、执行器配置
- 面包屑末段 SHALL 以加粗样式表示当前页面
- TopBar 高度 SHALL 为 56px
- TopBar SHALL NOT 包含 "Phase 2 Prototype" 文本

#### Scenario: Home page breadcrumb

- **WHEN** 用户访问首页（`/`）
- **THEN** TopBar 显示面包屑 `🏠 首页`
- **AND** "首页" 为末段，加粗且不可点击

#### Scenario: Project list breadcrumb

- **WHEN** 用户访问项目列表（`/projects`）
- **THEN** TopBar 显示面包屑 `🏠 首页 > 项目列表`
- **AND** "🏠 首页" 可点击，跳转到首页
- **AND** "项目列表" 为末段，加粗且不可点击

#### Scenario: Project overview breadcrumb

- **WHEN** 用户访问项目概览（`/projects/:id/overview`）
- **THEN** TopBar 显示面包屑 `🏠 首页 > 项目列表 > {项目名} > 项目概览`
- **AND** "🏠 首页"、"项目列表" 可点击跳转
- **AND** "{项目名}" 可点击跳转到项目概览
- **AND** "项目概览" 为末段，加粗且不可点击

#### Scenario: Task plan list breadcrumb

- **WHEN** 用户访问任务计划列表（`/projects/:id/task-plans`）
- **THEN** TopBar 显示面包屑 `🏠 首页 > 项目列表 > {项目名} > 任务计划`
- **AND** "任务计划" 为末段，加粗且不可点击

#### Scenario: Task plan detail breadcrumb

- **WHEN** 用户访问计划详情（`/projects/:id/task-plans/:planId`）
- **AND** 计划数据已加载
- **THEN** TopBar 显示面包屑 `🏠 首页 > 项目列表 > {项目名} > 任务计划 > {计划名}`
- **AND** "任务计划" 可点击，跳转到任务计划列表
- **AND** "{计划名}" 为末段，加粗且不可点击

#### Scenario: Execution detail breadcrumb

- **WHEN** 用户访问执行详情（`/projects/:id/executions/:executionId`）
- **AND** 执行数据已加载
- **THEN** TopBar 显示面包屑 `🏠 首页 > 项目列表 > {项目名} > 任务计划 > {计划名} > {执行名}`
- **AND** "任务计划" 可点击，跳转到任务计划列表
- **AND** "{计划名}" 可点击，跳转到计划详情
- **AND** "{执行名}" 为末段，加粗且不可点击

#### Scenario: Settings page breadcrumb

- **WHEN** 用户访问系统配置（`/settings`）
- **THEN** TopBar 显示面包屑 `🏠 首页 > 系统配置`
- **AND** "系统配置" 为末段，加粗且不可点击

### Requirement: Breadcrumb Click Navigation

面包屑中除末段外的每一段 SHALL 可点击，点击后跳转到对应页面。

- 可点击段在 hover 时 SHALL 显示视觉反馈（颜色变化、背景变化）
- 点击 SHALL 触发 `router.push()` 导航到对应的路由路径

#### Scenario: Click breadcrumb segment to navigate

- **WHEN** 用户在执行详情页面
- **AND** 面包屑为 `🏠 首页 > 项目列表 > 测试项目 > 任务计划 > 压测计划 > 执行 #42`
- **AND** 用户点击 "任务计划" 段
- **THEN** 系统跳转到任务计划列表页（`/projects/:id/task-plans`）
- **AND** 面包屑更新为 `🏠 首页 > 项目列表 > 测试项目 > 任务计划`

#### Scenario: Click home icon to navigate

- **WHEN** 用户在任意页面
- **AND** 面包屑包含 `🏠 首页` 段
- **AND** 用户点击 "🏠 首页" 段
- **THEN** 系统跳转到首页（`/`）

### Requirement: Breadcrumb Data Resolution

面包屑 SHALL 基于路由参数和业务数据动态解析各段的显示文本。

- 项目名 SHALL 来自 `useWorkspace().currentProject.name`
- 计划名 SHALL 来自 `useTaskPlans().activePlan.name`
- 执行名 SHALL 来自 `useTaskPlans().executionDetail.name` 或其 ID
- 当业务数据尚未加载时，SHALL 显示 ID 占位（如 `计划 #42`、`执行 #99`）
- Tab 名 SHALL 使用现有 `tabLabel()` 格式化函数

#### Scenario: Plan name not yet loaded

- **WHEN** 用户直接通过 URL 访问计划详情页（`/projects/1/task-plans/42`）
- **AND** 计划列表数据尚未加载完成
- **THEN** 面包屑显示 `🏠 首页 > 项目列表 > {项目名} > 任务计划 > 计划 #42`
- **AND** 计划数据加载完成后，自动更新为 `... > {计划名}`

#### Scenario: Execution name not yet loaded

- **WHEN** 用户直接通过 URL 访问执行详情页
- **AND** 执行详情数据尚未加载完成
- **THEN** 面包屑显示 `🏠 首页 > ... > 任务计划 > {计划名} > 执行 #99`
- **AND** 执行数据加载完成后，自动更新为 `... > {执行名}`

### Requirement: Remove Legacy Back Buttons

系统 SHALL 移除任务计划详情页中的旧返回按钮。

- `TaskPlanDetail.vue` SHALL NOT 包含 "返回计划列表" 按钮
- `ScenarioDetail.vue` SHALL NOT 包含 "返回计划详情" 按钮
- `ExecutionDetailView.vue` SHALL NOT 包含 "返回场景详情" 按钮
- 各详情页的标题和内容区域 SHALL 保持不变
- 执行详情的"查看历史记录"下拉 SHALL 保留

#### Scenario: Task plan detail without back button

- **WHEN** 用户访问计划详情页
- **THEN** 页面顶部不显示"返回计划列表"按钮
- **AND** 面包屑中的"任务计划"段提供导航回列表的功能

#### Scenario: Execution detail without back button

- **WHEN** 用户访问执行详情页
- **THEN** 页面顶部不显示"返回场景详情"按钮
- **AND** 面包屑中的"任务计划"和计划名段提供导航功能
- **AND** "查看历史记录"下拉按钮保留在页面 hero 区域
