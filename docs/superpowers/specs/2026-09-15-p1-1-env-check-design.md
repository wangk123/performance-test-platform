# P1-1 环境检查 · 详细设计

> 2026-09-15 头脑风暴定稿。权威来源：`docs/architecture-and-roadmap.md` P1-1 行、D7/D8（本文档含修订，见 §2）。
> 前置：P0-1 计划文档模块（`PrecheckSettings` / `runPrecheck` / 首执行门禁已交付占位实现）。

## 1. 目标与验收

对"配置有问题"的被测服务器跑预检：输出三态清单（正常 / 已修复 / 需手动处理，附建议与方法）；问题项经用户确认可批量修复且留 diff 可回滚；结果回填计划文档。

核心诉求：**检查项 = 注册表驱动的独立可选项**——后端新增检查项（写一个 Spring Bean），前端页面自动出现新选项，前端零改动。

## 2. 决策记录（含对 roadmap 的口径修订）

| # | 决策 | 内容 |
|---|------|------|
| E1 | 检查项形态 | **代码级注册表**：每个检查项 = 一个实现 `EnvCheckItem` 的 Spring Bean，平台启动自动收集。新增检测项 = 写一个类。不做页面配置式检查项 |
| E2 | 检查项类型 | 只有两类：`LOCAL`（查计划自身就绪：指标/场景/脚本）+ `REMOTE`（SSH 探测目标机）。**人工确认项移出环境检查**（数据就绪/人员到位/接口人明确等回文档「入口准则」人工勾选，不再拦截执行——行为变更） |
| E3 | 检测目标来源 | 解析计划文档「测试资源 → 环境部署信息」表（canonical 为「五、测试资源」；实现按标题含「测试资源」关键词定位，章节号演进免疫）的**地址列**得机器清单；**模块列**做标签匹配（如含 "mysql" 视为 MySQL 机器），决定检查项 × 机器的执行矩阵 |
| E4 | 凭据 | **项目级凭据池 + 计划可覆盖**（覆盖也存凭据表，`plan_id` 非空）；密码 AES-GCM 加密落库，主密钥存平台 `storage/keys`；凭据管理提供「测试连接」 |
| E5 | 探测通道 | 复用 `remote-runner`（平台本机 paramiko）新增 `env-probe` 子命令：支持密码/密钥双认证、执行 shell 片段、SFTP push 脚本。Java 侧 `ProcessBuilder` 调用（与 `RemoteRunnerClient` 同模式）。不要求被测机预装任何运行时；平台保持单 jar 部署（D15 不变） |
| E6 | 修复交互（**D8 修订**） | **不做自动修复**。检查完列问题清单批量勾选修复：低风险默认勾选、中风险手动勾选、高风险勾选时单独弹确认；一键全选含中/高风险弹一次确认；「执行修复」按下后不再弹窗。风险等级只决定提示强度（普通/橙色/红色 + 分级确认文案） |
| E7 | 修复总闸 | 系统配置 `platform.envcheck.fix-enabled`（默认 true）。false = 纯只读探测，前端不渲染勾选框与修复按钮 |
| E8 | 开跑校验 | 缺凭据**不开始检查**：手动触发弹窗列缺失机器 IP + 「去配置」；首执行自动触发报错拦截（列出 IP），可走现有「跳过环境检查」强闯 |
| E9 | 回填（**D7 语义微调**） | 只做两样：系统批注（检查/修复/回滚各一条）+ 文档「测试资源」章节（五、）末尾追加摘要行。**不联动「六、测试约束 → 入口准则」**（环境检查与文档解耦）。D7 三态保留，"已修复"语义 = 用户确认修复完成 |

## 3. 检查项 SPI 与注册表

新增包 `com.yr.perftest.platform.envcheck`（与 `task/plandoc` 平级）。

```java
public interface EnvCheckItem {
    String key();                  // 唯一 key，如 "os.ulimit"——precheck_json items 存的就是它
    String label();                // 显示名：文件句柄数 (ulimit -n)
    String description();          // 说明（前端 tooltip）
    EnvCheckCategory category();   // 分组（也是前端渲染分组）：DOC / OS / JVM / MIDDLEWARE
    EnvCheckKind kind();           // LOCAL / REMOTE
    Set<String> appliesTo();       // 适用模块标签（小写匹配模块列文本）；空 = 所有机器
    int sortOrder();               // 组内排序
}
```

### 3.1 LOCAL 型

```java
public interface LocalCheckItem extends EnvCheckItem {
    LocalVerdict check(PlanContext ctx);   // OK / WARNING(detail, suggestion)，平台进程内判断
}
```

迁移现有 `PlanWorkflowService.runPrecheck` 的 switch-case 三项为 Bean：`doc.metrics-defined`、`doc.scenarios-configured`、`doc.script-bound`（判定逻辑原样搬移）。

### 3.2 REMOTE 型

```java
public interface RemoteCheckItem extends EnvCheckItem {
    ProbeSpec probe(TargetHost host);          // shell 片段（单条命令或 push 脚本）
    ProbeVerdict judge(ProbeOutput output);    // 解析 stdout → OK / WARNING
    Optional<FixSpec> fix(TargetHost host, ProbeOutput output);  // 有修复动作者才提供
}
```

- **探测位置**：`ProbeSpec` 声明执行位置——`TARGET`（经 SSH 通道在目标机执行）或 `PLATFORM`（平台本机执行，如端口拨测 `nc -z -w3 <host> <port>`）；
- **探测输出契约**：shell 片段约定输出**单行 JSON**（如 `{"open_files":1024}`），Java 判定器解析；异常输出视为探测失败（归 WARNING，附原因）。
- `FixSpec` 声明：`risk`（LOW/MEDIUM/HIGH）、`backupScript`（备份原值/文件，返回备份标识）、`applyScript`（修复命令）、`rollbackScript(backupRef)`（回滚）、`summary`（"ulimit 1024 → 65535"）。复查复用 `probe`。

### 3.3 注册表

`EnvCheckRegistry`：构造注入 `List<EnvCheckItem>`，提供 `byKey()` / `grouped()`；启动时校验 key 唯一。对前端暴露 `GET /api/env-check/items`（分组返回元数据 + 该项 `fixable` 标记 + `risk`）——**前端勾选清单完全由此接口驱动**。

### 3.4 存量兼容

`PrecheckSettings` 数据结构不变（`items: List<String>`），语义从"显示文本"升级为"注册表 key"。读取 `precheck_json` 时惰性迁移：旧中文字符串按映射表转 key（`指标已定义→doc.metrics-defined` 等）；**人工项（数据就绪/人员到位/接口人明确/环境就绪）映射为丢弃**（E2：不再拦截）。`DEFAULT_ITEMS` 更新为机器项全集。

## 4. 执行编排

### 4.1 触发时机（沿用现有，不变）

- 评审通过后**首次执行**自动运行（`precheck_executed_at` 为空则跑，`assertExecutionAllowed` 现有 seam）；
- 计划设置**手动触发**；`newRevision` 重置 `precheck_executed_at`；跳过强闯留系统批注。

### 4.2 开跑校验（新增，E8）

1. 解析「环境部署信息」表 → 机器清单（空表 = 无 REMOTE 项可跑，只跑 LOCAL，正常放行）；
2. 逐台解析凭据（计划覆盖 > 项目池）；有缺失 → 不启动：
   - 手动触发：HTTP 400 `ENV_CREDENTIALS_MISSING` + `missingHosts`，前端弹窗 + 「去配置」；
   - 首执行自动触发：`PlanPrecheckFailedException` 同错误码，执行被拦，现有跳过弹窗展示机器清单。

### 4.3 执行矩阵与并发

- 矩阵 = 勾选项 × 机器（`appliesTo` 标签匹配模块列文本，不匹配 = 不适用，结果灰显 `NA` 不参与判定）；
- LOCAL 项平台内直接跑；
- REMOTE 项：**机器间并行**（固定线程池），**单机内检查项串行**；每条命令 10s 超时；
- 执行中问题（连接超时/认证失败/命令超时）统一归 `WARNING`，detail 附原因。

### 4.4 结果状态与整体判定

| 状态 | 含义 |
|------|------|
| `OK` | 通过 |
| `WARNING` | 需处理（detail + suggestion + method + risk） |
| `FIXED` | 用户确认修复完成（复查通过） |
| `NA` | 不适用（灰显，不参与判定） |

整体通过 = 无 `WARNING`（`OK`/`FIXED`/`NA` 均可）；有 `WARNING` 拦执行（可跳过强闯）。

### 4.5 修复流程（E6/E7）

问题清单（`WARNING` 且 `fixable`）→ 勾选（低默认勾 / 中手动 / 高勾选弹确认 / 全选含中高弹一次确认）→「执行修复」逐项：备份 → 修复 → **复查**（失败标回 `WARNING`，不假报 FIXED）→ 记 `env_check_fix`（diff、备份标识、操作人）。回滚按 fix 记录执行 `rollbackScript`，留痕。

## 5. 结果回填（E9）

- 系统批注：检查完成（触发人/机器数/通过/待处理）、修复（项数）、回滚（项数）各一条；
- 文档「四、测试资源」章节末尾追加摘要行（`PlanMarkdownSupport.appendExecutionRecord` 同机制，幂等）：`- 2026-09-15 14:00 环境检查：3 台机器 · 8 项通过 · 2 项待处理（详见环境检查面板）`；
- 不写「六、测试约束」，不勾入口准则。

## 6. 数据模型（V9 迁移）

```sql
CREATE TABLE env_check_credential (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id   BIGINT       NOT NULL,
  plan_id      BIGINT       NULL,               -- 空=项目级；非空=该计划覆盖
  host         VARCHAR(255) NOT NULL,
  ssh_port     INT          NOT NULL DEFAULT 22,
  username     VARCHAR(128) NOT NULL,
  secret_cipher VARCHAR(1024) NOT NULL,          -- AES-GCM 密文（密码或密钥内容）
  auth_type    VARCHAR(20)  NOT NULL,            -- PASSWORD / KEY
  remark       VARCHAR(255) NULL,
  created_by   VARCHAR(64)  NOT NULL,
  created_at   DATETIME     NOT NULL,
  updated_at   DATETIME     NOT NULL,
  KEY idx_proj_host (project_id, host)
);

CREATE TABLE env_check_run (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id      BIGINT       NOT NULL,
  triggered_by VARCHAR(64)  NOT NULL,
  started_at   DATETIME     NOT NULL,
  finished_at  DATETIME     NULL,
  passed       INT          NOT NULL DEFAULT 0,  -- OK+FIXED 数
  warned       INT          NOT NULL DEFAULT 0,  -- WARNING 数
  detail_json  ${lob_type}  NULL                 -- 完整矩阵（H2 测试 clob / 生产 longtext，同 V1 约定）：targets[] + results[]{host,itemKey,state,detail,suggestion,method,risk,fixable}
);

CREATE TABLE env_check_fix (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id        BIGINT       NOT NULL,
  host          VARCHAR(255) NOT NULL,
  item_key      VARCHAR(64)  NOT NULL,
  risk_level    VARCHAR(10)  NOT NULL,
  backup_ref    VARCHAR(512) NOT NULL,           -- 备份标识（目标机备份路径或旧值）
  diff_text     ${lob_type} NULL,               -- 改动差异（同上）（参数：旧→新；文件：统一 diff）
  summary       VARCHAR(512) NULL,
  applied_by    VARCHAR(64)  NOT NULL,
  applied_at    DATETIME     NOT NULL,
  rolled_back_at DATETIME    NULL
);
```

## 7. REST API

| 方法路径 | 说明 |
|----------|------|
| `GET /api/env-check/items` | 检查项注册表（分组 + 元数据 + fixable + risk）。前端动态渲染支柱 |
| `GET/POST /api/projects/{id}/env-check/credentials`、`PUT/DELETE .../credentials/{cid}` | 凭据池 CRUD（密码只写不回显） |
| `POST .../credentials/{cid}/test` | 测试连接 |
| `GET/PUT /api/task-plans/{planId}/precheck-settings` | 现有接口；items 语义 = 注册表 key |
| `POST /api/task-plans/{planId}/env-check/runs` | 触发检查；缺凭据 400 `ENV_CREDENTIALS_MISSING` + missingHosts |
| `GET /api/task-plans/{planId}/env-check/runs` | 运行历史 |
| `GET /api/env-check/runs/{runId}` | 运行详情（矩阵） |
| `POST /api/env-check/runs/{runId}/fixes` | 批量修复（body：host+itemKey 列表；服务端复核勾选合法性与风险） |
| `POST /api/env-check/fixes/{fixId}/rollback` | 回滚 |

**MCP 边界（D12）**：凭据管理、修复、回滚不进 MCP；检查触发/查询工具本期不做。

**权限**：检查项设置、触发检查、修复、回滚 = 项目成员（与计划其他操作同级，沿用 `PlanAccess` 现有口径）；凭据池管理 = 项目成员，密码字段只写不回显。

## 8. 前端交互

1. **计划环境检查设置**（现有 precheck 设置处升级）：清单拉 `GET /api/env-check/items` 动态渲染，按 category 分组、每项独立勾选 + tooltip；计划级凭据覆盖入口。
2. **项目设置 → 环境检查凭据**：列表（host/账号/备注）+ CRUD + 测试连接；密码不回显。
3. **检查结果面板**（计划详情环境检查区）：
   - 概要条：最近运行（时间/触发人/通过率）+「运行检查」+ 历史切换；
   - 结果矩阵：机器 × 检查项，状态徽章（OK 绿 / WARNING 按 risk 着色：低=常规黄、中=橙、高=红 / FIXED 蓝 / NA 灰）；
   - 问题清单：勾选框（低默认勾 / 中手动 / 高勾选弹确认 / 一键全选含中高弹确认）+「执行修复（已选 N 项）」；修复总闸关闭时不渲染；
   - 修复历史：diff 查看 + 「回滚」。
4. **首执行被拦弹窗升级**：列待处理项与缺凭据机器，出口 =「去修复/去配置」+「跳过并执行」（现有留痕逻辑）。

原型：`env-check-prototype.html`（效果图级，light/dark 双主题）。

## 9. 内置检查项（第一期）

| key | 分组 | label | risk | 修复 |
|-----|------|-------|------|------|
| `doc.metrics-defined` | DOC | 指标已定义 | — | 无 |
| `doc.scenarios-configured` | DOC | 场景已配置 | — | 无 |
| `doc.script-bound` | DOC | 脚本已关联 | — | 无 |
| `os.ulimit` | OS | 文件句柄数 (ulimit -n) | MEDIUM | limits.conf |
| `os.kernel-params` | OS | 内核参数 (tw_reuse/somaxconn) | MEDIUM | sysctl |
| `os.disk-usage` | OS | 磁盘空间水位 | LOW | 无（只提示） |
| `os.port-reachability` | OS | 端口可达性（平台本机探测） | — | 无 |
| `jvm.discovery` | JVM | JVM 进程与堆参数 | HIGH | 无（只建议） |
| `middleware.mysql` | MIDDLEWARE | MySQL 关键参数 (max_connections 等) | HIGH | 有（确认后修） |

## 10. 测试策略

- 判定逻辑单测：各检查项 `judge()` 喂样例 stdout → 三态；
- 注册表完整性：key 唯一、存量 `precheck_json` 旧字符串可迁移；
- 编排单测：矩阵生成（模块标签匹配 / NA）、开跑校验（缺凭据拦截）、整体判定；
- 修复链路单测：备份 → 修 → 复查失败回退 → diff → 回滚（probe/fix 走 fake 通道）；
- 冒烟：本地容器 SSH（密码认证）跑全量 REMOTE 项。

## 11. 明确不做

- 检查项页面配置化（自定义命令型）——注册表代码级扩展已满足诉求；
- 自动修复（任何风险级别）——一律用户确认；
- Windows 目标机、Web 探测（HTTP 拨测）——后续按需加检查项；
- 环境检查 MCP 工具；
- 与文档入口准则的任何联动。
