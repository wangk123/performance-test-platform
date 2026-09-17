# 脚本版本发布模型（draft / published）· 详细设计

> 2026-09-17 头脑风暴定稿。起因：`parseStatus` 假信号治理（DTO 硬编码 `PARSED`、持久层无状态列、前端死分支）引出脚本生命周期缺失——编辑保存原地覆盖、删除无引用守卫、执行基准随保存漂移。
> 用户已确认决策：钉死具体版本（执行确定性优先）、发布版本号手动指定仅限递增、变更说明必填、draft 永不执行、状态展示中文（代码英文）、版本 UI 半隐形化、衍生需求降级清单（§9）。
> 项目内先例：`aux_scripts`/`aux_script_versions` 两级模型与 `aux_script_bindings` 钉版本绑定；计划模块版本发布（`2026-09-10-plan-version-publish-design.md`）。

## 1. 目标与验收

- 编辑可分多次保存（草稿），确认完成后手动**发布**生成不可变版本快照；执行与场景绑定只认已发布版本，半成品永不被执行。
- 场景绑定钉死具体版本：同一场景任何时刻执行，脚本内容完全一致（`verify_change` 基线/候选对比的前提）。
- 删除有守卫：被场景引用的版本/脚本不可删除，报错列出引用方。
- 清理 `parseStatus` 假信号，文档与实现对齐。

## 2. 现状与断点（调研结论）

1. **无 script 主实体**：`script_versions` 平铺挂 `project_id`，`version_no` 是项目级流水号（`countByProjectId + 1`），同名脚本两次上传是两条无关记录；文档第 4 节设计的 `script`/`script_version` 两级结构未实现。
2. **保存即生效**：`ScriptService.saveScriptContent` 原地覆盖当前版本文件（非原子写），被场景引用的脚本内容随每次保存变化——执行基准漂移。
3. **删除无守卫**：`deleteScript` 物理删除记录 + 文件，被 `task_scenarios.script_version_id` 引用时可删，执行时按 `storedPath` 现场读文件（`DistributedJmeterExecutionRunner` prepare 阶段）→ 悬空失败。
4. **parseStatus 假信号**：`ScriptDefinition.parseStatus` 硬编码 `"PARSED"`（`ScriptService.java:255`）；前端 `ParseStatus` 类型含 `PARSE_FAILED` 死分支；解析失败在上传事务内即抛 `ScriptValidationException` 拒绝，`UPLOADED`/`PARSE_FAILED` 中间态不存在。
5. **执行自包含**：执行启动时 JMX 已复制进 `storage/executions/{projectId}/{planId}/{scenarioId}/{executionId}/`，历史执行详情不依赖源版本文件存活——版本删除的追溯兜底已天然存在。
6. `TaskScenarioService.validateScript` 仅校验存在性 + 项目归属，无状态校验。
7. 前端已有派生可执行性判断 `scriptExecutableStatus()`（无线程组/无请求/缺插件），与本次落库状态正交，保留。

## 3. 决策记录

| # | 决策 | 内容 |
|---|------|------|
| C1 | 状态机 | 版本两态单向：`DRAFT`（可反复保存、不可执行、不可绑定）→ `PUBLISHED`（不可变快照、可执行、可绑定）。无回退转换；不设第三态（ARCHIVED/DISABLED，YAGNI） |
| C2 | 版本号 | 发布时手动指定，校验**大于** `scripts.latest_version_no` 水位；水位只增不减（删除版本不回退），杜绝同号对应两个内容 |
| C3 | 变更说明 | 发布必填 `remark`（多行文本），随版本留存 |
| C4 | 绑定语义 | 场景钉死具体 `script_version_id`（现结构不变）；`validateScript` 增加 `status = PUBLISHED` 校验；换绑为显式操作，提供一键升级到最新 + 旧版本提示徽标（不阻塞）。不引入"跟随最新"alias 双语义 |
| C5 | draft 载体 | 每脚本至多一条 DRAFT，`version_no = 0` 约定 + 唯一索引 `(script_id, version_no)` 保证；惰性创建（编辑器首次保存时建）；编辑只基于最新已发布版本 + 现有草稿延续 |
| C6 | 删除·版本 | DRAFT 随时可删（= 丢弃草稿）；PUBLISHED 当前无场景引用可物理删除，被引用则拒绝并列出场景名。守卫范围 = `task_scenarios.script_version_id` 当前值；历史执行靠执行目录复制件自包含 |
| C7 | 删除·脚本 | 级联删 script + 全部版本 + 存储文件；任一 PUBLISHED 被引用整体拒绝（列出全部引用场景），绝不级联解绑 |
| C8 | 入口语义 | 上传 JMX = 建脚本 + 直接 `PUBLISHED v1`（外部已调好）；平台内新建 = DRAFT 脚手架（空白线程组） |
| C9 | 展示语言 | 状态值仅在代码/API 层用英文 `DRAFT`/`PUBLISHED`；展示层中文映射 `草稿`/`已发布`，集中在 `utils/format.ts`（沿用 `parseStatusText` 模式） |
| C10 | UI 半隐形化 | 主视图只操作脚本（名称 + 最新发布版本 + 使用情况提示）；版本历史收进详情抽屉默认折叠；版本行显示"被场景 A、B 引用"。多版本是数据事实，不是用户日常管理对象 |
| C11 | 衍生需求降级 | 砍"基于指定旧版本编辑"；回滚降级为版本历史"基于此版本创建草稿"便利按钮（复制内容为 DRAFT 再发布，变更说明自动标注回滚来源）；存储不治理（JMX 纯文本量级伪问题），列表折叠 + 分页解决视觉负担 |
| C12 | 并发编辑 | 不做编辑中悲观锁（锁悬挂、需心跳回收）；不做乐观并发（出现实际丢失更新再加 revision 字段，`plan_update` 已有先例）。草稿保存改原子写（tmp + `ATOMIC_MOVE`）为防御性措施 |
| C13 | parseStatus 清理 | 删 `ScriptDefinition.parseStatus` 字段与硬编码；前端删 `ParseStatus` 类型、`parseStatusText`、`api/scripts.ts` 映射、`script-status.ts` 的 parseStatus 分支及徽标引用 |

## 4. 数据模型（V11 迁移）

```sql
CREATE TABLE scripts (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id         BIGINT NOT NULL,
  name               VARCHAR(200) NOT NULL,
  latest_version_no  INT NOT NULL DEFAULT 0,
  created_by         VARCHAR(80) NULL,
  created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uk_script_name UNIQUE (project_id, name)
);

ALTER TABLE script_versions
  ADD COLUMN script_id BIGINT NULL,
  ADD COLUMN status ENUM('DRAFT','PUBLISHED') NOT NULL DEFAULT 'PUBLISHED',
  ADD COLUMN remark VARCHAR(512) NULL,
  ADD COLUMN updated_at DATETIME(6) NULL,
  ADD CONSTRAINT uk_script_version UNIQUE (script_id, version_no),
  ADD CONSTRAINT fk_sv_script FOREIGN KEY (script_id) REFERENCES scripts (id);
```

**存量迁移（随 V11 执行）**：每条现有 `script_versions` 记录生成一个 `scripts` 壳（名称取 `nameOf(original_filename)` 规则，重名追加序号），回填 `script_id`；版本记录 **id 不变**（`task_scenarios.script_version_id` 引用零断裂）；原 `version_no` 原样保留并标 `PUBLISHED`，`latest_version_no` 取该脚本最大号；存量文件不搬移（沿用原 `stored_path`）。

**存储路径（新文件）**：`storage/scripts/{projectId}/s{scriptId}/draft-{filename}`（草稿）与 `storage/scripts/{projectId}/s{scriptId}/v{versionNo}-{filename}`（发布）。文件路径规则：DRAFT 惰性创建时生成 `draft-` 路径新文件（内容复制自最新已发布版本或脚手架渲染）；发布转换时**文件不复制、`stored_path` 不变**（路径前缀是创建时的事实记录，不承担展示语义）；PUBLISHED 文件自此永不重写——执行装配读它无竞态。

## 5. API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/projects/{projectId}/scripts/assets` | **脚本维度**聚合列表：名称、最新发布版本（号/说明/时间/发布人）、`hasDraft`、使用情况（`currentScenarioCount` / `outdatedScenarioCount`）。旧 `/definitions` 端点保留至前端切换后移除 |
| `GET` | `/api/projects/{projectId}/scripts/{scriptId}/versions` | 版本历史（PUBLISHED 倒序 + 顶部 DRAFT），含每版本引用场景名列表 |
| `POST` | `/api/projects/{projectId}/scripts` | 现有两入口分别处理：multipart 上传 JMX = 建脚本 + `PUBLISHED v1`（同名追加为新发布版本）；JSON body 新建空白脚本 = 建脚本 + DRAFT 脚手架 |
| `PUT` | `/api/projects/{projectId}/scripts/{scriptId}/draft` | 保存草稿（body: steps 或 content 两形态，对齐现有两保存端点语义）：无 DRAFT 则惰性创建（内容 = 最新已发布 or 脚手架），有则覆盖；沿用现有校验（JMX 合法 + patch 后步骤可解析） |
| `POST` | `/api/projects/{projectId}/scripts/{scriptId}/publish` | 发布：body `{ versionNo: int, remark: string }` 均必填；校验 `versionNo > latest_version_no` 且为正整数；DRAFT 行转 PUBLISHED、更新水位 |
| `POST` | `/api/projects/{projectId}/scripts/{scriptId}/fork-draft` | C11 回滚便利键：body `{ sourceVersionId: long }`，以指定 PUBLISHED 版本内容创建/覆盖 DRAFT |
| `DELETE` | `/api/projects/{projectId}/scripts/{scriptId}/versions/{versionId}` | 删版本（C6 守卫） |
| `DELETE` | `/api/projects/{projectId}/scripts/{scriptId}` | 删脚本（C7 级联守卫） |

执行装配、场景绑定链路不改（仍按 `scriptVersionId` 读 `storedPath`）；`TaskScenarioService.validateScript` 加 `status = PUBLISHED` 校验，DRAFT 报"脚本版本未发布"。

错误走既有 `ScriptValidationException` → ApiError 约定；守卫拒绝信息中文可辨（"该版本被场景「xxx」引用，无法删除"）。

## 6. 前端交互

- **列表页**：行 = 脚本（名称、最新发布版本号、`已发布` 徽标、`有草稿` 标记、使用情况"N 个场景使用旧版本"提示）；行展开进版本历史抽屉（折叠、分页），版本行 = 号 / 变更说明 / 发布人 / 时间 / 引用场景 / 删除入口（守卫规则启用）+ "基于此版本创建草稿"。
- **编辑器**："保存草稿"与"发布"两动作分离；发布弹窗版本号预填 `latest_version_no + 1` 可改、变更说明必填多行。
- **绑定对话框**：脚本分组下只列 PUBLISHED 版本（显示号 + 变更说明摘要）；对当前绑定非最新的给出"升级到最新"一键操作。
- **状态文案**：`DRAFT → 草稿`、`PUBLISHED → 已发布`，集中 `utils/format.ts`。
- 清理：`ParseStatus` 类型、`parseStatusText`、`script-status.ts` parseStatus 分支、列表徽标引用。

## 7. 后端结构调整

- `script` 包新增 `PersistentScriptRecord` / `PersistentScriptRepository`；`ScriptService` 承接发布 / 草稿 / 守卫逻辑；`ScriptDefinition` 去掉 `parseStatus`，增加 `status`、`remark`、脚本聚合字段。
- `PersistentTaskScenarioRepository` 增加 `existsByScriptVersionId`（守卫查询）与按版本反查场景列表（报错列名用）。
- 草稿保存原子写：tmp 文件 + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`。
- 文档同步：`docs/modules/03-script-management.md` 第 4 节状态机改为本设计（两态 + 守卫语义），接口草案对齐 §5。

## 8. 测试策略

`ScriptApiBehaviorTest` 扩展 + 迁移正确性测试：

1. 发布校验：版本号 ≤ 水位拒绝、非正整数拒绝、remark 缺失拒绝；发布后水位递增。
2. DRAFT 不可绑定 / 不可执行（`validateScript` 拒绝）。
3. 版本删除守卫：被引用拒绝且报错含场景名；解绑后可删；删除最大号版本后水位不回退、新发布仍须 > 水位。
4. 脚本级联删除守卫：任一 PUBLISHED 被引用整体拒绝；无引用时级联删记录 + 文件。
5. 存量迁移：每条旧记录获得脚本壳、id 与 version_no 不变、`task_scenarios` 引用照常解析。
6. 上传 = PUBLISHED v1；新建 = DRAFT；`fork-draft` 内容等于来源版本。
7. parseStatus 清理后 API 响应无该字段，前端类型编译通过。

## 9. 明确不做

- 编辑中悲观锁 / draft 心绪回收（C12）。
- 乐观并发 revision（出现实际丢失更新再加）。
- 异步解析（UPLOADED/PARSE_FAILED 中间态，解析是同步毫秒级）。
- ARCHIVED/DISABLED 第三态、脚本级状态字段。
- "跟随最新" alias 双语义绑定。
- 基于指定旧版本编辑（回滚按钮已覆盖）。
- 存储治理 / 版本自动轮转。
