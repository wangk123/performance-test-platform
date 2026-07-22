## Context

前置变更 `redesign-frontend-design-system` 已提供：冷青 token、浅/深主题、密度 A/B、双轨壳、入口页改造。剩余缺口集中在项目工作区子模块与弹层，以及暗色/效果图验收。

约束：不改业务行为；复用已有 `page-head` / `panel` / density 模式；不新增组件库。

## Goals / Non-Goals

**Goals:**

- 脚本、任务计划、造数、函数库、成员页视觉与入口页一致
- Dialog/Drawer 与编辑器内部去掉旧装饰（Module 0x、无意义 eyebrow 堆叠）
- 暗色可读；对照 mockup 完成目视门禁

**Non-Goals:**

- 不重做双轨壳或 token 体系
- 不改造数/计划/脚本业务工作流
- 不新增功能

## Decisions

### 1. 复用既有模式，不发明第三套布局

- 列表/配置：density B + page-head + panel + 单行 toolbar
- 执行实时区：保持 density A（已在前置变更启用）
- 去掉装饰性编号与英文 eyebrow；若需分区标题用中文 `h2`/`panel-header`

### 2. 落地顺序（高频 → 长尾）

1. ScriptWorkspace + 编辑器内部  
2. TaskPlan 列表/详情/场景 + Dialog  
3. Seed 工厂全套  
4. 函数库 / 成员  
5. 其余 Dialog/Drawer 扫尾  
6. 暗色 + mockup 目视验收  

### 3. CSS 策略

- 优先改 Vue 结构 class，少写一次性样式
- 共享样式进 `pages.css` / 已有模块 css；禁止再硬编码旧蓝

## Risks / Trade-offs

- **[Seed 面板文件多]** → 先统一外壳与表头，再扫细节  
- **[Dialog 行为回归]** → 只改 class/间距/标题，不改表单字段与提交逻辑  
- **[与前置变更并行]** → 以当前仓库 token/壳为准，不 fork 第二套变量  

## Migration Plan

1. 按模块切片合入，每片可单独验收  
2. 回滚仅涉及对应 Vue/CSS；无数据迁移  

## Open Questions

- 成员权限页若嵌在 ProjectDetail 内，是否单独 page-head 或沿用项目壳（实现时按现结构最小改动）  
