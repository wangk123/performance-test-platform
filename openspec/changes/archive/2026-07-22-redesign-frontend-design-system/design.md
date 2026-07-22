## Context

前端为 Vue 3 + Ant Design Vue 4 + Vue Router。样式集中在 `frontend/src/styles/`（尤其 `pages.css` 体量过大），主题经 `useTheme` 切换 `default`/`dark`，Ant 主题几乎未定制。壳为 `MainLayout` + `SidebarNav` + `TopBar`；进项目后侧栏切换为模块列表。执行实时监控图表在 `ExecutionDetailView`（TPS/RT + 服务器/JVM 面板），项目「监控」页当前偏配置目标。

探索结论与视觉稿：`design-mockup-redesign.html`。约束：不改 API/路由语义/权限/表单字段；不换组件库；不新增业务功能。

## Goals / Non-Goals

**Goals:**

- 建立可复用 Design Token，并映射到 CSS 变量与 Ant Design `ConfigProvider` theme
- 双轨壳落地：全局图标轨始终可见；项目上下文栏仅在项目内出现，可折叠
- 两套密度：B（配置/列表）与 A（监控/执行实时页）；脚本编辑器全屏工作台
- 全站页面族视觉对齐（含鉴权、造数、配置、报告、暗色）
- 实现按「地基 → 页面族」切片，可分 PR 合并

**Non-Goals:**

- 不更换 Ant Design Vue、不重写业务逻辑
- 不新增监控指标采集或后端报表能力
- 不把 `design-mockup-redesign.html` 当作运行时依赖（仅设计对照）
- 不做营销向落地页；品牌通过壳与强调色表达

## Decisions

### 1. 视觉方向：冷青工程工作台

- **选择**：浅色默认；强调色冷青 `#0B7F8A`；界面字 IBM Plex Sans，数据/代码 IBM Plex Mono；平涂 + 细线，少阴影
- **备选**：Ant 默认蓝 / 近黑强调 → 辨识度不足或过冷
- **理由**：与性能测试「测与看」气质一致，并与现状默认蓝拉开距离

### 2. 组件策略：Ant 保留 + 壳自研

- **选择**：`SidebarNav`/`TopBar`/内容区壳自研；表单、表格、弹层、抽屉继续用 Ant，通过 token 换肤
- **备选**：整库替换 → 成本过高；仅 CSS 皮肤不改壳 → 双轨与信息架构落不下去
- **理由**：气质由壳决定，交付效率由 Ant 保证

### 3. 导航：双轨

```
[G 48px] [Context ~220px 可折] [ Main ]
```

- 未进项目：仅 G + Main
- 进项目：G + Context（模块）+ Main
- 编辑器：可收起 Context（必要时弱化 G），保留退出路径
- 去掉 01/02 编号与侧栏「后端接口模式」说明卡

### 4. 密度双档共 token

- **B**：默认行高/间距，用于首页、项目、造数、配置、鉴权
- **A**：同色板更紧 padding/行高、等宽数字优先，用于执行实时监控与高密度扫数区
- 监控页图表结构对齐现网：汇总条 → 聚合表 → TPS/RT → 服务器资源 → JVM → 异常样本（配置目标页仍属 B）

### 5. 主题

- 默认浅色，保留深色切换；深色映射同一语义 token，对比度可用
- Ant `algorithm` 与 CSS `data-theme` 同步（扩展现有 `useTheme`）

### 6. 样式架构

- Token 集中在 `base.css`（或拆 `tokens.css`）；壳在 `layout.css`；页面模式类名收敛，逐步拆分 `pages.css` 巨石
- 页面组件优先用共享 class（page-head / panel / density-*），减少一次性魔法数

### 7. 落地节奏（方案 2）

1. Token + Ant 主题 + 双轨壳  
2. 鉴权 / 首页 / 项目列表与概览  
3. 脚本编辑器  
4. 执行监控（密度 A + 图表区）  
5. 造数 / 配置类  
6. 报告预览 + 暗色精修  

## Risks / Trade-offs

- **[全站 diff 过大]** → 按页面族切片 PR；先合壳与 token，业务页可渐进吃 token  
- **[Ant 换肤不彻底]** → 关键控件在 `ant-design.css` 补丁；禁止页面内硬编码旧蓝 `#1677ff`  
- **[双轨与现路由状态不同步]** → 壳只读现有 `useNavigation` / 路由状态，不改路由表语义  
- **[字体加载失败]** → 提供系统字体回退栈（PingFang SC / Microsoft YaHei）  
- **[监控页「配置」vs「实时」易混]** → 项目监控配置保持 B；实时图表面板在执行详情用 A，文案层级区分  

## Migration Plan

1. 合入 token + 壳后全站应「立刻换气质」，旧页面允许短暂不齐  
2. 逐页去掉 hero-band / 编号导航等旧模式 class  
3. 暗色与报告最后收口  
4. 回滚：恢复 `styles` + layout 组件即可；无数据迁移  

## Open Questions

- 字体：CDN 引入 vs 仓库自托管（实现时选延迟与合规更稳的一种）  
- 项目 Context 默认宽度与折叠断点是否固定 220px / 图标化（实现期按 mockup 微调）  
