# 环境检查信息架构重构 · 详细设计

> 2026-09-16 头脑风暴定稿。前置：P1-1 环境检查（`2026-09-15-p1-1-env-check-design.md` E1–E9 决策全部不变，本次是 UI 信息架构层重构，不碰执行链路）。
> 原型：`prototype-assets/env-check/env-check-restructure-prototype.html` + `prototype-assets/env-check/env-check-restructure-light.png` / `prototype-assets/env-check/env-check-restructure-dark.png`（仓库根目录，双主题）。

## 1. 目标与验收

解决两个信息架构问题：

1. **凭据池错位**：凭据卡嵌在「计划详情 → 环境检查」标签里却标题自称"项目设置"（API 本就是项目级 `/api/projects/{id}/env-check/credentials`），语义与位置不符。
2. **检查目标不可见**：目标机清单只在点「执行」时才从文档解析（或缺凭据拦截弹窗里闪现），执行前无任何入口能看到"检查谁、检查什么"的完整清单。

验收：

- 项目详情新增「环境检查」tab 管理凭据池，项目内所有计划共用；
- 计划侧「环境检查」标签首卡为「检查总览」：检查目标（文档单一事实源、只读、凭据三态）与检查项勾选同屏；
- 无 DB 迁移、无破坏性 API 变更；P1-1 的 E1–E9（注册表、探测通道、修复确认制、缺凭据拦截等）行为零变化。

## 2. 决策记录

| # | 决策 | 内容 |
|---|------|------|
| R1 | 凭据池位置 | 项目详情新增 tab「环境检查」（`/projects/:projectId/env-check`，侧边栏位于「监控」之后），内容为「SSH 凭据池」卡（现 `EnvCheckCredentialCard` 迁移改造，去 planId 依赖）。API 与数据模型不动 |
| R2 | 目标清单能力 | 计划侧**只读预览**，文档「五、测试资源 → 环境部署信息」为单一事实源（E3 不变）。平台侧不提供目标机增删、不做"本次不检"排除标记 |
| R3 | 检查总览形态 | 计划侧首卡「检查总览」：左=检查目标表，右=检查项分组勾选（现设置卡勾选区并入）；原凭据大卡压缩为一条凭据状态条 |
| R4 | 适用项数口径 | 目标表「适用远程检查项」= 勾选的远程项中 `appliesTo` 匹配该机模块列文本的数量（复用 `TargetHost.matches`）。**后端计算**，避免前端复刻匹配规则漂移 |
| R5 | 计划级覆盖 | 保留（E4 不变）：计划侧状态条「计划级覆盖」弹窗编辑（复用凭据表单，携 planId）；项目池卡不再承载覆盖编辑，「生效范围」列只读显示覆盖标记 |

## 3. 页面与导航（前端）

### 3.1 项目级「环境检查」tab（新增）

- `useNavigation` 项目 tab 枚举 + `ProjectDetail.vue` 分发 + 路由 `projects/:projectId/env-check`（参照现有 `project-monitoring` 模式）。
- 新组件 `ProjectEnvCheckView`：渲染迁入的凭据池卡，标题「SSH 凭据池」，副题保留"密码加密存储、只写不回显"。
- 凭据卡改造：去掉「仅当前计划生效」勾选（项目上下文无 planId）；「生效范围」列渲染项目池/计划覆盖标记（按现有凭据列表 API 返回的 scope 信息，若现响应无 scope 字段则补一个只读派生字段，不改表结构）。
- 表单抽共用组件 `CredentialFormModal`：项目池新增/编辑与计划侧覆盖弹窗共用（覆盖模式携 planId）。

### 3.2 计划侧「环境检查」标签（改造）

自上而下：

1. **`EnvCheckOverviewCard`（新，首卡）**
   - 上半·检查目标：`地址 | 模块 | 凭据 | 适用远程检查项`。凭据三态：✓ 已覆盖（项目池）/ 🔒 计划覆盖 / ✗ 缺失（红）。来源提示行："实时解析自计划文档「五、测试资源 → 环境部署信息」（N 台），修改部署表后刷新生效" + 「跳到文档章节」锚点链接；文档无部署表时空态引导补文档（不给平台侧编辑，R2）。
   - 下半·检查项：现 `EnvCheckSettingsCard` 的分组勾选区（LOCAL/REMOTE 分组、风险等级 chip）原样并入；「启用环境检查」开关与「保存设置」保留在本卡卡头。
2. **`EnvCheckCredBar`（新，替代原凭据大卡）**：一行摘要"目标机 N 台 · 凭据就绪 M 台"；缺失时红字点名 host + 提示"勾选远程检查项将被拦截"（与 E8 拦截文案一致）；右侧「🔒 计划级覆盖」（弹窗）+「去项目配置凭据 ↗」（路由跳项目 tab）。
3. **`EnvCheckResultPanel`**：不动。

`EnvCheckCredentialCard` 从 `EnvCheckTab` 移除，其表单逻辑归宿 `CredentialFormModal`。

## 4. REST API

新增一个只读端点，其余全部复用：

```
GET /api/task-plans/{planId}/env-check/targets
```

- 鉴权：与现有 `POST /api/task-plans/{planId}/env-check/runs` 相同（PlanActorRole）。
- 响应：

```json
{
  "targets": [
    { "host": "10.190.123.164", "module": "nginx-web、fast-gateway…",
      "credential": "POOL", "applicableRemoteItems": 5 }
  ],
  "total": 7,
  "ready": 6,
  "missing": ["10.190.123.20"]
}
```

- `credential` ∈ `POOL | PLAN_OVERRIDE | MISSING`：`EnvTargetParser.parse(body)` 得目标 → 逐台 `EnvCheckCredentialService.resolve(projectId, planId, host)`，按命中的记录 `planId` 是否非空区分 POOL/PLAN_OVERRIDE，空为 MISSING。
- `applicableRemoteItems`：`PrecheckSettings.migrate(settingsOf(plan))` 勾选的 REMOTE 项中 `TargetHost.matches(target, item.appliesTo())` 通过的数目（R4；未启用环境检查时按默认勾选集计）。
- 文档无部署表：`targets: []`、`total = 0`（不报错）。
- 实现落点：`EnvironmentCheckRunner` 新公开方法（复用其既有依赖：parser、credentials、settings 读取），`EnvCheckController` 加 GET 端点。

## 5. 兼容性

- 无 DB 迁移；无 API 破坏（纯新增端点 + 凭据列表响应可能的只读派生字段）。
- 执行编排（`run`/`runRemote`）、E8 缺凭据拦截、结果矩阵、修复/回滚：零改动。
- 计划级覆盖凭据数据（`plan_id` 非空记录）读取路径不变，仅编辑入口从池卡换到计划侧弹窗。
- `EnvCheckTab` 现有 props/事件契约对 `TaskPlanDetail` 保持不变。

## 6. 测试策略

- 后端单测：targets 端点——解析与三态合成（池命中/计划覆盖命中/缺失）、applicable 计数（含 `appliesTo` 标签项如 mysql 只匹配模块列含 mysql 的机器）、空部署表、鉴权（非成员 403）。
- 前端：`EnvCheckOverviewCard` spec（三态渲染、缺失点名、空态引导、勾选变化联动 applicable 数需刷新 targets）；路由 tab 集成冒烟（项目 tab 可达、计划侧跳转按钮路由正确）。
- 回归：环境检查全链路手动回归（E8 拦截 → 项目 tab 配凭据 → 计划侧状态条转绿 → 执行 → 矩阵 → 修复回滚）。

## 7. 明确不做

- 平台侧目标机增删/临时排除（R2，文档单一事实源）。
- 检查执行链路、检查项注册表、凭据加密存储的任何改动。
- 项目 tab 不放检查历史（沿用计划侧 runs 列表现状）。
