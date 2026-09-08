# 计划模块 UI 样式 Review 与修复计划（2026-09-08）

## 0. 背景与对照基准

用户反馈计划模块（任务计划列表 / 计划详情 / 场景 / 执行详情）样式差，四个主问题：

1. Markdown 预览 / 编辑 / 章节导航区域风格不一致，观感割裂；
2. 文档内容滚动是整个页面在滚，而不是文档内容区域独立滚动；
3. 结构化卡片（场景设计模块）样式丑；
4. 上方进度导航条（阶段步骤条）样式、字号、整体风格不合适。

对照基准：

- **效果图**：用户提供的截图（真实应用骨架 GlobalRail + ProjectContextNav + TopBar 之上，计划详情页的目标形态）。
- **原型规格**：仓库内 `plan-document-prototype.html`（P0-1 时期改版效果图，与截图同族设计语言，含步骤条、工具栏、TOC、文档排版、评审时间线、发布快照、冲突对比屏的**完整 CSS 规格**，本计划的数值规格全部取自该文件）。
- **设计令牌基座**：`frontend/src/styles/base.css` + `frontend/src/constants/design-tokens.ts` 已与原型同源（IBM Plex Sans/Mono、canvas `#F4F6F8`、accent `#0B7F8A`、ok/warn/danger、全套暗色令牌）。**基座健康，问题全部出在计划模块实现层未按规格落地。**

结论：不需要新建设计体系，是一次"按已有规格落地 + 补全"的重构。

---

## 1. 现状盘点

| 文件 | 角色 | 现状评价 |
|---|---|---|
| `components/task-plans/TaskPlanList.vue` | 计划列表 | a-table 可用；阶段 tag 用 Ant 预设色（default/processing/warning/cyan/success），不在 token 语义家族内；`task-filters` 两列 grid 只放了一个搜索框，右列空置 |
| `components/task-plans/TaskPlanDetail.vue` | 计划详情壳 | page-head + PlanPhaseStepper + a-tabs 堆叠，整页随 `.content` 滚动 |
| `components/task-plans/PlanPhaseStepper.vue` | 进度导航条 | 细边框盒子 + 22px 圆点数字 + 灰色文字状态，无连接线、无状态 pill、无 done/current 色阶，与效果图差距最大 |
| `components/task-plans/PlanDetailDocument.vue` | 文档 Tab | 问题核心，见 §2-P1/P2 |
| `components/task-plans/ScenarioDesignModule.vue` | 场景卡 | 素盒子卡 + 原生 `<details>` + `window.prompt/confirm`，见 §2-P3 |
| `components/task-plans/ChecklistView.vue` | 约束清单 | a-checkbox 默认样式；`parseChecklistItems` 只抓 checkbox 行，**"入口准则/出口准则"分组标题丢失** |
| `components/task-plans/PlanDetailReview.vue` | 评审 Tab | a-timeline 默认蓝点；`window.prompt` 驳回；无元信息侧栏 |
| `components/task-plans/PlanDetailReport.vue` | 报告 Tab | 裸 h3 + a-alert + a-table 堆叠；verdict 用 Ant 色 green/red/orange 非 token |
| `components/task-plans/PlanDetailPublish.vue` | 发布 Tab | 裸表单 max-width 520；分享链接 `<code>` 无复制按钮 |
| `components/task-plans/PlanConflictDialog.vue` | 冲突屏 | 双栏 diff 已有，但无 warn 提示条/图例，diff 行内 rgba 色未走 token |
| `components/task-plans/PlanSectionEditor.vue` | 章节编辑弹窗 | MdEditor 固定 420px，可用 |
| `components/task-plans/ExecutionDetailView.vue` | 执行详情 | 结构最成熟（panel + summary-strip + aggregate-table），基本只随批次 F 微调 |
| `components/task-plans/ScenarioDetail.vue` | 场景详情 | 标准表格页，低优先 |
| `views/SharePlanPage.vue` | 只读分享页 | 复用 MdPreview，需继承同一预览预设 |
| `utils/plan-markdown.ts` | 解析层 | `splitSections/extractSection/replaceSection/parseScenarioBlocks` 齐备，**纯展示层重构不需要动它**（仅新增分组解析函数） |
| `composables/usePlanDoc.ts` | 状态层 | 健康，不动 |

---

## 2. 问题清单（证据）

### P1 预览 / 编辑 / 目录风格割裂（用户#1）

1. **Pretty 视图只渲染 6 个受限章节**（`CONSTRAINED` 过滤），五个叙事章节（背景/策略/风险/附录/结论）被隐藏，靠一行 `pretty-hint` 提示"请切换到 Markdown 视图查看"。效果图是**全文档连续阅读视图**：TOC 一~十一章全部在列，正文从文档标题起连续渲染。这是割裂感的最大来源。
2. **md-editor-v3 `MdPreview` 用默认 GitHub 排版**，全项目没有任何针对 `.md-editor-preview` 的排版覆盖（仅 ScenarioDesignModule 去了 padding）：全边框表格 vs 效果图的横线表格 + canvas 表头；h2 无下边框 vs 效果图 h2 带分隔线；代码/引用样式同样不一致。同一个文档在 Pretty（分节卡）和 Markdown（整篇预览）两个模式下是两套排版。
3. **TOC 无状态**：`.doc-toc` 是纯文本列表，无当前章节高亮（效果图有 accent 左边框 + soft 底 + 加粗的 current 态）、无 scrollspy；`scrollTo()` 在 Markdown 模式下查不到 `[data-section]` 直接 return，**点击静默无效**。
4. **工具栏是裸排**：`a-segmented`（Ant 默认样式）+ 按钮直接浮在内容上，效果图中是白底圆角工具条（segmented 胶囊 + 右侧按钮组 + rev 号）。
5. **编辑态动线粗糙**：编辑按钮复用为保存按钮（文案切换）；MdEditor 固定 560px 高，与外层页面滚动叠加成双滚动；无 dirty 状态。
6. `MdPreview/MdEditor` 均未传 `theme`，**暗色主题下预览/编辑仍是亮色**（SharePlanPage 同病）。
7. ScenarioDesignModule 内嵌 MdPreview 形成"卡中卡"，字号 13px 与外层预览字号不一致。

### P2 滚动容器错误（用户#2）

- `.app-shell` 100vh + `overflow:hidden`，`.content`（`layout.css:318`）是唯一滚动容器 → 页头、步骤条、Tabs、工具栏、TOC 全部长文档一起滚走。
- 效果图语义：**页头/步骤条/Tabs/工具栏/TOC 固定，只有文档主体滚动**。
- 现有 `scrollIntoView` 滚的是 `.content`，改造后需相对新的滚动容器定位并补 `scroll-margin-top`（粘性导航不得遮挡节首，检索佐证：ux-guidelines "Sticky Navigation — add padding/scroll-margin compensation"）。

### P3 结构化卡片丑（用户#3）

`ScenarioDesignModule` 的 `.scenario-card`：

- 1px 边框 + 8px 圆角 + 12px padding 的素盒子，无头部层级：场景名是 `<strong>`，旁边灰色长文本"最新执行：…"（title 悬浮，无状态色、无省略）；
- "目的：xxx" 纯段落；设置用内嵌 MdPreview（字号又与外层不同）；
- 操作按钮左对齐裸排，脚本绑定状态（未关联/已关联 #id）完全不可见；
- 执行记录是原生 `<details><summary>` + `<ul>` 纯文本，SUCCESS/FAILED 无状态色；
- `window.prompt` 关联脚本、`window.confirm` 跳过预检——原生弹窗与整体风格割裂；
- 效果图对应件是 canvas 底 + 字段栅格（sd-grid）+ 表格化设置（preset-table）+ 状态色徽章的层级化卡片。

另：`ChecklistView` 丢"入口准则/出口准则"分组；结论空态是裸文本（效果图是虚线框 `conclusion-empty`）。

### P4 进度导航条（用户#4）

现状 `PlanPhaseStepper`：透明细边框盒、22px 圆点、label 默认字号、当前阶段状态是 12px 灰文本。效果图规格：

- canvas 底、radius 10px、padding 14px 16px 的容器；
- 24px 节点圆（1.5px 边框），节点间 26px×1.5px 连接线（完成段变 ok 绿）；
- done 节点 ok 绿底白勾、current 节点 accent 实底、未来灰边框；
- 阶段名 12px（current 为 accent 加粗），状态是 **pill 徽章**：current = accent 实底白字、done = ok-soft、未来 = 白底灰边框；
- 窄屏 `overflow-x:auto`。

字号体系统一基准（效果图/原型）：正文 14px、页面标题 20px/700、tab 与按钮 13px、辅助 12px、pill/tag 11px。

### P5 补充发现（效果图未直接覆盖，需设计补全）

- 评审/报告/发布三个 Tab 均为裸元素堆叠，原型有完整规格（review-layout 双栏 + 自绘时间线；report-layout；publish 快照行卡 + 分享行）——按原型补全即可；
- verdict/Ant tag 色未映射 token 语义色（达成=ok、未达成=danger、无法判定=warn、已发布=ink 实底）；
- 列表页阶段 tag、`task-filters` 空列；
- 空状态不统一；
- 暗色模式：md-editor theme、diff 行 rgba；
- a11y：TOC 无 `nav`/`aria-current`，步骤条无 `aria-current="step"`；
- 响应式：doc-layout 208px 双栏在窄屏无降级。

---

## 3. 目标设计规格

### 3.1 页面骨架（计划详情）

```
.content（本路由下不滚动）
└─ .task-detail            height:100%; flex 列; gap 12
   ├─ .page-head           标题 20/700 + meta 行 + 右侧按钮组（固定）
   ├─ .plan-phase-stepper  canvas 底圆角容器（固定）
   ├─ a-tabs               样式对齐效果图（固定）
   └─ .plan-document       flex:1; min-height:0
      ├─ .doc-toolbar      白底工具条：segmented | rev + 按钮组（固定）
      └─ .doc-body         flex:1; min-height:0; grid 208px 1fr
         ├─ .doc-toc       独立列，自身 overflow-y:auto，scrollspy 高亮
         └─ .doc-main      ★唯一文档滚动容器，overflow-y:auto
            └─ .doc-section × N / .doc-title / MdEditor(height:100%)
```

实现要点：`.content:has(.task-detail)` 时 `overflow:hidden`（项目已用 `:has`，见 `base.css:123`）；a-tabs 覆盖为 flex 列撑满且 `.ant-tabs-content` 高度 100%，作用域限定 `.task-detail` 命名空间，不影响其它页面。

### 3.2 组件规格（数值取自 `plan-document-prototype.html`）

| 组件 | 规格 |
|---|---|
| 步骤条 | 容器 `padding:14px 16px; background:var(--canvas); radius:10px`；节点 24px 圆 1.5px 边框；连线 `26px×1.5px margin:11px 6px 0`，done 段 `var(--ok)`；done 节点绿底白勾、current accent 底；阶段名 12px；状态 pill 11px `padding:1px 8px` radius 999——current accent 实底白字 / done ok-soft / 未来白底灰边框 |
| Tabs | 字号 13px/500，active accent 600 + 2px 底边（覆盖 a-tabs：item padding `12px 14px`、ink-bar 色 accent） |
| 工具条 | 白底卡 `padding:8px 12px; radius:10px; border`；segmented：canvas 底 `padding:3px; radius:8px`，选中项白底 + accent 字 + 微阴影；右侧 `rev.n` mono 12px + 按钮组 |
| TOC | 列宽 208px；标题 12px/700；项 12.5px `padding:5px 8px; radius:6px`；current：accent 字 + `border-left:2px accent` + soft 底 + 600；受限/叙述章节右上角 10px tag |
| 文档阅读视图 | 每章一张 `.doc-section` 白卡 `radius:12px; padding:18px 22px`；章头 h2 15px + `padding-bottom:8px` 下边框 + 右侧"受控"chip / 编辑章节按钮；文档标题块 20px/700 + meta |
| markdown 排版（全局预设 `.plan-md`） | `line-height:1.75`；h2 16px 下边框；h3 14px；行内 code = mono 12.5px canvas 底 chip；表格横线式 + th canvas 底 12px muted；blockquote 左 3px accent + soft 底；img max-width:100%；mermaid 居中 |
| 场景卡 | 头部：S 编号方块徽章（accent-soft）+ 名称 14/600 + 类型 mono chip + 右侧最新执行状态 pill（SUCCESS ok-soft / FAILED danger-soft / 未执行灰）+ 时间与关键指标 mono 12px 省略；主体：目的字段（11px muted 标签 + 13px 正文）、设置表 preset 风格；底部操作条：左侧脚本绑定 chip（未关联 warn-soft / 已关联 ok-soft `#id` mono），右侧按钮组（编辑 + 关联脚本/执行 primary）；执行记录：样式化 summary + 行级状态色点 |
| 约束清单 | 分组渲染（入口准则/出口准则），自绘 16px 圆角 checkbox 风格（保留 a-checkbox 行为 + token 色覆盖），自动项 accent chip 右置 |
| 评审 | `grid 1fr 300px`：左自绘时间线（`padding-left:26px`、2px 竖线、dot 18px：评审 accent / 系统 canvas+边框）、气泡 `tl-body`；右侧元信息卡（阶段/状态/参与人/批注数 kv）；驳回改 a-modal + textarea 必填 |
| 报告 | verdict 头部卡（token 色 pill + 预填结论文本）；未达标表 size=small；结论预览进 `.doc-section` 卡 |
| 发布 | 表单卡 + 前置条件 warn 条；快照行卡（36px ink 方块图标 + revision mono + 时间）；分享行（mono 链接 chip + **复制按钮**（复用 `utils/clipboard.ts`）+ 状态 pill + 撤销） |
| 冲突屏 | warn 提示条 + 双栏 diff（del/add 用 `--danger-soft/--ok-soft`）+ 图例 + 底部动作区 |
| 空态 | 统一 `.plan-empty`：虚线框 + muted 文案 + 引导动作 |

### 3.3 效果图未覆盖处的补全决策

| 决策点 | 结论 |
|---|---|
| Pretty 视图范围 | 升级为**全文档阅读视图**：按 `CANONICAL_HEADINGS` 顺序连续渲染 11 章——受限章节走结构化模块（清单/场景卡/表格），叙事章节直接 markdown 渲染；删除 pretty-hint |
| Markdown 模式 TOC | 保留可用：MdPreview 开启 `md-heading-id` 生成锚点，与 Pretty 共用 scrollspy/滚动定位（ IntersectionObserver 监听章首元素） |
| 编辑态动线 | 非编辑：`编辑`（primary，仅 Markdown 模式）；编辑中：`保存`（primary，dirty 才可用）+ `取消`；MdEditor 高度改 flex 撑满（去 560px），消除双滚动 |
| 原生弹窗替换 | 关联脚本 → a-modal：项目脚本版本下拉（`useTaskPlans` 已有 scripts/scriptById）+ 手输 ID 兜底；驳回原因 → a-modal textarea；跳过预检 → `Modal.confirm` |
| 列表阶段色 | 语义映射 token：DRAFT 灰（#EEF0F3）、REVIEW accent-soft、EXECUTION warn-soft、REPORT ok-soft、PUBLISH ink 实底白字（与原型 badge 家族一致） |
| 响应式 | ≤1280px TOC 208→180px；≤1100px doc-layout 单列、TOC 变工具条下方横向滚动 chips；步骤条 overflow-x |
| 暗色 | 新样式一律 token（禁裸 hex）；md-editor `theme` 属性接 `useTheme`；SharePlanPage 同步 |
| a11y | TOC `<nav aria-label="章节导航">` + `aria-current`；步骤条 `aria-current="step"`；图标按钮补 aria-label |

---

## 4. 修复计划（六批，可独立验收）

> 原则：只动展示层；`usePlanDoc.ts` / `plan-markdown.ts` 逻辑不动（仅新增清单分组解析）；不碰 P0-1/P0-3 保存链与判等语义；全部新样式走既有 token，暗色零额外成本。

### 批次 A：页面骨架与滚动容器（P2）— ~0.5d
- `TaskPlanDetail.vue` / `PlanDetailDocument.vue`：`.task-detail` 高度 100% flex 列；`.content:has(.task-detail)` 关滚动；a-tabs 撑满覆盖；`.doc-main` 成为唯一滚动容器，`scrollTo` 改为容器内定位 + `scroll-margin-top`。
- **验收**：长文档下页头/步骤条/Tabs/工具条/TOC 全程可见；TOC 点击精确落节首不被遮挡；窗口缩放正常；其他页面滚动不受影响。

### 批次 B：Markdown 排版与文档工具条（P1 核心）— ~1d
- 新增全局 `.plan-md` 预设（覆盖 `.md-editor-preview` 排版为 §3.2 规格），四处 MdPreview（文档/场景设置/报告/分享页）统一挂用；`theme` 属性接 useTheme。
- Pretty → 全文档阅读视图（含文档标题块、叙事章节、删除 hint）；每章 `.doc-section` 卡（章头 + 受控 chip + 编辑章节按钮）；`.doc-toolbar` 卡片化 + rev 展示；TOC current 态 + scrollspy + Markdown 锚点。
- 可选增强：结论达成表（backfill:verdict）按原型 `.backfill-wrap` 虚线强调样式渲染。
- **验收**：两模式排版一致；TOC 高亮跟随滚动且两模式均可跳转；暗色下预览/编辑正常。

### 批次 C：页头 / 步骤条 / Tabs（P4）— ~0.5d
- 重写 `PlanPhaseStepper.vue` 为 §3.2 规格（连接线 + 状态 pill + 三态色阶 + aria-current）。
- `TaskPlanDetail.vue` 页头：标题 20/700、meta 数字 mono、按钮层级（编辑默认配置 default / 提交评审 primary / 撤回·退回草稿 default，danger 语义预留）。
- **验收**：与效果图步骤条逐项一致；窄屏横向滚动。

### 批次 D：结构化卡片（P3）— ~1d
- 重设计 `ScenarioDesignModule.vue`（§3.2 场景卡规格：编号徽章、状态 pill、绑定 chip、操作条、执行记录状态色）；`ChecklistView.vue` 分组 + 自绘样式（`plan-markdown.ts` 新增 `parseChecklistGroups`，不改原函数）；统一 `.plan-empty` 空态。
- **验收**：七章卡片层级与效果图同族；多场景/长记录不撑破；清单分组完整。

### 批次 E：评审 / 报告 / 发布 / 冲突屏 + 原生弹窗清零 — ~1d
- 四个组件按 §3.2 规格补全（评审双栏、报告 verdict 卡、发布快照/分享行 + 复制、冲突 warn 条 + 图例）；verdict/tag 色 token 化；`window.prompt/confirm` 全部替换为 a-modal / Modal.confirm（含关联脚本选择器）。
- **验收**：模块内无原生弹窗；四 Tab 均为卡片化布局；分享链接可复制。

### 批次 F：列表页 / 场景页 / 对话框 / 响应式 / a11y 收尾 — ~0.5~1d
- 列表页阶段语义 badge + 筛选行修正；ScenarioDetail status 色 token 化；TaskPlanDialog/ScenarioDialog/PlanSectionEditor 表单栅格与高度微调；响应式断点（§3.3）；a11y 项；暗色全量回归。
- **验收**：`npm run build`（vue-tsc）绿；冒烟清单走查：新建计划 → 编辑文档（整篇/章节/冲突三选一）→ TOC 跳转 → 提交评审 → 批注/通过/驳回 → 关联脚本 → 执行 → 报告 verdict → 发布 → 创建分享/复制/撤销；亮暗两主题截图比对。

---

## 5. 风险与约束

| 风险 | 对策 |
|---|---|
| md-editor-v3 内部类名随升级变化 | 覆盖集中在 `.plan-md` 一处；依赖锁 ^6.5.6；覆盖只作用于预览排版不碰交互 |
| `:has` 选择器兼容性 | 项目已在其于 `base.css` 使用 `:has`（同基线 Chrome/Safari）；若需兜底，由路由 meta 驱动 class 替代 |
| a-tabs 高度改造外溢 | 全部覆盖限定 `.task-detail` 前缀作用域 |
| P0-3 判等/报告达成表零回归 | 不改 `usePlanDoc`、保存链、判等接口；报告 Tab 仅布局与色板调整，字段不动 |
| 展示层重构碰坏 markdown 写回 | `replaceSection/extractSection` 等纯函数不动；清单分组仅新增解析函数 |

工作量合计约 4.5~5 人日；A→F 顺序执行（B 依赖 A 的滚动容器，D 依赖 B 的卡片与排版预设，E/F 可并行）。
