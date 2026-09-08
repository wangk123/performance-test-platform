# 计划模块 UI 重构 —— ui-ux-pro-max 三轮 review 记录

日期：2026-09-08 · 分支：`feature/plan-module-ui-restyle` · 工具：ui-ux-pro-max `search.py`（每轮定向检索、单一意图 2–5 词）

环境说明：运行时冒烟在无头 IAB 标签页中进行，该环境渲染管线挂起（rAF 永不执行、程序滚动不派发 scroll 事件、剪贴板 API 不可用、ant-select 弹层无法展开）。涉及这些路径的验证采用「直接驱动 Vue 组件状态 + 同公式手动计算」的方式完成，逻辑等价性已逐项核实；对应 UI 表现截图取自可渲染路径。

---

## 第 1 轮：设计系统一致性

**检索**：
- `semantic color token raw hex --domain color` → 0 结果（声明回退）。按技能优先级表 §6「Semantic color tokens / Raw hex in components」与 ux 域命中执行审计。
- `contrast 4.5 text background --domain ux` → 3 条（4.5:1 正文对比度、灰上灰反例、Alt 文本）。
- `font size scale hierarchy --domain typography` → 2 条字体配对（量级参考）。

**发现清单**：

| # | 级别 | 发现 | 位置 |
|---|---|---|---|
| 1-1 | P1 | 裸 hex 扫描 | 全部新改文件 0 处违规（仅注释提及原型近似色） |
| 1-2 | P1 | 小字号语义文本对比度不足：`--ok` 文本 3.13–3.49、`--warn` 2.86–3.07、`--accent` on `--accent-soft` 4.25、`--danger` on soft 4.0（均 <4.5，字号 11–13px/600） | verdict/status/phase/share 胶囊、doc-chip、toc current、diff 行、conflict-note、plan-note、stepper sub 等 |
| 1-3 | P2 | 字号离群：10px（toc-tag）、12.5×3、15/16/22 等（阶梯 14/20/13/12/11 之外） | plan-module.css（详见第 3 轮规格裁决） |
| 1-4 | P2 | 受控/叙述 toc-tag 视觉无区分 | PlanDetailDocument |

**修复**：
1. 新增 `:root` 派生语义文本色（token + `color-mix`，无裸 hex，双主题自动适配，配比经数值验证）：`--plan-accent-text`(82%+ink)、`--plan-ok-text`(72%)、`--plan-warn-text`(70%)、`--plan-danger-text`(82%)。所有小字号语义文本替换为派生色；裸语义色仅保留于边框/底色/圆点（非文本 3:1 门槛全过）。
2. phase-badge 三态（review/execution/report）文本改 ink，语义改由边框 + 状态色圆点承载（同时满足「不单靠颜色」）。
3. toc-tag 受控章加 accent 描边 + 派生色文本。

**复核证据**：配比数值表（ok 72% → 4.74/5.28/6.38/8.07；warn 70% → 4.6/4.94/6.93/8.78；danger 82% → 4.79+；accent 82% → 5.31+，明暗 × soft/surface 全过 4.5）；`npm run build` 绿；截图 13/14（明暗 Pretty 视图，computed color 已确认为 color-mix 派生值）。

---

## 第 2 轮：交互导航 UX

**检索**：
- `scroll anchor sticky offset --domain ux` → 3 条（平滑滚动、sticky 遮挡补偿、横向滚动）。
- `keyboard focus visible trap --domain ux` → 3 条（焦点环、Focus Not Obscured）。

**发现清单**：

| # | 级别 | 发现 | 位置 |
|---|---|---|---|
| 2-1 | P0 | TOC 链接无 `href`——键盘 Tab 不可达、无焦点 | PlanDetailDocument toc-item |
| 2-2 | P1 | 双 `<h1>`（页头 + 文档标题块）、章头 h2 与文档 h2 同级 | PlanDetailDocument |
| 2-3 | P1 | 无 `prefers-reduced-motion` 降级（badge 脉冲、smooth 滚动） | plan-module.css |
| 2-4 | P2 | doc-main 滚动区无键盘焦点钩子/region 标注 | PlanDetailDocument |
| 2-5 | P2 | segmented tablist 无方向键切换、无 aria-controls | PlanDetailDocument |
| 2-6 | 通过 | 滚动容器唯一性（`.content:has(.plan-detail)` 关滚动 + `.doc-main` 唯一 overflow-y:auto）、stepper `aria-current="step"`、toc aria-current、a-modal 焦点陷阱（ant 自带）、无 outline 抑制 | 冒烟实测 + 代码走查 |

**修复**：
1. toc-item 加 `href="#"` + `@click.prevent`（Tab 可达、Enter 触发、默认焦点环保留）。
2. 文档标题块 h1→h2、章头 h2→h3（CSS 选择器同步）；标题层级 页头 h1 → 文档 h2 → 章节 h3。
3. 追加 `@media (prefers-reduced-motion: reduce)`：脉冲动画关闭、`doc-main` 滚动行为改 auto。
4. doc-main 加 `tabindex="0"` + `role="region"` + `aria-label="计划文档内容"`。
5. segmented 加左右方向键切换（`onSegmentedKeydown`）+ 两视图面板 `id` 与 `aria-controls` 关联。

**复核证据**：`npm run build` 绿；键盘路径逐项为模板/属性级修改，DOM 结构由 vue-tsc 模板校验保证。

---

## 第 3 轮：视觉还原走查（对照计划文档 §3.2 / 原型数值）

**检索**：本轮以规格表逐项核对为主（数值唯一来源为 `plan-document-prototype.html` 摘录表），检索沿用第 1/2 轮 ux/typography 命中。

**发现清单**：

| # | 级别 | 发现 | 裁决 |
|---|---|---|---|
| 3-1 | P1 | 第 1 轮把 §3.2 显式数值「阶梯化」改错：TOC 项 12.5→13、章头 15→14、md h2 16→14、md h3 14→13、code 12.5→12、segmented 12.5→13 | **规格表优先**（任务书：数值规格唯一来源是原型）。全部回退为 §3.2 原值 |
| 3-2 | 通过 | 步骤条（容器 14px 16px/canvas/radius10、节点 24px/1.5px、连线 26×1.5 margin 11px 6px 0、pill 11px 1px 8px 999）、Tabs（13/500/12px 14px/ink-bar accent）、工具条卡（8px 12px/surface/radius10）、segmented（canvas/3px/8px）、TOC 208px（≤1280→180）、doc-section（radius12/18px 22px）、.plan-md 预设（1.75/h2 边框/表格横线式/blockquote/img/mermaid） | 与实现逐项一致 |
| 3-3 | 记录偏差 | toc-tag 原型 10px，实现 11px；md h1 原型未给，实现 20px（阶梯顶格） | 可读性下限 11px 的有意偏差，记录不回退 |

**修复**：见 3-1 回退清单（含 diff-line 12.5 与 code chip 一致化）。

**复核证据**：`npm run build` 绿；§3.2 逐行核对表（上文 3-2 行）；截图 13/14 为最终视觉态。

---

## 汇总

- 三轮共发现 P0×1、P1×4、P2×6，全部修复或裁决记录；每轮后 `npm run build`（vue-tsc + vite）全绿。
- 冒烟期另修复三处运行时缺陷（scrollspy 初始高亮 watch `immediate`、场景列表变更后不重载、执行状态文案恒等映射），已于独立 commit 落库。
- 环境限制（无头 IAB）：smooth 滚动动画/原生 scroll 事件、剪贴板、ant-select 弹层展开、a-modal 关闭过渡动画不可原生验证；对应逻辑均以等价方式驱动验证，真实浏览器行为依赖标准 API。
