## 1. 核心 Logics — useBreadcrumb composable

- [x] 1.1 新建 `frontend/src/composables/useBreadcrumb.ts`，定义 `BreadcrumbSegment` 接口 (`{ label: string; to?: string }`)
- [x] 1.2 实现面包屑构建逻辑：基于 `route.name` 和 `route.params` 构建所有页面层级的面包屑数组
- [x] 1.3 从 `useWorkspace()` 获取当前项目名作为面包屑段
- [x] 1.4 从 `useTaskPlans()` 获取计划名和执行名，数据未加载时显示 ID 占位（`计划 #id`、`执行 #id`）
- [x] 1.5 返回 `computed<BreadcrumbSegment[]>` 确保数据响应式更新

## 2. TopBar 改造

- [x] 2.1 修改 `TopBar.vue`：移除 `<div class="eyebrow">Phase 2 Prototype</div>` 和 `<div class="page-title">{{ pageTitle }}</div>`
- [x] 2.2 引入 `useBreadcrumb()`，渲染面包屑 HTML 模板
- [x] 2.3 实现面包屑渲染：每段用 `<span class="crumb-sep">&gt;</span>` 分隔，可点击段用 `<a>` 包裹
- [x] 2.4 末段加粗 (`class="crumb-current"`)，不可点击；前面段点击触发 `router.push(segment.to)`

## 3. 样式调整

- [x] 3.1 在 `layout.css` 中：TopBar 高度从 76px 改为 56px
- [x] 3.2 在 `layout.css` 中新增面包屑样式：`.topbar-breadcrumb` 容器、`.crumb-seg` 可点击段、`.crumb-sep` 分隔符、`.crumb-current` 当前段
- [x] 3.3 可点击段 hover 状态：颜色变蓝色 + 浅蓝背景
- [x] 3.4 在 `pages.css` 中移除 `.task-back-button` 相关样式

## 4. 旧返回按钮清理

- [x] 4.1 `TaskPlanDetail.vue`：移除 `<a-button class="task-back-button">返回计划列表</a-button>` 和 `<span class="eyebrow">Task Plan</span>`
- [x] 4.2 `ScenarioDetail.vue`：移除 `<a-button class="task-back-button">返回计划详情</a-button>` 和 `<span class="eyebrow">Scenario</span>`
- [x] 4.3 `ExecutionDetailView.vue`：移除 `<a-button class="task-back-button">返回场景详情</a-button>` 和 `<span class="eyebrow">Scenario</span>`，保留"查看历史记录"下拉

## 5. 验证

- [x] 5.1 验证首页、项目列表、系统配置、执行器配置面包屑正确展示
- [x] 5.2 验证项目详情各 Tab（概览、脚本、任务计划、监控、报告等）面包屑正确展示
- [x] 5.3 验证任务计划深度页面（列表→计划详情→执行详情）面包屑逐级展示和跳转
- [x] 5.4 验证直接通过 URL 访问深层页面时，面包屑占位符正确显示并在数据加载后自动更新
- [x] 5.5 验证点击面包屑各段能正确跳转到对应页面
- [x] 5.6 运行 `npm run build` 确保 TypeScript 类型检查和构建通过
