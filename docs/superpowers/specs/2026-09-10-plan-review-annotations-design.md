# 计划文档行级批注与评审工作台 设计

> 2026-09-10 · 评审功能改造。本文档取代 2026-09-02 设计 §6.1「全文档级批注，不做行锚定」的决策（D5 仍不做行级合并——批注不进正文，两者不冲突），并与 2026-09-10 版本体系设计（快照+修订记录）共存。

## 1. 背景与目标

**痛点**：评审记录只能在与文档割裂的「评审」Tab 以全文档级自由文本添加。评审会中换 Tab 加记录、再切回文档、滚动找回之前的记录继续评，上下文反复断裂；会前熟悉文档时也无法随手标记疑问。

**目标**：类 Word 的块级批注体验——评审人在文档行边直接加批注，意见锚定到具体内容块；批注面板与评审工作台汇总同一份数据。会前阅读、会中评审、会后处理意见全程不离开文档上下文。

**验收口径**：一个项目成员在评审会中打开文档 Tab，悬浮某条指标行 → 行尾出现「＋批注」→ 填写提交 → 该行出现徽标、右侧面板与评审 Tab 工作台同时出现这条记录；会议结束前每条意见被逐个「解决」；负责人点「评审通过」时若有遗漏会得到提醒（不阻断）。

**已确认的关键决策**（用户拍板）：

| 决策点 | 结论 |
|---|---|
| 锚定粒度 | **块级**（段落/标题/引用/表格行/列表项/清单项 → 源 Markdown 行），不做选区级 |
| 展示形态 | **方案 C**：行边徽标 + 高亮，右侧可开关批注面板（与评审 Tab 同数据源） |
| 解决（Resolve） | 线程级状态；「评审通过」时有未解决批注 → **提醒但不阻断**（后端不拦） |
| 批注生命周期 | **跨轮次留存**：驳回重提、new-revision 开新修订版，批注原地保留、容错重挂 |
| 原「评审」Tab | **改造为评审工作台**（动作条 + 批注总表 + 流转记录分区） |

## 2. 概念模型

| 术语 | 定义 |
|---|---|
| 块（block） | 文档渲染的最小锚定单元：段落、标题、引用、代码块、表格的数据行（`<tbody><tr>`）、列表项（`<li>`）、约束清单项 |
| 锚点（anchor） | `(anchorLine, anchorText, sectionTitle)` 三元组，记录创建时块所在的源 Markdown 行号、纯文本快照、所属章标题 |
| 线程（thread） | 一条根批注 + 至多一层回复；`resolved` 状态存根批注上，作用于整条线程 |
| 锚定状态 | **纯派生、不落库**：每次渲染时由 `anchorText` 与当前文档比对得出 正常 / 已重挂 / 断链 三态 |
| 未锚定 | `anchor` 三字段为空的 REVIEW 批注：存量历史批注、流转附带的驳回原因等，置顶分组展示 |

批注本身无状态机，只有 `resolved` 二态（可反复切换）。批注的全部读写**不触碰 `plan.body` 与 `plan.revision`**，与乐观锁无交集，多人并发批注天然可叠加。

## 3. 交互设计（各功能的界面位置与入口）

### 3.1 文档 Tab：新增批注

- **入口**：Pretty 视图下，悬浮任意可锚定块 → 该块右上缘（文档容器右缘对齐块顶部）浮现「＋批注」幽灵按钮；移开隐藏，按钮自身悬停时高亮。
- **输入**：点击后块下方展开内联输入框（textarea + 「提交」「取消」），placeholder「针对此行添加批注（评审中全员可见）」，`Ctrl/Cmd+Enter` 提交、`Esc` 取消。
- **提交后**：POST 成功 → 该块立即出现徽标与高亮，右侧面板（若开启）顶部插入新卡，评审 Tab 无需任何操作即拥有该记录（同一数据源）。
- **必带锚点**：前端新增入口只此一处，提交必带 `{line, text, section}`；不再提供全文档级批注的新增入口。
- **权限**：沿用现有 COMMENT（项目成员 × DRAFT/REVIEW 阶段）。无权限时悬浮不出按钮；进入执行后批注整体只读（现状不变）。

### 3.2 文档 Tab：批注面板（右侧滑出）

- **入口**：Tabs 工具条（`rightExtra`，与 Pretty/Markdown 视图切换同排）新增「💬 批注 N」toggle 按钮，N = 线程总数。REVIEW 阶段默认开启，其他阶段默认关闭；用户手动开关记入 localStorage（`plan-comment-panel-open`），跨会话记忆。
- **面板结构**（自上而下）：
  1. Header：「批注」+「未解决 N · 已解决 M」+ 收起按钮（»）。
  2. 「未锚定」分组置顶：历史批注与驳回原因等无锚点 REVIEW 批注，灰卡样式。
  3. 按 `CANONICAL_HEADINGS` 章节顺序分组的未解决线程。
  4. 「已解决」折叠分组（可展开，灰卡）。
- **卡片内容**：作者（头像+名）、相对时间、`rev N`（body_revision）、锚定行引用文本块（黄底左线）、正文、回复列表（一层）、回复输入框（有 COMMENT 权限时）、操作区「↧ 定位」「✓ 解决 / 重新打开」（按 canResolve 显隐）「删除」（按 canDelete 显隐）。
- **已解决线程锁定**：不可直接回复，需先「重新打开」——防止解决后又被悄悄翻案。
- **定位交互**：点「定位」→ 若当前是 Markdown 源码视图自动切回 Pretty → 滚动容器 smooth 滚动到目标块 → 块闪烁高亮约 1.5s。

### 3.3 文档 Tab：徽标、高亮与断链呈现

- 有未解决线程的块：浅黄高亮 + 橙色「💬 n」徽标（n = 该块线程总数）。
- 线程全部已解决的块：高亮褪去 + 灰色「✓ n」徽标。
- 断链线程：不显示在原位置，改为所在章（sectionTitle）顶部一条灰色折叠条「⚠ N 条批注的锚点因内容变更失效」，展开可见原引用文本与批注内容。
- Markdown 源码视图：不渲染徽标与新增入口（源码无稳定块语义）；面板保留可用，定位时自动切回 Pretty。
- 八、场景设计章（自定义组件渲染）：第一期仅支持**章级锚点**——悬浮章标题出「＋批注」，锚文本 = 章标题行。六、测试约束章：清单组件的每一项独立锚定。
- 暗色模式：高亮与徽标配色走主题 CSS 变量（`plan-module.css`），不写死色值。

### 3.4 评审 Tab → 评审工作台

布局三段式（改造现有 `PlanDetailReview.vue`，不换路由）：

1. **动作条**（顶部）：左 = 状态徽标（如「评审中 · rev 5」）；右 = 开始评审 / 评审通过 / 驳回，显隐沿用 `permissions`，不新增动作。
2. **批注总表**（中部）：筛选 chips「全部 / 未解决 / 已解决 / 断链 / 未锚定」（计数实时）；按章节分组的批注卡（与面板**同一组件复用**），操作区为「↧ 去文档定位」等；「断链」计数由前端对当前 body 跑同一重挂派生算法得出。
3. **流转记录**（底部）：SYSTEM 记录时间线，现状不变，独立分区收底。

- **通过确认弹窗**：点「评审通过」时未解决线程数 > 0（含断链未解决）→ Modal「评审通过确认」：正文「仍有 N 条未解决批注。通过后进入执行阶段，批注将保留在文档上。」按钮「取消」/「仍要通过」；计数为 0 时走原有确认流程。后端不拦截。
- **驳回**：照旧必填原因；原因作为无锚点 REVIEW 批注落入「未锚定」分组。

### 3.5 权限规则（新增/变更部分）

| 动作 | 规则 |
|---|---|
| 新增批注 / 回复 | 沿用 COMMENT：项目成员 × DRAFT/REVIEW 阶段 |
| 解决 / 重新打开 | 批注作者本人 或 PLAN_OWNER / PROJECT_OWNER / SYSTEM_ADMIN（与删除同口径），后端校验 |
| 删除 | 口径不变；根批注删除级联删除其回复；回复也可单独删（作者/管理员） |
| 评审通过 | 不新增未解决校验（提醒仅在前端） |

`PlanAccess` 不新增 plan 级动作键；线程级 `canResolve` / `canDelete` 由后端在 CommentView 里按条计算下发，保持「权限矩阵/后端是唯一真相源」。

### 3.6 跨 Tab 定位与深链

- `activeTab` 写入 URL query（`?tab=document|review|report|publish`），切换 Tab 时 `history.replaceState` 同步；详情页加载时解析 query 恢复 Tab（顺带修复「刷新总是落文档 Tab」的旧问题）。
- 「去文档定位」= `?tab=document&comment={id}`：文档 Tab 挂载后滚动定位并闪烁。

## 4. 数据模型

`plan_comments` 加列（SYSTEM 记录不受影响，新列全部可空或有默认值）：

| 列 | 类型 | 说明 |
|---|---|---|
| `parent_id` | BIGINT NULL | NULL=根批注；非空=回复，且只能指向根（对回复回复 → 400） |
| `anchor_line` | INT NULL | 锚定块源 Markdown 行号（0 基） |
| `anchor_text` | VARCHAR(200) NULL | 锚定块纯文本快照（超长截断），重挂依据 |
| `section_title` | VARCHAR(64) NULL | 所属章标题 ∈ CANONICAL_HEADINGS |
| `body_revision` | BIGINT NULL | 创建时文档 revision（审计：意见针对哪版提出） |
| `resolved` | BOOLEAN NOT NULL DEFAULT FALSE | 仅语义上作用于根批注（回复行的该列不读） |
| `resolved_by` / `resolved_at` | VARCHAR(80) / datetime(6) NULL | 解决人与时间 |

索引：`(parent_id)`、`(plan_id, kind, resolved)`。

**迁移**：新增 Flyway `V{n}__plan_comment_anchor.sql`；存量 REVIEW 批注三锚点字段为 NULL → 前端归入「未锚定」分组；不回填、不猜测锚点。

**CommentView 扩展**（前后端契约同步）：`parentId`、`anchorLine`、`anchorText`、`sectionTitle`、`bodyRevision`、`resolved`、`resolvedBy`、`resolvedAt`、`canResolve`、`canDelete`。列表仍为按 `createdAt` 升序的扁平数组（含回复），前端组线程树。

**与版本体系的关系**：批注不属于任何快照，跨 revision 留存；`body_revision` 仅作审计与将来「按版本回看批注」的字段支撑。发布快照冻结的是 body，不含批注；`PUBLISHED` 状态批注整体只读（现状延续）。

## 5. 锚定与重挂（前端核心算法）

### 5.1 块切分与行号映射

1. `utils/plan-markdown.ts` 新增 `splitBlocks(content)`：按 Markdown 块语法将章内容切为 `{startLine, endLine, raw}[]`（段落/ATX 标题/引用/代码栅栏/表格/列表各成块，行号 0 基）。
2. 每章仍整章交 MdPreview 渲染；`onRendered` 后遍历其顶层 DOM 子元素，与 `splitBlocks` 结果**按顺序 + 归一化文本前缀校验**对齐，注入 `data-line`。
3. 细化两档映射：
   - 表格：`<tbody>` 第 i 个 `<tr>` → 块 `startLine` + 表头与分隔行数 + i（表头行不可批注）；
   - 列表：文档序第 i 个 `<li>` → 块内第 i 个列表项首行（嵌套列表按各自源行计数，允许±1 行误差，由 anchorText 兜底）；
   - 六、约束清单项：`ChecklistView` 的项即源行，直接挂 `data-line`。

### 5.2 新增批注

悬浮 `data-line` 块 → 行尾「＋批注」→ 内联输入框 → POST 携带 `{line, text, section}`（text = 块归一化纯文本前 200 字）。

### 5.3 重挂算法（渲染时派生，不回写）

对每个未锚定之外的根批注，在当前文档 blocks 上依序尝试：

1. `anchorLine` 精确命中，且与该块文本归一化相似度 ≥ 0.6 → **正常**；
2. 在 `sectionTitle` 章内模糊匹配（归一化 = 去空白与 Markdown 修饰符，LCS 比率 ≥ 0.6 或前缀/包含）→ **已重挂**（徽标正常显示，卡片引用文本旁加「已自动跟随内容」小标）;
3. 全文范围同规则匹配一遍 → 同上；
4. 均失败 → **断链**：折叠条挂 `sectionTitle` 章顶；若 `sectionTitle` 也对不上（章被改名/文档结构大改），挂文档最顶部。

批注永不因重挂失败而丢失或删除。算法为纯函数（`deriveAnchors(blocks, comments)`），文档 Tab 与评审工作台共用；复杂度 O(章块数 × 批注数)，当前文档规模可忽略。

### 5.4 边界情况

- DRAFT 阶段 owner 编辑保存、precheck 勾选回写、报告/发布系统回填：任何 body 变更后重渲染即自动重挂，无需迁移动作。
- 两名成员并发批注：仅追加行，无冲突。
- 内容改到面目全非：走断链兜底，意见仍可在章顶被看到、被解决。

## 6. 后端设计

API 变更（均挂在现有 `/api/task-plans/{planId}` 下）：

| 端点 | 变更 |
|---|---|
| `POST /comments` | body 增可选 `parentId`、`anchor{line,text,section}`；校验：parentId 必须指向根批注（嵌套 400 `PLAN_COMMENT_NESTED`）；anchor 三字段须同时出现或同时缺省，`section ∈ CANONICAL_HEADINGS`、`line ≥ 0`、`text` 非空截 200；`body_revision` 服务端取当前 plan.revision，不信任客户端 |
| `GET /comments` | 返回扁平列表 + CommentView 新字段；`canResolve`/`canDelete` 按当前用户逐条计算 |
| `POST /comments/{id}/resolve` | body `{resolved: boolean}`；权限 = 作者本人或 PLAN_OWNER/PROJECT_OWNER/SYSTEM_ADMIN，否则 403；仅根批注可操作（对回复 → 400） |
| `DELETE /comments/{id}` | 口径不变；删根级联删回复 |

其他：流转附言（驳回原因）与 SYSTEM 记录不携带 anchor，落「未锚定」/流转分区；批注操作不推进 plan.revision、不触发快照；`PlanWorkflowService` 内批注逻辑抽 `PlanCommentService`（增删查/线程组装/解决），状态机服务只留流转。

## 7. 前端设计

| 单元 | 职责 |
|---|---|
| `utils/plan-markdown.ts` | +`splitBlocks`；CANONICAL_HEADINGS 复用不新增常量 |
| `utils/plan-anchors.ts`（新） | 归一化、相似度、`deriveAnchors(blocks, threads)` 纯函数 |
| `composables/usePlanDoc.ts` | comments 线程化（roots/replies）、`addComment`（带 anchor/parentId）、`resolveComment`、面板开关状态 |
| `PlanDetailDocument.vue` | MdPreview 后处理挂 `data-line`、悬浮按钮/内联输入、徽标高亮渲染、断链条、面板开关与布局（文档列 + 面板列） |
| `PlanCommentPanel.vue`（新） | 右侧面板：分组、折叠、header 计数 |
| `PlanCommentCard.vue`（新） | 面板/工作台共用卡：引用文本、回复列表、回复框、操作区 |
| `PlanCommentComposer.vue`（新） | 内联新增与回复共用输入组件（Ctrl+Enter/Esc） |
| `PlanDetailReview.vue` → 评审工作台 | 三段式重排、筛选 chips、通过确认弹窗、跨 Tab 定位 |
| `router` / `TaskPlanDetail.vue` | `tab`/`comment` query 解析与 replaceState 同步 |
| `plan-module.css` | 高亮/徽标/断链条主题变量（亮暗双色） |

UI 文案（全部中文）：开关「💬 批注 N」；面板 header「批注 ｜ 未解决 N · 已解决 M」；分组「未锚定」「已解决」；内联 placeholder「针对此行添加批注（评审中全员可见）」；回复 placeholder「回复…」；操作「定位」「解决」「重新打开」「删除」；重挂小标「已自动跟随内容」；断链条「⚠ N 条批注的锚点因内容变更失效」；通过弹窗「评审通过确认 / 仍有 N 条未解决批注。通过后进入执行阶段，批注将保留在文档上。/ 取消 / 仍要通过」。

## 8. 测试

**后端单测**：anchor 三字段同现校验与截断；section 白名单；嵌套回复 400；resolve 权限三分支（作者/负责人/无关成员 403）与仅根限制；删根级联；驳回原因批注无 anchor 正常返回；`canResolve/canDelete` 计算正确；批注操作后 plan.revision 不变；SYSTEM 记录不受迁移影响；存量批注（无 anchor）回读兼容。

**前端单测**：`splitBlocks`（段落/表格/列表/代码栅栏/空行边界/行号正确性）；`deriveAnchors`（精确命中/章内重挂/全文重挂/断链/章标题也失效挂顶）；线程组树与计数；面板分组排序（未锚定→章节序→已解决）。

**手测清单（浏览器，亮暗双主题）**：悬浮出按钮与移开隐藏；内联提交后徽标/面板/工作台三处同步；表格行与清单项粒度批注；编辑文档（改一行文字/删一行/整章重写）后三类重挂结果；断链条展开；面板定位闪烁与 Markdown 视图自动切换；工作台筛选与「去文档定位」深链（直接粘贴带 `?tab=document&comment=` URL）；通过弹窗提醒与「仍要通过」；已解决线程回复锁定与重开；权限账号矩阵（成员/只读/负责人）。

## 9. 明确不做（YAGNI）

- 选区级（文字区间）锚定、Markdown 源码视图内批注。
- 八、场景设计章的行级锚定（二期可给场景表行加）。
- 批注的实时推送（WebSocket）：他人在会话内新增批注，靠动作后刷新/重新拉取同步；不做在线状态与未读提醒。
- 批注导出（正文导出/发布快照不含批注）、邮件/站内通知、批注跨计划搜索。
- 按评审轮次归档、批注与版本快照绑定（已被「跨轮次留存」决策否决）。
