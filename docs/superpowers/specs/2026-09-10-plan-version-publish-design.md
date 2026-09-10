# 计划文档版本发布与修订记录 设计

- 日期：2026-09-10
- 状态：已与需求方对齐（问卷四项 + 弹窗版本号规则补充）
- 范围：任务计划（task-plans）文档的版本体系；不改报告发布、不改 revision 乐观锁

## 1. 背景与目标

现状：`task_plans.revision` 随每次文档保存自增，此前在界面作为「版本号」徽标展示（现已从界面移除），语义混淆。

目标：建立真正的计划版本体系——

- 版本号关联修订记录；
- 发布时**手动设置版本号与变更内容**，**自动关联修订人**；
- 该「发布」贯穿计划全生命周期（策略→评审→执行→报告→发布各阶段的任意变更节点都可发版），与测试报告发布无关；
- 编辑保存不再产生用户可见的「版本号 +1」。

## 2. 概念模型

**PlanVersion（计划版本）**，一次发布动作产生或覆盖一条记录：

| 字段 | 说明 |
|---|---|
| `version_no` | 版本号，用户手输，同计划内唯一 |
| `change_note` | 变更内容，用户手填，必填 |
| `created_by` | 首次发布人（自动，覆盖发布不变） |
| `author` | 最新修订人（自动；覆盖发布时更新为覆盖人） |
| `snapshot_body` | 发布时刻整篇 markdown 冻结快照 |
| `plan_phase` | 发布时计划所处阶段（DRAFT/REVIEW/…，审计展示用） |
| `plan_revision` | 发布时内部 revision 号（审计用） |
| `created_at` / `updated_at` | 首次发布时间 / 最近覆盖时间 |

边界约定：

- **内部 revision 体系不动**：继续作为保存冲突检测的乐观锁令牌，界面不再展示。
- **与报告发布快照（`plan_publish_snapshots`）完全解耦**：报告发布流程不改。
- 快照正文以**服务端 DB 当前 body** 为准，不信任客户端提交内容。

## 3. 发布版本交互

### 3.1 入口

- 文档工具条「发布版本」按钮（Pretty/Markdown 切换右侧，替代原 revision 徽标位置）；
- 「版本」Tab 顶部「发布版本」按钮。
- 可见性：项目成员即可（服务端拒绝非成员）；任意阶段可用。

### 3.2 发布弹窗

单个版本号输入框 + 变更内容 textarea：

- **版本号默认值 = 最新版本号**；无任何历史版本时留空（placeholder「如：V1.0」）；必填。
- **变更内容**必填。
- 不做 +0.1 递增预填；发新版本由用户手动改号。

### 3.3 提交规则（按序校验）

| # | 条件 | 行为 |
|---|---|---|
| 1 | 版本号 **等于最新版本号** | 前端弹确认告警「版本号未变，将覆盖当前版本 vX 的快照与修订记录」；确认后**覆盖最新版本**：更新 snapshot_body / change_note / author / plan_phase / plan_revision / updated_at（created_by 不变） |
| 2 | 版本号与最新号**均可解析为数值版本**（`v?数字[.数字]*`，忽略大小写）且**低于**最新（逐段数值比较） | **拒绝提交** |
| 3 | 版本号已被**非最新**历史版本占用（含不可解析的自由文本重号） | **拒绝提交**（覆盖仅限最新版本） |
| 4 | 其余 | 创建新版本记录 |

说明：自由文本版本号（如「终稿」）无大小语义，只能「等于最新 → 覆盖」或「全新 → 唯一性校验」，规则 2 不适用。

## 4. 版本 Tab（修订记录展示）

位置：计划详情 Tabs 的第五个，**文档 / 评审 / 报告 / 发布 / 版本**。

- **未发布变更提示**：当前正文 ≠ 最新版本快照时，Tab 顶部提示条「有未发布的变更」；无版本或已一致时不显示。
- **列表**按 `created_at` 倒序（版本号为手输字符串，字典序不可靠）。列：版本号、变更内容、修订人（author）、时间（首次发布时间；覆盖过的显示「修订于 updated_at」）、发版时阶段。
- **行操作**：
  - **查看全文**：只读弹窗预览该版本快照（复用 `.plan-md` 排版）；
  - **回滚到此版本**：确认弹窗 → 以快照内容走现有「保存全文」链路（保留 revision 冲突保护）；回滚本身**不产生版本记录**，属于编辑，变更随下次发布入库。
- **空态**：暂无版本 + 「发布第一个版本」引导。

## 5. 后端设计

- **Flyway** 新表：

```sql
CREATE TABLE plan_versions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  version_no VARCHAR(32) NOT NULL,
  change_note VARCHAR(1000) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  author VARCHAR(64) NOT NULL,
  snapshot_body LONGTEXT NOT NULL,
  plan_phase VARCHAR(20) NOT NULL,
  plan_revision INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_plan_version (plan_id, version_no)
);
```

- **持久层/服务**：跟随 `plan_publish_snapshots` 的既有实现模式（entity + repository + service）。校验逻辑集中在 service：
  - 必填校验（versionNo / changeNote）；
  - 规则 3.3 的 1–4（覆盖仅限最新、数值低于最新拒绝、重号拒绝）；
  - 快照取服务端当前 body；author/created_by 取当前登录人；
  - 计划不存在/无 body 的防御处理。
- **REST**（挂在现有 task-plans 控制器族，鉴权沿用现有计划权限模型）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/task-plans/{id}/versions` | 版本列表（不含 snapshot_body） |
| GET | `/api/task-plans/{id}/versions/{versionId}` | 版本详情（含 snapshot_body） |
| POST | `/api/task-plans/{id}/versions` | 发布：`{ versionNo, changeNote }`；等于最新即覆盖（幂等语义由服务端判定） |

- 权限：发布/回滚同口径：发布=项目成员（任意阶段）；回滚=复用文档保存接口的既有编辑校验；列表/详情=计划可见（READ）即可。
- 错误码沿用现有风格（校验失败/冲突类），前端按 code 给中文提示。

## 6. 前端设计

- `api/plan-doc.ts` + `types` 增补版本接口与类型。
- 新组件 `PlanDetailVersions.vue`（版本 Tab 内容：提示条 + 列表 + 空态 + 发布入口）。
- 新组件 `PublishVersionModal.vue`（版本号 + 变更内容 + 覆盖确认告警逻辑）。
- `TaskPlanDetail.vue` 注册第五个 Tab；文档工具条加「发布版本」按钮（弹窗状态上提至 TaskPlanDetail，或经 composable 共享，实现计划定）。
- 「版本历史」「发布版本」在评审/报告/发布 Tab 间共享同一数据源，切换 Tab 后刷新。
- 查看全文复用 `.plan-md` 预览样式；回滚走 `usePlanDoc.saveDocument` 既有链路与冲突弹窗。

## 7. 测试

- 后端单测：发布新版本；版本号低于最新被拒；重号（非最新）被拒；覆盖最新版本（快照/author/updated_at 更新、created_by 不变）；必填校验；列表排序；权限（非项目成员不可发布/读取）。
- 前端：`npm run build`（vue-tsc）；浏览器冒烟：首版发布 → 小修覆盖发布（告警确认）→ 新版本发布 → 未发布变更提示条 → 查看全文 → 回滚 → 回滚后提示条复现。

## 8. 明确不做（YAGNI）

- MCP 版本工具（后续按需）；
- 版本间 diff 对比视图（二期预留：快照已存，随时可加）；
- 发布审批流 / 发版后锁文档；
- 历史 revision 数据回填映射为版本；
- 版本删除。

2026-09-10 终审修订：发布权限口径定为项目成员即可（任意阶段），消除与 PlanAccess.EDIT 阶段门控的互斥。
