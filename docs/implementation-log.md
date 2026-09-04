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

## 2026-09-04（P0-2 ② MCP 工具目录页）

已完成：

1. 后端 `GET /api/mcp/tools`（`api/McpDirectoryController`）：直接映射内存 `McpToolRegistry` 单一事实源，固定规范 stage 序列（PLAN→NAVIGATE→DESIGN→OBSERVE→DIAGNOSE→VERIFY→CAPTURE）排序，登录可读；`McpTool` 契约补 `default usageExample()`（存量 8 工具零改动）。
2. 前端 `/mcp-tools` 顶级路由 + 全局导航「MCP 工具」入口：接入指引横幅（Claude Code / DSH 配置片段一键复制、API Key 申请入口），阶段筛选 tabs + 本地搜索 + 单一平铺卡片网格（两态状态图标、写权限徽标），接口文档式详情抽屉（inputSchema 参数表 + 使用示例），页脚收尾；对照 `mcp-directory-prototype.html` 视觉基准实现。
3. ① 计划工具 ×5 与 ③ perf-plan skill 依赖 P0-1（未开发），按 spec §8 保持延后跟踪。

验证：

1. `McpDirectoryControllerTest`（401 / 与 registry 严格一致 / 序列排序 / 字段口径）+ `McpServerApiTest` 回归全绿；`gradle :backend:test` 全量通过。
2. `npm run build`（vue-tsc + vite）零错误；bootRun + curl 端到端冒烟（登录读取目录、匿名 401）通过。
