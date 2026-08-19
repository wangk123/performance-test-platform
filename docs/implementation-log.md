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
