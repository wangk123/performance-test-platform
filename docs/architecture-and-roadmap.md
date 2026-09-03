# 平台目标架构与待完成计划

> 生成于 2026-08-28 头脑风暴（grill 会话）。本文档是**未来目标**与**待办大纲**的唯一权威来源，实现时按项拆 Issue。
> 旧需求见 `docs/requirements-spec.md`（其"版本路线图"章节已同步更新）。

## 1. 定位与范围

- **定位**：企业团队内部落地推广级别的性能测试平台（当前处于开源吸粉阶段，无真实用户；"吸粉"不是目标）。
- **范围**：聚焦性能测试本身的完整闭环——**需求 → 环境检查 → 测试执行 → 缺陷提交/分析/修复 → 复测 → 生成报告 → 发布 → 邮件通知 → 迭代间对比**。
- **边界**：不要求所有环节都堆在平台上。缺陷管理通过 Jira 等外部平台承载（平台只做台账+同步），邮件通过 MCP 邮件服务发送（平台只做模板与清单配置）。
- **运行环境**：仅在测试环境内部使用，不考虑单点故障、高可用、多租户售卖；"分布式"仅指压测执行多节点加压。

## 2. 头脑风暴决策记录

> 这些是本次梳理定死的口径，实现时照此执行，变更需回到本文档更新。

| # | 决策 | 内容 |
|---|------|------|
| D1 | 计划文档形态 | 压测计划 = **结构化字段**（目标指标/验收标准/环境/关联场景/结论）+ **Markdown 正文**；两层并存。（2026-09-02 修订：**一稿走到头**——同一份文档经历计划→执行回填→报告→发布；结构化模块 = 基本信息/测试目的与核心指标清单（指标挂交易）/测试范围（交易清单+配比）/测试资源（人员+环境部署信息表+执行节点）/测试约束（入口·出口准则清单）/结论（达成表+风险建议+总体结论），见 P0-1 设计） |
| D2 | 计划状态机 | 草稿 → 待评审 → 评审中（批注）→ 已通过 → 执行中 → **已发布（终态）**；评审人 = 项目成员；**发布权限 = 计划负责人/项目管理员**；分享链接带有效期、可撤销。（2026-09-02 修订：改为**二级状态**——阶段 = 草稿/评审/执行/报告/发布；评审子状态 = 待评审/评审中/评审通过；执行子状态 = 待执行/执行中/执行完成；报告子状态 = 待生成/生成中/已生成；发布 = 已发布终态。**环境检查不是状态**，是计划可选项（启用+清单，评审通过后首次执行时自动运行、可手动跳过）。评审通过 = 任意项目成员可点（含自审，记录审批人）；发布前置 = 报告已生成 + 总体结论确认 + 无活跃执行，见 P0-1 设计） |
| D3 | AI 生成计划 | 平台提供 **MCP 接口**（模板/创建/读取/更新/查询）+ 模板（内置通用 + 项目自定义 Markdown 占位符）；**本地 Agent 通过仓库内 `skills/perf-plan/` 梳理需求、生成、同步**；平台内与本地可交替编辑 |
| D4 | 文档自动回填 | 执行摘要、环境检查结果、缺陷清单**自动追加**；最终结论**半自动**（判等自动算，发布前人工确认） |
| D5 | 编辑冲突 | revision 号 + **双栏差异 + 三选一**（保留平台版/采纳本地版/手改）；MCP 更新冲突返回 409 + 差异文本；**不做自动覆盖、不做行级合并** |
| D6 | 验收判等 | 验收标准实体挂在计划上（目标 TPS/P95/P99/错误率/并发峰值/容量），报告自动判定 + 人工确认 |
| D7 | 环境检查 | **独立预检任务**，平台本机优先，**SSH 连目标服务器探测**；SSH 做不了的 push 脚本到目标机执行；结果三态：本身 OK / 有问题已修复 / 需手动修复（附建议与方法）。（2026-09-02 修订：环境检查是**测试前的执行动作**——挂计划执行设置（非文档内容、不进评审），勾选启用 + 配置检测清单，评审通过后**首次执行时自动运行**（挂现有执行预检 seam），可手动跳过；探测/修复能力仍由 P1-1 交付） |
| D8 | 自动修复边界 | 三级：**低风险默认自动修**（连接池/超时等应用配置）/ 中风险需勾选（ulimit/内核参数）/ 只建议（DB/中间件）；所有修复留 diff 可回滚，全局开关；**检查项插件式扩展，平台默认只做通用低风险排查，项目特性自行扩展自担风险** |
| D9 | 缺陷闭环 | 平台内置**缺陷台账**（记录+关联执行/样本/报告），同步到 **Jira 为第一个 adapter**（可插拔 gateway，后续按需扩展）；Jira 凭据密文存储、字段固定映射；状态回拉 = 手动刷新 + 定时轮询（不做 webhook）；**手动推送为主 + 可配自动推送** |
| D10 | 通知 | 发送动作走 **MCP `sendEmail`**；平台做**模板配置 + 抄送清单配置**，配置在项目（默认）+ 计划（覆盖）两级；抄送默认按项目成员角色，**发送前确认可临时修改**；时机 = 执行完成/失败、计划发布；模板变量先做 5 个核心（执行摘要/报告链接/判定结果等） |
| D11 | 迭代对比 | **发布版本快照为主**（计划"已发布"固化快照，下次同计划执行自动对比上次快照）+ **场景手动基线兜底**；快照内容 = 场景列表 + 脚本版本 + 线程组配置 + 结果摘要；对比内容 = TPS/P95/P99/错误率 + 资源指标 + 差异判定；对比时自动检测被测侧代码变更（复用现有 verification 模块） |
| D12 | MCP 全功能化 | 大部分功能 MCP 化，MCP 与 REST **共用 facade seam**；**随功能落地逐个暴露工具**，不单独立"全量 MCP 映射"项目；白名单不进 MCP：登录/密钥管理、发布终态、删除类、自动修复 |
| D13 | 架构拆分 | 单仓多模块：`platform-core` / `platform-api` / `platform-worker`（详见 §3）；worker 架构图按**可独立进程**画，落地先同进程、留独立启动类 |
| D14 | 数据层 | 主库 + 执行明细**一次性统一迁 MySQL**；H2 仅保留给本地无依赖开发（profile） |
| D15 | 部署 | **后端托管前端**（前端 build 产物打进 api jar，单进程起全部）+ `scripts/start.sh` + 部署文档；Dockerfile 顺手加一个不承诺维护；CI 排最后 |
| D16 | 分布式现状 | 多节点分布式压测**已支持**（1 controller + N workers，前后端链路完整）；缺的是**容量调度/自动分片**（按目标并发自动选节点/分配负载/容量感知），列为远期 |
| D17 | JSR223 组件 | 编辑器支持 JSR223 前后置处理器，语言先支持 **Groovy**（后续按需加 JS）；加解密**密钥/盐不进脚本明文**，统一走脚本参数化引用（`${__P(...)}` / 用户参数 / CSV），平台不落盘明文密钥、不做平台级 vault |
| D18 | MCP 目录页 | MCP 工具列表页**只读展示 + 复制接入配置**：工具启停与可见性由注册表（stage + write scope）决定，页面不做运行时注册；说明文案与使用示例作为工具元数据随平台发布维护 |

## 3. 目标架构

### 3.1 全景图

```mermaid
flowchart TB
    subgraph ACCESS["接入层"]
        direction LR
        WEB["Web UI<br/>Vue3 单页应用"]
        AGENT["本地 Agent<br/>Claude Code / DSH · skills/perf-plan"]
    end

    subgraph PLATFORM["platform 平台本体 · 单仓三模块 · 单进程部署"]
        direction TB
        API["platform-api · 对外接口层<br/>REST · /api/agent/** · MCP Server<br/>Facade · 安全 · 通知编排 · 前端静态托管"]
        CORE["platform-core · 领域内核<br/>计划 / 场景 / 执行 / 缺陷 / 报告<br/>确定性分析 · 证据链 · LLM 网关<br/>Prometheus 采集 · 造数工厂 · 审计治理"]
        WORKER["platform-worker · 执行引擎<br/>执行控制 seam · 脚本装配 · 分布式 Runner<br/>JMeter 运行时 · 环境检查 · 辅助脚本"]
        API --> CORE
        API -->|"触发执行 / 预检"| WORKER
        WORKER --> CORE
    end

    subgraph DATA["数据层"]
        DB[("MySQL<br/>主库 + 执行明细")]
    end

    subgraph EXTERNAL["外部系统"]
        direction LR
        NODES["执行节点集群<br/>SSH · 1 Controller + N Workers"]
        PROM["被测服务<br/>Prometheus"]
        JIRA["Jira 缺陷平台"]
        MAIL["MCP 邮件服务"]
        LLM["LLM Providers"]
    end

    WEB -->|"REST /api/**"| API
    AGENT -->|"MCP 工具"| API
    CORE --> DB
    WORKER -->|"SSH 编排 / 结果回传"| NODES
    NODES -. "压测流量" .-> PROM
    CORE -->|"查询指标"| PROM
    API -->|"DefectGateway"| JIRA
    API -->|"sendEmail"| MAIL
    CORE -->|"LlmGateway"| LLM

    classDef access fill:#EFF6FF,stroke:#3B82F6,stroke-width:1.5px
    classDef api fill:#DBEAFE,stroke:#2563EB,stroke-width:1.5px
    classDef core fill:#FEF9C3,stroke:#CA8A04,stroke-width:1.5px
    classDef worker fill:#DCFCE7,stroke:#16A34A,stroke-width:1.5px
    classDef data fill:#FCE7F3,stroke:#DB2777,stroke-width:1.5px
    classDef ext fill:#F8FAFC,stroke:#94A3B8,stroke-width:1.5px
    class WEB,AGENT access
    class API api
    class CORE core
    class WORKER worker
    class DB data
    class NODES,PROM,JIRA,MAIL,LLM ext
```

### 3.2 模块职责

| 模块 | 职责 | 依赖 | 备注 |
|------|------|------|------|
| `platform-core` | 领域模型与持久化（计划/场景/执行/缺陷/环境检查/报告/项目/成员）、确定性分析引擎、证据链、LLM 网关、监控采集、造数工厂、审计与治理 | 无 Web 依赖 | 纯领域，不 import 任何 Controller/MCP |
| `platform-api` | REST 控制器、Agent 面（`/api/agent/**`）、MCP Server（工具注册）、Facade 层、安全（登录/API Key）、通知编排、前端静态托管 | 只依赖 core | MCP 与 REST 共用 facade seam |
| `platform-worker` | 执行控制 seam（`ExecutionControlService`）、脚本装配、分布式 Runner、JMeter 运行时与 `jmeter-functions` 产物、环境检查执行器、辅助脚本执行 | 依赖 core | 目标态可独立进程；落地先同进程 + 独立启动类 |
| 前端（独立 Vue3 项目） | 全部页面 | — | build 产物打进 api jar（D15） |
| `skills/perf-plan/` | 本地 Agent 生成压测计划用的 skill（对话梳理 → 拉模板 → 生成 → MCP 同步） | 平台 MCP 工具 | 随仓库分发 |

### 3.3 依赖规则

- 依赖方向单向：`api → core`、`worker → core`；core 不得反向依赖。
- 执行启停/脚本装配等既有 seam 决策（见 `CONTEXT.md`）保持不变，拆模块只挪边界不挪语义。

### 3.4 部署架构图（目标态）

> 依据 D15/D16：后端托管前端单 jar 单进程、单机部署、SSH 远端执行节点集群、监控栈独立部署。本地开发与生产部署的差异见下方说明。

```mermaid
flowchart TB
    subgraph CLIENT["访问端"]
        direction LR
        BROW["浏览器<br/>REST /api/** + 前端静态资源"]
        AGENT["本地 Agent<br/>Claude Code / DSH<br/>MCP /mcp · API Key"]
    end

    subgraph HOST["平台主机（单机部署）"]
        direction TB
        JAR["platform-api 单进程 jar<br/>内嵌前端 build 产物<br/>scripts/start.sh 启动"]
        WORKER["platform-worker（同进程）<br/>执行控制 · 脚本装配 · 环境检查"]
        STORE[("本地文件存储<br/>storage/ 脚本·日志·结果·密钥")]
        JAR --- WORKER
    end

    subgraph DATA["数据层"]
        MYSQL[("MySQL 8.0+<br/>主库 + 执行明细")]
        PROM[("Prometheus<br/>指标存储")]
    end

    subgraph REMOTE["执行节点集群（SSH · 1 Controller + N Workers）"]
        direction LR
        CTRL["Controller 节点<br/>Docker · JMeter"]
        W1["Worker 节点 ×N<br/>Docker · JMeter"]
        CTRL --- W1
    end

    subgraph SUT_ENV["被测环境"]
        direction LR
        SUT["被测服务<br/>业务 / JVM / MySQL / Redis / Kafka"]
        EXP["Exporter ×N<br/>node / jvm / mysql / redis / nginx / kafka"]
    end

    subgraph EXT["外部系统"]
        direction LR
        JIRA2["Jira 缺陷平台"]
        MAIL2["MCP 邮件服务"]
        LLM2["LLM Providers"]
    end

    BROW -->|"REST /api/**"| JAR
    AGENT -->|"MCP 工具"| JAR
    JAR --> MYSQL
    JAR --> STORE
    JAR -->|"查询指标"| PROM
    JAR -->|"DefectGateway"| JIRA2
    JAR -->|"sendEmail"| MAIL2
    JAR -->|"LlmGateway"| LLM2
    WORKER -->|"SSH 编排 / 结果回传"| CTRL
    CTRL -. "压测流量" .-> SUT
    EXP -->|"scrape"| PROM
    EXP -. "采集" .-> SUT

    classDef client fill:#EFF6FF,stroke:#3B82F6,stroke-width:1.5px
    classDef host fill:#FEF9C3,stroke:#CA8A04,stroke-width:1.5px
    classDef data fill:#FCE7F3,stroke:#DB2777,stroke-width:1.5px
    classDef remote fill:#DCFCE7,stroke:#16A34A,stroke-width:1.5px
    classDef sut fill:#FFF7ED,stroke:#EA580C,stroke-width:1.5px
    classDef ext fill:#F8FAFC,stroke:#94A3B8,stroke-width:1.5px
    class BROW,AGENT client
    class JAR,WORKER,STORE host
    class MYSQL,PROM data
    class CTRL,W1 remote
    class SUT,EXP sut
    class JIRA2,MAIL2,LLM2 ext
```

**部署形态说明**：

| 形态 | 组成 | 适用 |
|------|------|------|
| 本地开发 | Vite dev（5173）→ 后端 8080 代理；H2 file（MySQL 兼容模式）profile | 日常开发，无外部依赖 |
| 生产部署（P2-1） | 前端 build 产物打进 api jar，页面与 API 同端口；MySQL 8.0+；`scripts/start.sh` 一条命令起全部 | 测试环境内部落地 |
| 监控栈 | Prometheus + Exporters 独立部署（Docker Compose）；平台只做查询与展示，不承载 Prometheus 自身 | 与平台同环境，可用时增强报告 |

- worker 目标态可独立进程（D13）：部署图按独立进程画，落地先同进程、留独立启动类，不影响部署拓扑。
- Dockerfile 顺手提供不承诺维护（D15）；CI、高可用、多租户明确不做（§6）。
- 执行节点通过 SSH 下发任务，JMeter 跑在远端 Docker 容器内（D16），与平台主机解耦。

## 4. 核心闭环与平台分工

```mermaid
flowchart TB
    A["① 需求梳理<br/>本地 Agent 对话 + 公司模板<br/>MCP 同步到平台"] --> B["② 计划评审<br/>状态机流转 + 成员批注"]
    B --> S["③ 脚本开发<br/>与评审并行 · 现有能力"]
    S --> C["④ 环境检查<br/>SSH 探测 · 分级自动修复"]
    C --> D["⑤ 测试执行<br/>单机 / 多节点 + 实时流"]
    D --> E["⑥ 缺陷分析<br/>确定性分析 + LLM 归因"]
    E --> F["⑦ 缺陷提交<br/>平台台账 → Jira 同步"]
    F --> G["⑧ 修复 / 复测<br/>外部修复 · 平台一键复测"]
    G -. 回归 .-> D
    D --> H["⑨ 报告生成<br/>聚合 + 验收判等"]
    H --> I["⑩ 发布（终态）<br/>分享链接 + 邮件通知"]
    I --> J["⑪ 迭代对比<br/>vs 上次发布快照"]

    classDef prep fill:#EFF6FF,stroke:#3B82F6,stroke-width:1.5px
    classDef run fill:#DCFCE7,stroke:#16A34A,stroke-width:1.5px
    classDef defect fill:#FFF7ED,stroke:#EA580C,stroke-width:1.5px
    classDef report fill:#F5F3FF,stroke:#7C3AED,stroke-width:1.5px
    class A,B,S prep
    class C,D run
    class E,F,G defect
    class H,I,J report
```

| 环节 | 平台内 | 平台外 |
|------|--------|--------|
| 需求梳理 | 模板管理、MCP 接口、计划文档存储 | 本地 Agent 对话生成 |
| 计划评审 | 状态机、批注、评审人（项目成员） | — |
| 脚本开发 | 脚本编辑/版本/HTTP 调试/函数库（现有）+ JSR223 前后置处理器（P1-7） | 本地 JMeter 工具（现状不变） |
| 环境检查 | 预检任务、清单、自动修复记录、结果回填 | SSH 到目标服务器执行 |
| 测试执行 | 触发/控制/实时流/结果 | JMeter 在远端节点跑 |
| 缺陷 | 台账、关联、分析报告、同步推送 | Jira 承载缺陷流转 |
| 通知 | 模板、抄送清单、触发时机 | MCP 邮件服务实际发送 |
| 发布 | 终态动作、只读分享链接 | 导出 PDF/Word/HTML 供线下 |

## 5. 待完成功能大纲（按优先级）

> 状态：⬜ 未开始｜🔄 进行中｜✅ 已完成。**实施顺序由 P0 到 P3；同优先级内按"闭环断点优先"。**

### P0 —— 地基与闭环两端（先做）

| ID | 工作项 | 状态 | 依赖 | 完成口径（验收） |
|----|--------|------|------|------------------|
| P0-1 | **计划文档模块重构**：TaskPlan 升级为压测计划文档（结构化模块 + Markdown 正文，**一稿走到头**：计划→执行回填→报告→发布）；二级状态机 D2；评审流程与批注（任意成员通过）；业务化场景设计（脚本不进文档、评审后编写关联）；环境检查可选项（首执行触发、可跳过）；模板体系（内置 + 项目自定义）；自动回填 D4；revision 冲突三选一 D5；发布终态 | ⬜ | — | 一个计划能从草稿走到"已发布"，中间可评审、可批注、可被本地 Agent 同步修改且冲突可手工处理 |
| P0-2 | **MCP 计划工具集**：`plan_templates` / `plan_create` / `plan_get` / `plan_update` / `plan_query`（发布不进 MCP，D12）；仓库内 `skills/perf-plan/` skill | ⬜ | P0-1 | 本地 Agent 仅凭 MCP + skill 完成"梳理→生成→同步→再修改"全流程 |
| P0-3 | **验收标准实体 + 自动判等**：验收指标挂计划，分两层——**场景级指标**（每场景 TPS/RT/错误率）+ **计划级总体判定**；报告自动判定通过/不通过 + 人工确认；结论回填文档 | ⬜ | P0-1 | 一次执行结束，报告直接给出"过/不过 + 哪些指标超标"，场景级明细可下钻 |
| P0-4 | **MySQL 一次性迁移**：主库 + 执行明细（SQLite）统一迁 MySQL；H2 留本地开发 profile；部署初始化说明 | ⬜ | — | 全量测试通过，Docker 起 MySQL 后平台可开箱运行，本地无 MySQL 时仍可用 H2 profile |

### P1 —— 闭环中部补强

| ID | 工作项 | 状态 | 依赖 | 完成口径（验收） |
|----|--------|------|------|------------------|
| P1-1 | **环境检查**：独立预检任务（平台本机 SSH 探测，push 脚本兜底）；插件式检查项；三级自动修复边界 D8；三态结果清单 + 修复建议；结果回填计划文档 | ⬜ | P0-1 | 对一台"配置有问题"的服务器跑预检，输出三态清单；低风险项可一键修复且留 diff |
| P1-4 | **迭代对比**：发布版本快照 + 手动基线兜底（D11）；同计划执行 vs 快照自动对比；TPS/P95/P99/错误率 + 资源指标 + 差异判定；**复用 verification 模块自动检测被测侧代码变更**，把"性能变差"与"改了什么"关联起来 | ⬜ | P0-1、P0-3 | 计划第二次发布后，同计划再执行自动给出"相对上次快照"的差异报告，并列出期间被测侧变更 |
| P1-5 | **CSV 测试数据管理**：参数文件上传、版本管理、执行时分发到各节点；与脚本 CSV 步骤联动 | ⬜ | — | 上传一个 CSV，配置到场景，多节点执行时每个节点都拿到该文件 |
| P1-6 | **MCP 工具目录页（卡片式）**：展示全部已注册 MCP 工具（名称/阶段/说明/参数 schema 摘要/写权限标记）；页头接入指引（endpoint + API Key 申请入口 + Claude Code / DSH 配置片段一键复制）；按阶段筛选 + 搜索；页面只读，启停与可见性由注册表 stage/scope 决定（D18） | ⬜ | — | 新成员打开页面复制配置即可在本地 Agent 接入，无需问人；卡片信息与注册表一致 |
| P1-7 | **JSR223 前后置处理器组件**：编辑器支持给线程组/请求添加 JSR223 PreProcessor / PostProcessor（Groovy 优先）；内置加解密代码片段库（AES/签名等）；密钥走参数化引用不落明文（D17）；渲染进 JMX 且单机/分布式执行均生效 | ⬜ | — | 编辑器添加带请求签名的 JSR223 PreProcessor，导出 JMX 在 JMeter 与分布式执行中均生效 |
| P1-8 | **监控深化**：应用层与中间件指标接入展示（JVM GC 分代/线程池、MySQL/Redis/Kafka 关键指标）；指标采样频率/下采样/保留周期可配置 + 过期清理；资源指标对比纳入迭代对比（联动 P1-4） | ⬜ | — | 执行详情可下钻 JVM/中间件指标；采样保留按配置清理；迭代对比含资源指标差异 |

> P1-2（缺陷台账）、P1-3（通知）已于 2026-09-02 延期至 P3（P3-7 / P3-8）。

### P2 —— 工程与规模

| ID | 工作项 | 状态 | 依赖 | 完成口径（验收） |
|----|--------|------|------|------------------|
| P2-1 | **部署整理（最小版）**：后端托管前端单 jar；`scripts/start.sh`；部署文档；Dockerfile（不承诺维护） | ⬜ | P0-4 | `./scripts/start.sh` 一条命令起全部，页面 + API 同一端口可用（目标部署图见 §3.4） |
| P2-2 | **容量调度/自动分片**（远期）：按目标并发自动选节点、负载分配、节点容量感知 | ⬜ | — | 指定目标并发数，平台自动决定用几个 worker、各分多少 |
| P2-3 | **执行数据保留与清理**：执行明细/采样/失败样本的保留周期策略 + 过期清理任务 | ⬜ | P0-4 | 按项目/计划配置保留时长，过期自动清理，存储不无限膨胀 |
| P2-4 | **执行中监控告警**：目标资源/错误率阈值告警 + 可选自动熔断 | ⬜ | P1-8、P3-8 | 压测中指标越线即告警、可选自动停止执行（邮件通知随 P3-8 接通） |

### P3 —— 延期项与企业化甜点（最低优先级）

| ID | 工作项 | 状态 | 依赖 | 备注 |
|----|--------|------|------|------|
| P3-1 | 多团队空间 + 细粒度 RBAC | ⬜ | — | 企业多团队共用时才需要 |
| P3-2 | AI 生成脚本（OpenAPI/自然语言 → JMX，REQ-SCRIPT-009） | ⬜ | — | 甜点 |
| P3-3 | CI（构建/测试流水线） | ⬜ | — | 团队规模大了再上 |
| P3-4 | SSO、审计合规扩展 | ⬜ | — | 仅预留位，不进近期计划 |
| P3-5 | 执行节点健康巡检/平台运行监控（REQ-DIST-004） | ⬜ | — | 节点离线/异常在平台内可见 |
| P3-6 | 定时/周期执行（按计划 cron 自动回归） | ⬜ | P1-4 | **优先级最低，最后再考虑** |
| P3-7 | **缺陷台账 + Jira 同步**：缺陷记录与执行/样本/报告关联；缺陷来源 = 失败样本 + **慢请求 TopN 明细** + 人工登记；可插拔 DefectGateway；Jira adapter（凭据密文、字段映射）；手动/自动推送；状态轮询回拉 | ⬜ | — | 原 P1-2，2026-09-02 延期；闭环环节 ⑥⑦ |
| P3-8 | **通知（MCP 邮件）**：模板 + 抄送清单配置（项目默认 + 计划覆盖）；按角色抄送 + 发送前确认可改；时机 = 完成/失败/发布；5 个核心模板变量 | ⬜ | P0-1 | 原 P1-3，2026-09-02 延期；闭环环节 ⑩；P2-4 的邮件通知依赖本项 |

## 6. 明确不做 / 保留现状

- **多节点分布式压测**：已支持（D16），不重复立项。
- **MCP 白名单**（D12）：登录/密钥管理、发布终态、删除类、自动修复默认不进 MCP。
- 旧路线图中的甜点项（多引擎导出、.jmx 下载、Word 导出增强、造数导出、Groovy 函数调试）维持"未排期"状态，不进 P0–P3。执行中监控告警已入 P2-4，节点健康巡检已入 P3-5。**JSR223 前后置处理器（P1-7）与 Groovy 函数调试甜点项不是一回事**：前者是脚本编辑能力（加解密实现），照常排期；后者是函数库调试器，仍不排期。
- 通知渠道仅 MCP 邮件，不做 IM webhook；评审流转不发通知（仅执行完成/失败、计划发布）。
- 微服务化、多仓拆分：明确不做（D13 单仓多模块）。

## 7. 实施节奏建议

1. **第一批（地基）**：P0-4 MySQL 迁移 + P0-1 计划文档重构同步开工——计划重构动到 TaskPlan/Scenario/Execution 关系，与数据层改造天然同批；后端多模块拆分（`core/api/worker`）作为**并行轨道小步做**：每批功能顺带挪一个模块边界，不单独停工重构。
2. **第二批**：P0-2 MCP + skill、P0-3 判等——闭环两端接通。
3. **第三批起**：P1 按 P1-1 → P1-4 → P1-5 顺序推进（每项独立可交付）；P1-6（MCP 目录页）与 P1-7（JSR223）无阻塞依赖，可与第三批并行穿插；P1-8（监控深化）是 P2-4 告警的前置，按序推进。P1-2（缺陷台账）、P1-3（通知）已延期至 P3（P3-7 / P3-8）：P2-4 的邮件通知依赖 P3-8，告警展示与熔断可先做。
4. 每项开工前：把本文档对应行转为 GitHub Issue，验收口径作为 Issue 的完成定义。

## 8. 文档维护约定

- 本文档 = 功能大纲与架构目标的**权威来源**；`docs/requirements-spec.md` 保留需求细节，路线图章节指向本文档。
- 任何决策变更（D1–D16，后续新增继续编号）必须更新 §2 对应行并注明变更日期。
- `CONTEXT.md` 在模块拆分落地后同步更新模块地图。
