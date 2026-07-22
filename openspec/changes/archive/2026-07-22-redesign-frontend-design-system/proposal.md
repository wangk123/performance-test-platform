## Why

前端此前以功能交付为主，视觉停留在 Ant Design 默认中台样貌：色板/间距/层级不统一，导航用编号装饰、大面积 hero 与卡片堆叠，监控与配置页共用同一密度，产品辨识度弱、扫读效率差。需要重建设计系统与应用壳，在不改变业务行为的前提下统一全站呈现。

## What Changes

- 建立冷青工程工作台视觉语言：Design Token（色、字、间距、圆角、状态色）+ 浅色默认 / 深色可切换
- 自研双轨应用壳：全局窄图标轨 + 进项目后上下文栏；TopBar 仅面包屑与用户/主题；去掉编号导航与侧栏说明卡
- 页面模式分层：日常业务页密度 B；监控/执行实时页密度 A（对齐现有 TPS/RT/服务器/JVM 图表面板结构）；脚本编辑器为工作台全屏模式
- 深度换肤 Ant Design Vue（ConfigProvider + CSS），不替换组件库；业务 API、路由、权限、表单字段不变
- 覆盖全站页面族：鉴权、首页、项目、脚本编辑器、执行监控、造数、配置、报告预览；暗色主题同期打磨
- 视觉参考稿：`design-mockup-redesign.html`（非运行时代码，实现时对齐其壳与监控信息结构）

## Capabilities

### New Capabilities

- `frontend-design-tokens`：设计令牌、主题（浅/深）、密度档位（A/B）、字体与 Ant Design 主题映射
- `frontend-app-shell`：双轨导航壳、TopBar、内容区布局规则、全屏工作台（编辑器）行为
- `frontend-page-patterns`：列表/详情、监控（含实时图表区）、编辑器、鉴权、报告等页面呈现契约

### Modified Capabilities

- （无）业务需求规格不变更；本次为前端呈现层重建。报告/执行等能力的功能要求保持不变，仅视觉对齐新模式。

## Impact

- **代码**：`frontend/src/styles/*`、`useTheme`、`MainLayout` / `SidebarNav` / `TopBar`、各 `views` 与 `components/views`、执行监控与报告预览相关样式；可能拆分 oversized `pages.css`
- **依赖**：继续使用 `ant-design-vue`；新增字体（IBM Plex Sans/Mono，或等价 CDN/自托管），不更换组件库
- **API / 后端**：无接口变更
- **风险**：全站样式面广，需按「token+壳 → 页面族」切片落地，避免单 PR 大爆炸
