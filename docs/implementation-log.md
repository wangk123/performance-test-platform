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
