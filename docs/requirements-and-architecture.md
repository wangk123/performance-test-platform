# 性能测试平台 - 需求与架构总览

本文档作为需求和设计入口，详细需求、数据模型和接口设计按模块拆分到 `docs/modules/`。后续调整详细设计时优先修改模块文档，避免所有内容继续堆叠在单一大文档中。

## 1. 产品定位

性能测试平台面向内部测试团队和项目研发团队，提供从性能脚本管理、测试执行、过程状态查看到测试报告生成的统一工作台。长期目标是形成可沉淀、可追溯、可扩展的性能工程平台；首版仍按核心压测闭环推进。

核心闭环：

```text
创建项目 -> 上传 JMX 脚本 -> 配置执行参数 -> 提交执行
-> 查看任务状态 -> 生成报告 -> 下载或分享报告
```

## 2. 目标用户

| 角色 | 主要诉求 | 典型操作 |
|------|----------|----------|
| 测试负责人 | 统一管理项目和测试资产，查看报告结论 | 创建项目、分配成员、查看报告 |
| 性能测试工程师 | 上传脚本、配置参数、发起执行、分析结果 | 管理 JMX、提交任务、下载报告 |
| 研发工程师 | 查看压测结果和错误信息，辅助定位问题 | 查看执行结果、下载报告 |
| 平台管理员 | 管理用户、角色、系统配置和运行风险 | 用户管理、权限配置、系统配置 |

## 3. 范围分层

### 3.1 MVP 必做

| 模块 | 文档 | 范围 |
|------|------|------|
| 系统管理 | [01-system-management.md](modules/01-system-management.md) | 登录、用户、角色、基础审计 |
| 项目管理 | [02-project-management.md](modules/02-project-management.md) | 项目 CRUD、归档、成员、基础权限 |
| 脚本管理 | [03-script-management.md](modules/03-script-management.md) | JMX 上传、版本、默认参数 |
| 测试执行 | [04-test-execution.md](modules/04-test-execution.md) | 执行配置、单机队列、状态、取消、日志 |
| 报告管理 | [05-report-management.md](modules/05-report-management.md) | 指标统计、Markdown/HTML 报告、下载分享 |

### 3.2 Phase 2 增强

| 模块 | 文档 | 范围 |
|------|------|------|
| 监控采集与报告增强 | [06-monitoring.md](modules/06-monitoring.md) | 资源指标采集、趋势图、报告对比、PDF/Word 导出 |
| 辅助脚本 | [09-auxiliary-scripts.md](modules/09-auxiliary-scripts.md) | 前置/后置脚本、执行日志、失败策略 |

### 3.3 远期扩展

| 模块 | 文档 | 范围 |
|------|------|------|
| 造数工厂 | [07-test-data-factory.md](modules/07-test-data-factory.md) | 数据模板、生成规则、导出 |
| 函数库 | [08-function-library.md](modules/08-function-library.md) | Groovy 函数管理、调试、版本 |
| Git、日志与 AI 分析 | [10-git-log-ai.md](modules/10-git-log-ai.md) | 代码关联、日志检索、AI 分析 |
| 分布式执行 | [11-distributed-execution.md](modules/11-distributed-execution.md) | 执行节点、分布式调度、结果聚合 |

## 4. 统一需求格式

模块文档中的 MVP 需求统一使用以下格式：

- 编号：`REQ-模块-序号`
- 用户故事：谁在什么场景下要完成什么目标。
- 功能说明：平台需要提供的能力。
- 输入/输出：关键输入和系统输出。
- 异常场景：必须处理的失败或边界情况。
- 验收标准：可以通过页面、接口、数据记录或文件结果验证的条件。

## 5. 统一术语

| 术语 | 定义 |
|------|------|
| 测试任务 | 用户提交的一次压测业务请求，绑定项目、脚本版本和执行配置 |
| 执行记录 | 测试任务的一次实际执行实例，记录状态、时间、日志和结果 |
| 执行结果 | JMeter 输出的原始结果文件、日志和聚合摘要 |
| 测试报告 | 基于执行结果生成的 Markdown、HTML 或后续扩展格式报告 |
| 脚本版本 | 某次上传后的不可变 JMX 文件版本 |
| 执行配置 | 线程数、循环次数、持续时间、Ramp-Up、目标环境等参数快照 |

## 6. 需求驱动架构

MVP 采用 Spring Boot 精简单体 + Vue 3 工作台。架构目标不是提前引入复杂分布式能力，而是让项目、脚本、执行、报告和权限数据先形成稳定边界，并通过接口抽象支撑后续替换。

```text
Vue 3 + TypeScript + Element Plus
        |
        | HTTP / 后续 WebSocket
        v
Spring Boot 3 应用层
        |
        v
领域模块：System / Project / Script / Execution / Report
        |
        v
基础设施：H2/MySQL、本地文件、JMeter、报告渲染
```

### 6.1 技术选型

| 分类 | MVP 选型 | 需求依据 | 后续扩展 |
|------|----------|----------|----------|
| 后端框架 | Spring Boot 3.x | 单体分层、事务控制、JMeter Java 生态 | Spring Cloud 微服务 |
| 前端框架 | Vue 3 + TypeScript | 企业后台、复杂表单、状态页面 | 微前端 |
| UI 组件库 | Element Plus | 后台组件完整，开发效率高 | 保持一致，不频繁替换 |
| 数据库 | H2 开发 / MySQL 生产 | MVP 关系模型明确，开发部署简单 | 分库分表 |
| 文件存储 | 本地文件系统 | JMX、日志、结果、报告低成本存储 | MinIO、OSS、COS |
| 任务调度 | Spring Async + 数据库任务表 | 单机队列满足首版 | MQ、分布式调度 |
| 测试引擎 | JMeter API 或 CLI 适配器 | 直接执行 JMX，符合目标场景 | Gatling、K6 |
| 报告生成 | Markdown + HTML | 易读、易下载、易生成 | Word、PDF、模板引擎 |

### 6.2 核心接口抽象

| 能力 | 接口 | MVP 实现 | 扩展方向 |
|------|------|----------|----------|
| 文件存储 | `FileStorage` | 本地文件系统 | MinIO、OSS、COS |
| 任务调度 | `TaskScheduler` | 数据库状态 + Spring Async | MQ、分布式调度 |
| 测试执行 | `TestExecutor` | JMeter API 或 CLI | Gatling、K6 |
| 报告生成 | `ReportGenerator` | Markdown/HTML 生成器 | Word/PDF 生成器 |
| 权限校验 | `PermissionService` | 角色 + 项目成员 | 组织架构、细粒度权限 |

## 7. 核心实体关系

```text
User 1..n ProjectMember n..1 Project
Project 1..n Script 1..n ScriptVersion
ScriptVersion 1..n TestTask 1..n TaskExecution 1..1 ExecutionResult
TaskExecution 1..n TestReport
```

各实体字段、状态流转和接口设计见对应模块文档。

## 8. 分阶段计划

阶段计划独立维护在 [development-plan.md](development-plan.md)，实现过程记录维护在 [implementation-log.md](implementation-log.md)。

## 9. 评审清单

1. 每个模块文档必须明确“职责边界、需求条目、关键实体、接口草案、状态流转、验收标准”。
2. MVP 模块必须至少包含 1 条可验收需求。
3. 监控、AI、造数、函数库、Git、辅助脚本、分布式执行不得反向增加 MVP 实现复杂度。
4. 技术选型必须能解释它服务的需求，不单独堆砌扩展能力。
5. 按核心闭环走查时，项目、脚本、执行、报告和权限不得出现责任断点。
