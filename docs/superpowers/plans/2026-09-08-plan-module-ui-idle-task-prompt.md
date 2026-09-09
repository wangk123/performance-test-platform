# 闲时任务提示词：计划模块 UI 样式优化改造

> 用法：将下述提示词整段投给闲时任务/新会话执行。任务依据已定稿的修复计划文档，无需再次方案评审。

---

## 任务

在 `/Users/wangk/Documents/Git/performance-test-platform` 工作区，独立完成「计划模块 UI 样式优化改造」。这是无人值守的闲时任务：**不要向用户提问**，所有决策自行做出并在产出中记录理由；只有遇到破坏数据或不可逆操作的风险时才中止。

改造目标与完整规格已定稿在 `docs/superpowers/plans/2026-09-08-plan-module-ui-restyle.md`（下称"计划文档"），必须原样执行，不要重新设计方案、不要缩减批次。

## 第 0 步：装载上下文（动手前按序读完）

1. `AGENTS.md` —— 工作区规则。Gradle 后端命令必须设置 `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`。
2. 计划文档 —— review 结论、六批修复计划（§4）、组件规格数值（§3.2）、补全决策（§3.3）、风险约束（§5）。
3. `plan-document-prototype.html`（仓库根目录）—— 效果图同族原型的完整 CSS 规格，步骤条/工具条/TOC/文档排版/评审/发布/冲突屏的数值全部以它为准。
4. `design-mockup-redesign.html`、`report-prototype.html` —— 全局视觉语言参照（用户的效果图截图不在仓库内，以上述两个原型文件为可执行规格源）。
5. `frontend/src/constants/design-tokens.ts`、`frontend/src/styles/base.css` —— 设计令牌基座（canvas #F4F6F8 / accent #0B7F8A / IBM Plex / 明暗双主题），新样式一律复用，不得另起色板。
6. 用 Skill 工具加载 `using-superpowers`，按其指引建立本次会话的技能使用习惯；随后创建 TodoWrite 任务清单（0~5 步 + 批次 A~F），全程维护。

## 第 1 步：进入 Superpowers 执行流程

- 加载 `executing-plans` 技能，按计划文档批次 **A→F 顺序**执行（B 依赖 A 的滚动容器，D 依赖 B 的排版预设，E/F 可并行）。
- 不使用 git worktree（前端 dev server 依赖现有 node_modules）；在当前工作区新建分支 `feature/plan-module-ui-restyle` 工作。
- 每批次完成标准：`cd frontend && npm run build`（含 vue-tsc）真实执行且绿 → 按该批次"验收标准"逐条自查 → `git commit`（遵循仓库现有风格：中文、`feat:`/`fix:` 前缀、写清改了什么与为什么；**不要 push**）。

## 第 2 步：批次实施要点（细节一律以计划文档 §3/§4 为准）

- **A 骨架与滚动**：计划详情页自占满高，`.doc-main` 成为唯一文档滚动容器；页头/步骤条/Tabs/工具条/TOC 固定；`scrollTo` 容器内定位 + `scroll-margin-top` 补偿。
- **B 排版统一**：全局 `.plan-md` 预设覆盖 md-editor-v3 预览为原型 `.markdown-body` 规格；Pretty 升级为全文档阅读视图（11 章连续渲染，删 pretty-hint）；doc-toolbar 卡片化 + rev 号；TOC current 态 + scrollspy（Markdown 模式用 heading 锚点）；MdPreview/MdEditor `theme` 接 `useTheme`。
- **C 页头/步骤条/Tabs**：重写 `PlanPhaseStepper`（24px 节点 + 连接线 + 三态色阶 + 状态 pill + `aria-current="step"`）；页头 20px/700 + mono meta；a-tabs 覆盖对齐效果图（作用域限定 `.task-detail`）。
- **D 结构化卡片**：场景卡重设计（编号徽章/类型 chip/执行状态 pill/脚本绑定 chip/操作条右置/执行记录状态色）；清单分组渲染（入口/出口准则，仅在 `plan-markdown.ts` **新增**解析函数）；统一 `.plan-empty` 空态。
- **E 四 Tab + 原生弹窗清零**：评审双栏（自绘时间线 + 元信息侧栏）、报告 verdict token 色卡、发布快照/分享行（链接复制用 `utils/clipboard.ts`）、冲突屏 warn 条 + 图例 + token 化 diff 色；`window.prompt/confirm` 全部替换为 a-modal / `Modal.confirm`（关联脚本做选择器 + 手输兜底）。
- **F 收尾**：列表页阶段语义 badge + 筛选行修正；ScenarioDetail/对话框微调；响应式断点（≤1280px TOC 180px、≤1100px 单列横向 chips）；a11y 项；暗色全量回归。

**硬约束（违反即返工）**：
1. 只动展示层；`usePlanDoc.ts` 与 `plan-markdown.ts` 既有函数不修改（仅可新增函数）。
2. 新样式一律走 `base.css` 既有 token，禁止裸 hex（diff 语义色用 `--danger-soft/--ok-soft`）；暗色主题必须同步可用。
3. 不改变 P0-1/P0-3 的保存链、判等、权限语义；md-editor-v3 样式覆盖集中在 `.plan-md` 一处。
4. 不引入新 UI 依赖库，不改后端行为。

## 第 3 步：运行时验证（加载 `verification-before-completion` 技能）

- `npm run build` 必须真实执行并在报告中贴出结果，不得以"应当通过"替代。
- 尽量起真实栈冒烟：后端按 `README.md` 部署章节启动（默认连 216 MySQL；不可达时按 README 用本机 compose 或 H2 方案）；前端 `npm run dev`；用 `browser-use` 技能走计划文档 §4-F 的冒烟清单（新建计划→整篇/章节编辑→冲突三选一→TOC 跳转→提交评审→批注/通过/驳回→关联脚本→执行→报告 verdict→发布→分享创建/复制/撤销），并截图留证：文档 Pretty / Markdown / 编辑态、步骤条、评审、报告、发布、冲突屏、暗色主题、≤1100px 窄屏。
- 若后端经合理尝试仍无法启动：降级为构建验证 + 以浏览器打开 `plan-document-prototype.html` 做静态对照检查，并在最终报告**显著标注"未经运行时冒烟"**。

## 第 4 步：ui-ux-pro-max 多轮整体 Review（改造完成后执行，至少 3 轮）

加载 `ui-ux-pro-max` 技能，严格按其 Query Contract 用 `search.py` 定向检索（`--domain` 一次一个意图、2~5 个关键词；检索结果须核实后采用，0 结果不得编造、须声明回退为通用准则）：

- **第 1 轮 · 设计系统一致性**：检索 `--domain style` / `color` / `typography`；核对：全部新样式 token 化无裸 hex、字号阶梯（正文 14 / 页题 20 / 按钮与 Tab 13 / 辅助 12 / pill 11）、明暗两主题下文字对比度 ≥4.5:1、无 Ant 预设色残留（green/red/orange/cyan 类）。
- **第 2 轮 · 交互与导航 UX**：检索 `--domain ux`（粘性导航补偿、焦点可见、触达尺寸）；核对：滚动容器唯一（仅 `.doc-main`）、sticky 元素有 `scroll-margin-top` 补偿、TOC scrollspy 与两模式跳转可用、步骤条语义、键盘可达、图标按钮有 aria-label。
- **第 3 轮 · 视觉还原走查**：以第 3 步截图逐组件对照 `plan-document-prototype.html` 与计划文档 §3.2 规格表（步骤条/工具条/TOC/文档排版/场景卡/约束清单/评审/报告/发布/冲突屏），记录偏差。

每轮产出问题清单并分级：**P0 阻断 / P1 应修 / P2 记录**；先修复再复核（重新 build + 重新截图），P0/P1 清零才进入下一轮；单轮修复迭代不超过 3 次，仍不达标则如实记录为遗留项，不得静默放弃。
全部轮次记录写入 `docs/superpowers/plans/2026-09-08-plan-module-ui-restyle-review.md`（每轮：检索结论 → 发现清单 → 修复内容 → 复核证据）。

## 第 5 步：收尾

- 加载 `requesting-code-review`（或 `code-review` 技能）对分支全部变更做 Standards + Spec 审查，确认的问题修复后重新构建。
- 按 `verification-before-completion` 做最终核验：构建证据、冒烟截图、三轮 review 记录齐全。
- 更新 `docs/implementation-log.md`（沿用仓库现有条目风格）；最终 commit。
- 输出总结报告：改动文件清单、六批次完成状态、三轮 review 结论与遗留项、已知限制（如"未经运行时冒烟"）。

## 中止与降级红线

- 同一批次连续 3 次修复仍构建失败或产生回归：回滚该批次 commit，停止并记录原因。
- 禁止：`git push`、删除分支/数据、强推、修改后端业务逻辑、新增依赖、跳过 review 轮次。
