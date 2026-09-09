# 性能测试平台 - 实现记录

## 2026-05-29

已完成：

1. 初始化 GitHub 公开仓库。
2. 建立 Spring Boot 3 后端和 Vue 3 前端骨架。
3. 实现登录演示接口。
4. 实现项目创建、列表、归档和恢复接口。
5. 前端从静态页面升级为可操作项目工作台。
6. 后端项目与用户从内存态切换为 JPA + H2 文件库。
7. 增加 API 行为测试和持久化测试。

提交：

1. `9a4b561 feat: scaffold performance test platform`
2. `02246cd feat: add phase one project workspace`
3. `437112a feat: persist phase one identity and projects`

## 2026-06-01

已完成：

1. 项目成员管理接口。
2. 项目成员弹窗。
3. 基础负责人权限约束。
4. 需求规格与阶段计划归档到项目 `docs/`。
5. Phase 2 脚本管理第一步：项目下 JMX 上传、脚本版本持久化、脚本版本列表。
6. 需求与设计文档按模块拆分，新增 `docs/modules/` 模块文档和 `docs/README.md` 索引。

验证：

1. `gradle :backend:test --tests com.yr.perftest.platform.api.PlatformApiBehaviorTest` 通过。
2. `gradle :backend:test` 通过。
3. `npm run build` 通过。
4. 本地接口验证：
   - `GET /api/projects/1/members`
   - `POST /api/projects/1/members`
   - `POST /api/projects/1/scripts`
   - `GET /api/projects/1/scripts`
5. `gradle :backend:test --tests com.yr.perftest.platform.api.ScriptApiBehaviorTest` 通过。

说明：

浏览器自动化刷新被 Browser 插件 URL 策略阻止，未通过自动化完成页面点击验证。后端服务已重启，前端开发服务仍运行在 `http://127.0.0.1:5173/`。

## 2026-06-17 ~ 2026-06-30

已完成：

1. 前端统一组件库、冷青主题编辑器、页面布局统一与 favicon。
2. 脚本编辑器补齐 JSON 断言组件、XML 视图、HTTP 配置与步骤操作优化。
3. 分布式压测执行（执行节点注册、SSH 下发、远程 JMeter 运行）与执行器健康检查。
4. Prometheus 监控集成：目标管理、远程部署 Exporter、任务执行期间指标关联。
5. 任务计划重构为计划/场景/执行三层模型，统一执行结果数据口径。
6. 失败样本采集链路重构：JSONL 采集、SQLite 存储、SSE 实时推送与异常详情分页。
7. 全局面包屑导航、执行详情历史记录下拉、场景创建与执行交互优化。

提交：`a691354` 至 `f0916d0`（`git log --since=2026-06-16 --until=2026-07-01` 可查完整列表）。

## 2026-07-01 ~ 2026-07-15

已完成：

1. 计划维度性能测试报告生成（含报告预览页与 Word 导出）。
2. 场景多组线程组配置、配置份选择，执行结果按线程组拆分展示。
3. 报告按线程组 preset 聚合，梯度总览对齐场景执行记录。
4. JMeter 函数库：只读展示、分布式 runtime 注入、内置造数与 CODEC 函数扩展。
5. 平台级 LLM 模型配置管理（Provider/Model/调用记录，多协议适配）并升为一级菜单。
6. 造数工厂：测环境录制确认写库、数据源管理、策略化录制、异步样本与相邻 Diff 分析。
7. 完善需求规格说明书、README 与数据库建表脚本，清理未使用配置。

提交：`c624e10` 至 `ed9df8e`。

## 2026-07-22 ~ 2026-07-30（Agent 化平台建设 M1/M2/M3）

已完成：

1. T1 认证底座：平台用户 opaque token + Agent API Key + 统一 Principal（human/machine）。
2. T3/T4 Agent-ready Facade 与统一响应契约：`/api/agent/**` 唯一入口、`ApiResponse` 封套、稳定错误码、springdoc OpenAPI。
3. T5/T6 M3 数据链底座：统一游标分页 + 三维响应预算 + 数据可用性语义；`evidence` 关联键与时钟对齐适配层。
4. T7 确定性分析：趋势/异常区间与拐点/错误聚类/资源饱和/执行间可比性五类带版本算法 + agent 面分析入口。
5. T8 压测执行工具化：幂等键、统一启动/停止/取消状态机、执行预检与影响评估、agent 面执行控制入口。
6. 前端冷青双轨设计系统、HTTP 编辑器变量与函数快捷引用/高亮/报文预览。

验证：

1. `gradle :backend:test` 全量通过（含 agent 分层守护、幂等、预检、分析黄金数据集用例）。
2. `openspec validate add-m3-data-chain --strict` 通过，变更已归档并同步主 spec。
3. OpenSpec 变更归档：`add-auth-foundation`、`add-agent-facade-contract`、`add-m3-data-chain`。

## 2026-08-19（M4/M5 与后续增强模块收官）

已完成：

1. 流程收尾：归档 M3 OpenSpec 变更、同步主 spec、补齐实现记录、勾选 T7/T8 计划、`.mcp.json`（含个人密钥）加入 `.gitignore`。
2. T10 治理：`governance` 包——输出边界脱敏（敏感键/令牌/头部）、请求审计 + 执行审计、滑动窗口限流 + 在途并发限流（仅 `/api/agent/**`），执行 START/STOP/CANCEL 审计落库。
3. T9 补充取证 + 优化验证：`verification` 包——取证（目的/影响/成本预检 → 人工审批 → 证据快照回流）+ 变更登记 + 三态优化验证（IMPROVED/REGRESSED/INCONCLUSIVE，复用 T7 execution-compare + 错误率护栏）。
4. T11 深度证据：`evidence/deep` 五类源（db-metrics/trace/app-log/slow-sql/profiling）注册进证据链，`GET /api/agent/executions/{id}/evidence` 按 executionId/时间窗/traceId 下钻，每源显式可用性；db-metrics 接 Prometheus exporter 真实探针。
5. T12 MCP Server：`/mcp` Streamable HTTP（MCP Java SDK），机器身份复用 API Key，8 个任务型工具复用 Facade，只读 scope 越权拦截，写操作幂等，端到端协议级测试。
6. T13 Skill Pack：`skill-pack/` 六技能 + 协议级验收脚本 + 审计重建入口（`GET /api/agent/audit/requests|executions`）+ Claude Code 走查手册。
7. 模块 09 辅助脚本：前置/后置脚本、版本不可变绑定、STOP_TASK/CONTINUE/MANUAL_CONFIRM 失败策略、超时与日志、执行生命周期钩子（PRE 在启动后、POST 在终态后）。
8. 模块 10 Git/日志/AI：Git 仓库配置与 JGit 提交导入、任务代码绑定、日志制品上传与检索、报告 AI 分析（LlmGateway 单次调用，保留输入/模型/Prompt 版本）。
9. 模块 06 增强：报告对比（标签级 + 总体差异，纯函数算法）+ PDF 导出（openhtmltopdf）；移除与真实接口冲突的 `ModuleMockController.compareReports` 占位。

验证：

1. 每阶段 `gradle :backend:test` 全量通过（含治理/取证/证据/MCP/辅助脚本/Git/AI/报告对比用例）。
2. 新增端到端：MCP initialize/tools/list/tools/call、幂等启动、只读 scope 越权、审计重建、取证审批流、深度证据可用性、辅助脚本失败策略、JGit 真实仓库导入、Mock LLM Provider 全链路。

## 2026-08-28（架构评审重构：控制 seam 收敛 + 上帝模块拆分）

依据架构评审报告执行（improve-codebase-architecture），C7 按报告结论跳过：

1. **C1 收敛双执行控制路径**：UI 面 `TaskPlanController` 触发/停止改走 `ExecutionControlService`（幂等键 + 审计 + 冲突语义），与 agent 面共用同一条 seam；前端执行确认对话框生成幂等键（`Idempotency-Key` 头）；`ExecutionConflictException` 映射 409。
2. **C2 拆分执行上帝模块**：`ScenarioExecutionService`（原 480 行 14 方法）拆为写模型（触发/停止/删除）+ `ExecutionQueryService` 读模型（结果/采样/失败样本/指标/实时流）；调用方 DataFacade/ReportDataService/TaskPlanController 全部切换。
3. **C4 脚本装配收敛**：新增 `ExecutionScriptAssembler`（装载→线程组预设补丁→监听器注入→写出），`DistributedJmeterExecutionRunner` 从 662 行减负，不再直接持有 Parser/Patcher/Injector；新增纯文件级装配测试。
4. **C5 Facade 一致性**：`AuditFacade` 执行审计改有界查询（`findByExecutionIdOrderByIdDesc`），移除全量内存过滤；`FacadeGuard` 清理占位注释；`ExecutionFacade` 审计移交控制模块。
5. **C6 造数工厂**：`testDatasource` 改类型化记录 `SeedDatasourceTestResult`（JSON 键不变）；行级数据保持 schema-less（表结构天然动态）。
6. **C8 前端**：抽出 `useExecutionEventStream`（SSE 连接/指数退避/回放），状态文案 `executionStatusText` 收敛到 api 层单点。
7. **功能修复**：脚本列表 N+1 消除；`steppingThreadGroupSupported` 改探测注入容器的 `jmeter-runtime/*.jar`（不再误测本机 JMeter）；`GitCommitImporter` 残留副本自愈重建（修复 GitLogAiApiTest 偶发失败）；`ModuleMockController` 整体删除；前端 reports 页接真实报告列表（HTML 预览/Word/PDF）；`PROMETHEUS_BASE_URL` 空默认 + 显式未配置报错；README/需求规格的模块状态与文档漂移修正。
8. 新增 `CONTEXT.md`：领域词汇 + 模块地图 + 关键 seam 决策记录。

验证：新增 `UiExecutionControlApiTest`（UI 幂等/审计/409）、`ExecutionScriptAssemblerTest`；`gradle :backend:test` 全量通过；`npm run build` 通过。

## 2026-09-04（P0-1 计划文档模块重构：TaskPlan 升级为压测计划文档）

已完成：

1. TaskPlan 升级为压测计划文档并"一稿走到头"（计划→执行回填→报告→发布）：实体二级状态（phase/status）与 revision、计划域权限矩阵与 8 动作状态机、文档服务（409 冲突体 + 系统回填幂等 + 行级锁）、批注（REVIEW/SYSTEM）、模板体系（内置 11 章节 + 项目自定义）、业务化场景（脚本后置关联 + purpose/testType 回写）、执行门禁与 precheck（挂 ExecutionControlService 唯一 seam、可跳过留痕）、报告生成与发布快照、只读分享令牌、REST 面与前端五阶段详情页/文档 Tab/批注时间线/场景设计/发布分享页（Task1~Task17）。

提交：

1. `3ab06b7` feat：P0-1 Task1 计划/场景实体扩展——二级状态列、文档正文与 revision、脚本可空与业务字段
2. `8867f1e` feat：P0-1 Task2 PlanMarkdownSupport——章节切分/替换、场景块执行记录幂等回填、清单解析
3. `137351e` feat：P0-1 Task3 计划域角色解析与 17 动作权限矩阵
4. `37076ce` feat：P0-1 Task4 文档服务——原文读写/409 冲突体/系统回填幂等/执行态惰性纠偏 + 批注实体
5. `8b5594d` fix：P0-1 Task4 updateMarkdown 行级锁防并发丢更新（409 契约）
6. `c781926` feat：P0-1 Task5 状态机流转与批注——8 个流转动作、权限与非法状态 409、SYSTEM 批注留痕
7. `1d4dfda` feat：P0-1 Task6 模板体系——内置 11 章节模板 seed、项目模板 CRUD、创建计划渲染正文与默认执行设置
8. `ee36d6c` fix：P0-1 Task6 precheck 默认清单只取入口准则 + 模板项目归属校验
9. `d497ace` feat：P0-1 Task7 业务化场景——脚本可空后置关联、purpose/testType、场景事实回写文档保留自由文本
10. `fae4ab8` fix：P0-1 Task7 updateScenario 对 purpose/testType 做 null 跳过合并（防局部 PUT 清空）
11. `7541cc1` feat：P0-1 Task8 执行门禁与环境检查挂唯一 seam——阶段/脚本校验、首执行自动 precheck、跳过留痕、报告作废
12. `c80b23f` feat：P0-1 Task9 执行终态联动——事件驱动回填场景块与 DONE 判定、快捷执行单事务四步
13. `453a059` feat：P0-1 Task10 发布快照与只读分享——token 实体、创建/撤销/过期判定、/api/share/** 放行
14. `b79cdc8` feat：P0-1 Task11 报告生成与发布终态——结果总览回填、达成表实际列、发布快照、新修订重置
15. `35be82b` fix：P0-1 Task11 publish 缺结论章节时不再拼入 null 字面量
16. `582cc6b` feat：P0-1 Task12 REST 面——文档/流转/批注/模板/分享/快捷执行端点与错误码映射、级联删除
17. `a84c129` feat：P0-1 Task13 前端地基——md-editor-v3/diff 依赖、plan 域类型与 API、markdown 章节工具
18. `b2de85e` feat：P0-1 Task14 计划详情壳——五阶段步进条、四 Tab、usePlanDoc 状态与冲突感知保存
19. `7c92e54` feat：P0-1 Task15 文档 Tab——Pretty|Markdown 分段控件、TOC、章节级写回、冲突三选一、执行设置抽屉
20. `b8442aa` feat：P0-1 Task16 评审/报告/发布 Tab 与场景设计模块——批注时间线、结论回填预览、发布快照分享、业务化场景卡
21. `a0d80e1` fix：P0-1 Task16 撤销按钮 SHARE 权限显隐 + 场景卡渲染设置表（解析器扩展 settings）
22. `31b73a8` feat：P0-1 Task17 列表阶段徽标、模板选择、场景业务字段、precheck 跳过、分享公开页与快捷执行单请求化

验证：

1. 验收口径核对：`gradle :backend:test --rerun` 全量通过（108 个测试类 / 392 用例，0 失败 0 错误）；前端 `npm run build` 0 错误。人工验收走查（路线图 P0-1 口径五条）由控制器后续执行，结果另行记录。

## 2026-09-04（P0-2 ② MCP 工具目录页）

已完成：

1. 后端 `GET /api/mcp/tools`（`api/McpDirectoryController`）：直接映射内存 `McpToolRegistry` 单一事实源，固定规范 stage 序列（PLAN→NAVIGATE→DESIGN→OBSERVE→DIAGNOSE→VERIFY→CAPTURE）排序，登录可读；`McpTool` 契约补 `default usageExample()`（存量 8 工具零改动）。
2. 前端 `/mcp-tools` 顶级路由 + 全局导航「MCP 工具」入口：接入指引横幅（Claude Code / DSH 配置片段一键复制、API Key 申请入口），阶段筛选 tabs + 本地搜索 + 单一平铺卡片网格（两态状态图标、写权限徽标），接口文档式详情抽屉（inputSchema 参数表 + 使用示例），页脚收尾；对照 `mcp-directory-prototype.html` 视觉基准实现。
3. ① 计划工具 ×5 与 ③ perf-plan skill 依赖 P0-1（未开发），按 spec §8 保持延后跟踪。

验证：

1. `McpDirectoryControllerTest`（401 / 与 registry 严格一致 / 序列排序 / 字段口径）+ `McpServerApiTest` 回归全绿；`gradle :backend:test` 全量通过。
2. `npm run build`（vue-tsc + vite）零错误；bootRun + curl 端到端冒烟（登录读取目录、匿名 401）通过。

## 2026-09-07（P0-2 ①③ 计划工具集与 skill）

已完成：

1. ① 五个计划 MCP 工具（`mcp/plan/`）：`plan_templates`（派生 sections/placeholders/scope）、`plan_create`（markdown 初始正文 revision=1，模板可见性校验）、`plan_get`（全文回读）、`plan_update`（乐观并发）、`plan_query`（phase/keyword 过滤 + 内存分页）；stage=PLAN，目录页零改动自动呈现（13 工具）。
2. 错误通道：`McpToolSupport` 增 `details` 负载与 `PLAN_REVISION_CONFLICT`/`PLAN_STATE`/`PLAN_INVALID`/`PLAN_ACCESS_DENIED`/NOT_FOUND 映射（词表同 REST PlanErrorBody）；机器身份合成 `HumanPrincipal("agent", ADMIN)` 进入 plandoc 服务。
3. `TaskPlanService` 增 9 参 `createPlan` 重载（initialMarkdown 初始正文不加版）。
4. ③ `skill-pack/perf-platform-plan/SKILL.md`（梳理→模板→渲染→同步→再修改，冲突三选一与停止条件）+ README 组件表 + 冒烟脚本扩 13 工具与计划只读调用。

验证：

1. `McpToolSupportPlanErrorTest`/`PlanInitialMarkdownCreateTest`/`PlanToolsTest` 直调 + `McpServerApiTest` 协议级（13 工具、readonly 调用写工具被拒、端到端含冲突 details）+ 目录 PLAN 排序断言全绿；`gradle :backend:test` 全量通过。
2. bootRun + acceptance-smoke.sh 实跑通过（13 工具可见、内置模板在列）；目录端点 toolCount=13。


## 2026-09-07（P0-3 验收标准解析 + 自动判等）

已完成：

1. 指标章节解析器 `task/plandoc/PlanAcceptanceParser`（纯静态）：「二、测试目的与指标」表格解析——别名映射（trim+大小写不敏感，TPS/平均RT/P95/P99/错误率/并发峰值/容量/OTHER）、`交易`旧表头向后兼容、方向符与单位剥离、格式非法抛 `PLAN_INVALID`（缺列/单元格不足/目标值非数字，带行号）；章节缺失/无表格/空表落无指标路径；`parseLeniently` 为报告期存量兜底。
2. 保存链解析即校验（不落库、不建判等实体）：挂 `PlanDocumentService.updateMarkdown`（REST PUT / Pretty 章节合并 / MCP `plan_update` 三路共用），revision 校验之后、updateBody 之前，非法 400 且文档与 revision 不变。
3. 判等引擎 `task/plandoc/PlanVerdictService`：判等输入 = 文档指标章节 + 每场景最近执行（`findFirstByScenarioIdOrderByIdDesc` 同报告取数口径）；场景级绑定 Summary（TPS/AVG_RT/P95/错误率）、交易级绑定 AggregateRow（含 P99）；同名 label 多场景/零命中/场景无执行/无聚合数据/不可判类型 → 无法判定附原因（含 scenarioId/executionId 供下钻）；计划级聚合 PASSED/FAILED/INDETERMINATE/NONE + 自动判定文本（发布预填与达成表尾行共用）；零样本守卫（评审修复）：无聚合数据的执行落无法判定、不按空 Summary 零值误判。
4. 报告链路：`generateReport` 按有无指标分流——有指标 `upsertVerdictTable` 幂等重绘达成表（`<!-- backfill:verdict -->` 块，六列+自动判定尾行；标记/`### 指标达成表` 占位小节/结论章节尾三态落点）、不再跑实际列摘要回填；无指标 P0-1 现状零回归；块尾扫描收敛 `blockEndOf`；`fillConclusionActualColumn` 实际列按表头自适应（新六列/旧四列兼容）。
5. verdict 只读接口 `GET /api/task-plans/{id}/verdict`（项目成员读门槛同 /report）：即时重算不持久化，present/available/overall/prefillConclusion/rows；仅 REPORT/DONE 与 PUBLISH/PUBLISHED 可读，复测重置（newRevision 后）available=false。
6. 内置模板 seed 微调：指标表头 `交易`→`对象`；结论达成表占位更新为重绘后六列形态；seed 存在即跳过不变。
7. 前端：报告 Tab 总体判定徽章（达成绿/未达成红/无法判定橙）+ 未达标行清单 + 执行详情下钻 + 无指标提示条 + 报告未生成警告（生成报告成功后即时刷新）；发布表单预填 prefillConclusion（仅空时预填、人可改）；`plan-doc.ts` 扩 verdict 类型与调用。

关键决策与偏差（对照 spec）：

1. verdict 响应未加顶层 reason 字段（spec §6.1 响应结构为准，available=false 以 overall=NONE 表达、前端提示"报告未生成"）。
2. spec §3.5"Pretty 指标表单列名改对象"无对应实体——Pretty 视图对该章节为 MdPreview 直渲染 Markdown，实际落点仅模板 seed 表头（前端无改动点）。
3. rows 字段名 `objectName`（spec §6.1 草图为 `object`；端到端一致且更达意，保留）。
4. P0-1 遗留项目级模板创建接缝（`POST /projects/{id}/plan-templates`）按任务书默认**保留**，待人工验收走查定夺（roadmap §6 活口）。
5. 评审裁量修复：报告 Tab 生成报告后即时刷新判定（终审发现同页过期警告缺陷）。

验证：

1. `gradle :backend:test` 全量两轮通过（修复波前后各一轮：120 测试类 / 450→451 用例，0 失败 0 错误）；新增 6 个测试类（解析器/保存校验/判等引擎/报告重绘/verdict API/模板 seed）+ 修复波补真实发布→修订复测链与平均RT方向断言。
2. `npm run build`（vue-tsc --noEmit + vite build）零错误。
3. Subagent-Driven 每任务实现+评审门（Task 3 零样本守卫修复轮、终审 3 项修复波，复审均 ADDRESSED）。

## 2026-09-07（P0-4 主库 MySQL 迁移——夜间跳过）

按任务书前置自检：本机 `docker info` 不可用（无 docker CLI / Docker.app / colima，属未安装而非守护进程未启动），P0-4 **整体跳过**，未做任何代码/配置变更。spec §10 验收口径（testMysql 全绿等）要求 Docker，不允许跳过 testMysql 假装完成。待具备 Docker 的环境（本机安装或昼间人工）后按 `docs/superpowers/specs/2026-09-07-p0-4-mysql-migration-design.md` 重新实施；亦不可夜间登录 192.168.17.216 部署（任务书红线）。

## 2026-09-08（P0-4 主库 MySQL 迁移——实施完成，7 任务全链）

> 前夜「跳过」记录由本日昼间实施取代：本机未装 Docker，改以 SSH unix-socket 隧道驱动 216 远程 Docker 满足全部真库验收，216 MySQL 已实部署为常驻库。

已完成（对应任务 1–7）：

1. 依赖与 compose：`com.mysql:mysql-connector-j` 确认在依赖中；`deploy/mysql/docker-compose.yml`（mysql:8.0、utf8mb4 服务器级、root/perftest-root + 应用账号 perftest/perftest、healthcheck、数据卷）部署到 216 `/app/perftest/`。
2. Flyway V1 基线生成与清洗：50 表全量 DDL（UNIQUE KEY 去名×16、二级索引提出为 CREATE INDEX×13、enum 保留经 H2 验证网裁决通过），`@Lob` 44 列以 `${lob_type}` 占位符按方言参数化（主配置 longtext / 测试侧 clob）。
3. 配置切换与 sweep：`application.yml` 删 H2 console/url，默认直连 216 MySQL + Flyway + `ddl-auto: validate`；55 处测试 `create-drop`→`validate`；56 处去 `DB_CLOSE_DELAY=-1` 恢复新上下文全新库语义；测试源集兜 `src/test/resources/application.yml`（`@DataJpaTest` replace=none 走 MODE=MySQL）。
4. 删除项：`MonitoringSchemaInitializer` 与废弃 `docs/database/mysql-schema.sql` 收编删除（补列结果已含于 V1）。
5. 迁移工具：`app.data-migration.h2-source` 属性开关，固定有序表清单按原主键分批拷贝（批 500），目标非空拒跑/`overwrite` 显式覆盖，旧库只读（IFEXISTS+ACCESS_MODE_DATA=r）；runner `HIGHEST_PRECEDENCE` 先于内置 seeder，避免 seed 行触发拒跑。
6. testMysql 任务与真库验证：`MysqlV1SchemaIT`（真库上下文 + LONGTEXT/datetime(6) 往返）与 `DataMigrationMysqlIT`（显式主键 + 自增续号）经远程 Docker 隧道跑绿；默认 test 显式 `excludeTags 'mysql'` 对称隔离（Gradle 9 已无 *Test 文件名默认过滤）。
7. 主配置真库启动缺陷修复：`MysqlLongtextDialect` 提升主源集 `config` 包并挂 `spring.jpa.properties.hibernate.dialect`（validate 与 `@Lob String`→longtext 双语义并存，test yml 整体遮蔽故 H2 网零扰动）；JDBC URL `characterEncoding=utf8mb4`→`UTF-8`（Connector/J 9 拒绝 MySQL 词法）。

关键决策与偏差（对照 spec）：

1. **远程 Docker 隧道**：spec 未预见"本机无 Docker"场景，SSH unix-socket 隧道（`-L /tmp/p024-docker.sock:/var/run/docker.sock`）+ `DOCKER_HOST` + `TESTCONTAINERS_HOST_OVERRIDE` 是不装本地 Docker 且零改服务器的唯一无损方案；ryuk 边车因 216 无外网拉不到 Docker Hub，须 `TESTCONTAINERS_RYUK_DISABLED=true`（README 已记录跑法）。
2. **T2/T3 原子合并**：V1 基线与配置切换互相依赖（validate 无表必红），合并为单提交保证每提交可构建。
3. **55 处 validate sweep**：spec §7"测试零改动"与 M1"validate 全局"冲突，以 M1 为准——机械 sweep 非语义改动。
4. **enum 保留**：H2 验证网全绿裁决通过（H2 MODE=MySQL 接受 MySQL enum 词法），未做 spec 预案的 varchar 降级。
5. **遗留项**：Hibernate 6.6 对 `@Lob String` 在原生 MySQL 方言渲染 tinytext（255 字节截断隐患），由自定义方言解析回 CLOB 保住 longtext 语义——升级 Hibernate 时需回归验证。
6. **test yml 遮蔽副作用**：`src/test/resources/application.yml` 整体遮蔽主配置（test JVM 永远看不到主 yml 的 dialect/占位符），两 IT 内显式内联 `lob_type=longtext` + 方言 FQN；这也意味着默认 H2 套件对主配置改动天然免疫（既是隔离优点也是盲区）。
7. **验收 #4/#5 记 N/A**：本机无存量 `./storage/perftest` H2 文件——迁移工具语义已被单测 + MySQL IT 覆盖，真实迁移待用户提供旧库时一次执行；旧文件保护逻辑（只读 IFEXISTS）同由测试覆盖。

验证：

1. 全量默认套件 **121 suites / 454 tests / 0 failures / 0 errors / 0 skipped**（IT 类不在默认结果中，主 yml 改动对 H2 验证网零扰动）。
2. `./gradlew :backend:testMysql` 经远程 Docker 隧道 **2/2 全绿**（修复波后含 datetime(6) 微秒精度断言）。
3. 默认配置直连 216 真 boot：`bootJar` + `java -jar` 无环境覆盖，Flyway `Successfully applied 1 migration`（51 表 = 50 + flyway_schema_history，history version=1 success=1）；表结构与种子数据按任务要求保留在 216。
4. 收尾冒烟（Task 7，2026-09-08）：bootJar 复用 HEAD 构建，默认配置启动（Started in 34.1s，Flyway 连 `192.168.17.216:3306/perftest` validate 通过）→ curl `POST /api/auth/login` admin/admin123 得 200+token → `POST /api/projects` 201（id=1）→ `POST /api/projects/1/task-plans` 201（id=1，模板正文落库）→ GET 项目 200 / 计划列表 200 / 计划详情 200；boot 日志零 ERROR，停机后 216 库保留冒烟数据。
5. 全仓 grep：代码（java/gradle/yml）`MonitoringSchemaInitializer`/`mysql-schema.sql` 零残留；文档残留仅 openspec 归档与 docs/superpowers 历史 spec/plan（有意保留的历史记录）。

### 2026-09-08（P0-4 终审补充）

双轴终审（Standards/Spec）+ 修复波（a2e3be9）：MIGRATION_TABLES 注释与 README 措辞诚实化（字母序、V1 无外键故顺序无关、V2+ 引入 FK 须人工保序）并新增 `DataMigrationRunnerTableListTest` drift-guard（V1 表集合 == 迁移清单，集合一致性机械化）；主 yml 补 Hikari 适度参数（10/2/10s/30min，spec §3 字面项）。终审其余发现记 P2 遗留：55 处测试内联属性块可退役、test yml 共享库 DB_CLOSE_DELAY 观察项、datamigration 测试样板重复、StartupMigration 中间人、迁移报错行号以"已拷贝 N 行"近似。最终口径：`:backend:test` 455/455（120+1 套件）、`testMysql` 2/2（远程 Docker）、默认配置直连 216 启动+冒烟通过。

## 2026-09-08（计划模块 UI 样式优化改造 · feature/plan-module-ui-restyle）

已完成：

1. 按 `docs/superpowers/plans/2026-09-08-plan-module-ui-restyle.md` 六批次原样执行：A 骨架滚动（`.plan-detail` 修饰类自占满高、`.content:has(.plan-detail)` 关滚动、`.doc-main` 唯一文档滚动容器）；B 排版统一（全局 `.plan-md` 预设、Pretty 全文档阅读视图、doc-toolbar 卡片化+segmented、TOC current 态+scrollspy）；C 页头/步骤条/Tabs（`plan-head-card`、PlanPhaseStepper 24px 节点+连线+三态 pill+`aria-current="step"`、a-tabs 13px/500 覆盖）；D 结构化卡片（场景卡、`parseChecklistGroups` 仅新增、分组清单、`.plan-empty`）；E 评审/报告/发布/冲突屏重设计 + `window.prompt/confirm` 清零（BindScriptDialog 下拉+手输兜底、驳回 a-modal、跳过预检 Modal.confirm）；F 列表 badge 家族/筛选行/响应式断点（≤1280 TOC 180、≤1100 单列横向 chips）。
2. 运行时冒烟全链路（真实栈：复用 8080 后端 + vite dev，详见 `docs/superpowers/plans/2026-09-08-plan-module-ui-restyle-review.md`）：新建计划→整篇/章节编辑保存→冲突三选一（服务端并发 rev 构造 409→采纳本地版）→TOC 双模式跳转（公式落点 8px 精确）→提交评审→批注→通过→关联脚本 #18→执行 #183 SUCCESS→报告 verdict（无法判定态）→发布→分享创建/复制降级提示/撤销→分享只读页；12 张截图归档 `docs/superpowers/screenshots/2026-09-08-plan-ui-restyle/`。
3. 冒烟实测修复三处：scrollspy watch `immediate`（初始 TOC 高亮缺失）、TaskPlanDetail 变更后重载场景列表（绑定徽标滞留）、执行状态文案中文映射（原恒等直出枚举）。
4. ui-ux-pro-max 三轮 review（记录文档同目录 `-review.md`）：R1 设计系统（token 派生 `--plan-{accent,ok,warn,danger}-text` color-mix 配比数值验证双主题全过 4.5:1、phase-badge 圆点化）；R2 交互（toc 链接键盘可达、标题层级、reduced-motion、doc-main region、segmented 方向键）；R3 视觉还原（§3.2 显式数值优先回退 R1 阶梯化误改，逐项核对一致）。
5. code-review 双轴终审：修复 Spec 轴 P0（R1 `:root` 块插入切进头注释致批次 A 滚动规则被吞——PostCSS 复核恢复）、md-editor 覆盖收敛 `.plan-md` 家族、批次 F 两弹窗紧凑栅格补位；Standards 轴四条硬约束全过，判断性气味记录于 review 文档不处理。

提交（8 个）：

1. `157c319` feat：批次A 骨架与滚动
2. `aad3bf5` feat：批次B 排版统一
3. `9705d33` feat：批次C 页头/步骤条/Tabs
4. `b37b6ff` feat：批次D 结构化卡片
5. `61ac42c` feat：批次E 评审/报告/发布/冲突屏
6. `0503223` feat：批次F 收尾
7. `ebbe5b4` fix：冒烟实测三处修复 + 12 截图归档
8. `bd6b6c4` fix：三轮 review 修复 + review 记录文档
9. （终审修复 commit 见下）

验证：

1. 每批次 + 每轮 review 后 `npm run build`（vue-tsc + vite）真实执行全绿（最终 8.06s）。
2. 运行时冒烟在真实栈完成（上表清单）；无头 IAB 环境限制（rAF 挂起/剪贴板/ant-select 弹层）以等价驱动方式验证并在 review 文档标注。
3. 终审 P0 修复后 PostCSS 解析复核 `.content:has(.plan-detail)` 规则独立存在，浏览器实测 `.content` overflow:hidden + `.doc-main` 可滚动。
4. 硬约束自查：plan-markdown.ts 仅新增、usePlanDoc.ts 零改动、package.json 零改动、后端零改动、裸 hex 零违规。

遗留（P2/P3，记录不处理）：

1. 结论达成表（backfill:verdict）虚线强调为计划标注"可选增强"，未实施。
2. 状态→pill 类映射三形、`.status-pill`/`.sc-status-pill` 双家族、`latestStatusText` 与 `executionStatusText` 词表分叉、checkbox 行解析第三处重复（受"不修改既有函数"约束）。
3. BindScriptDialog 下拉选择路径在无头环境无法展开验证（手输兜底路径已实测），真实浏览器待人工复核。
