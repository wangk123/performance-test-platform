## 1. Design tokens & Ant theme

- [x] 1.1 定义浅色/深色语义 CSS 变量（canvas/surface/ink/muted/line/accent/accent-soft/status），accent 对齐冷青 `#0B7F8A`
- [x] 1.2 接入界面字体与数据/代码等宽字体及回退栈；更新 `base.css` / `index.css`
- [x] 1.3 扩展 `useTheme`：Ant `ConfigProvider` token/algorithm 与 `data-theme` 同步
- [x] 1.4 增加密度工具类或 data 属性（density-b 默认、density-a 紧凑）
- [x] 1.5 清理 `ant-design.css` 中与旧主色冲突的硬编码，补关键控件换肤

## 2. Dual-rail app shell

- [x] 2.1 重做 `SidebarNav` 为全局窄图标轨（无 01/02 编号，无后端模式说明卡）
- [x] 2.2 实现项目上下文栏（仅 `currentProject` 时显示，模块列表，可折叠）
- [x] 2.3 更新 `TopBar`：面包屑 + 主题/用户；去掉顶栏堆叠式页面主 CTA
- [x] 2.4 调整 `MainLayout` / `layout.css` 栅格：rail + optional context + main
- [x] 2.5 脚本编辑器工作台：收起 context、保留退出路径，统一顶栏气质

## 3. Auth, home, projects (density B)

- [x] 3.1 重做 `AuthScreen` 为 token 一致的聚焦登录面
- [x] 3.2 重做 `HomeView`：指标 + 近期项目 + 工作流入口 + 概览，去掉大 hero/渐变
- [x] 3.3 重做项目列表与项目概览/详情头为标准 page-head + panel 模式
- [x] 3.4 回归：全局导航与进入项目路由行为不变

## 4. Script editor workbench

- [x] 4.1 统一 `script-editor.css` 色板/间距/字体到新 token
- [x] 4.2 对齐分栏、侧栏拖拽与 CodeMirror 主题变量
- [x] 4.3 确认保存/导入等业务操作入口仍可用

## 5. Live monitoring (density A)

- [x] 5.1 为 `ExecutionDetailView` 实时监控区启用 density A
- [x] 5.2 统一汇总条、聚合表、`TaskMonitoringCharts`（TPS/RT）卡片样式
- [x] 5.3 统一服务器/JVM `Target*MetricsPanel` 图表卡片样式与间距
- [x] 5.4 异常样本表与告警区紧凑排版；消除大块空白
- [x] 5.5 项目「监控配置」页保持 density B 标准配置模式

## 6. Seed, settings, nodes

- [x] 6.1 造数 / Seed Capture 相关面板套用标准业务页模式与 token
- [x] 6.2 系统配置、模型配置、执行器配置页统一 page-head + panel
- [x] 6.3 函数库等其余配置型视图跟齐 token（不改交互逻辑）

## 7. Report preview & dark polish

- [x] 7.1 `ReportPreviewPage` / 报告样式对齐设计 token 与字体角色
- [ ] 7.2 全站暗色扫一遍：壳、表格、图表、编辑器、报告可读性
- [ ] 7.3 对照 `design-mockup-redesign.html` 做壳与监控信息结构目视验收
- [x] 7.4 抽查硬编码旧色（如 `#1677ff`）并收敛到 token
