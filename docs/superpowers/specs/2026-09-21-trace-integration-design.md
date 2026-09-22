# Trace 链路追踪接入设计（P0-1）

> 状态：设计评审稿
> 日期：2026-09-21
> 范围：全链路——展示面 + 失败样本 traceId 贯通 + 容量轮/诊断轮执行配置
> 交互原型：`design-mockups/trace-integration.html`
> 缺口清单条目：`docs/data-collection-gaps.md` P0-1

## 1. 文档目的

把深度证据源 trace 从 `UnavailableDeepProbe` 占位变为真实实现，打通「压测异常 → 请求级链路下钻」的根因定位路径。本文定义选型、数据流、后端改造、界面位置与交互、API、边界与分期。

## 2. 现状与依托

- 契约已就绪：`DeepEvidenceProbe` 接口、`CorrelationKey`（executionId + 时间窗 + 目标实例 + 标签 + traceId）、可用性声明（NO_DATA / SOURCE_UNAVAILABLE / DELETED）。
- 配置已预留：`platform.evidence.deep.kinds.trace.{enabled,endpoint,retentionDays}`（`DeepEvidenceKind.TRACE` 声明 traceIdCapable=true、requiresApproval=true）。
- 失败样本管线已就绪：Groovy Failure Sample Collector 采集完整请求/响应，SQLite 按执行持久化。
- 前端执行详情页（`ExecutionDetailView.vue`）为 panel 堆叠结构，异常样本右侧已有三 Tab 详情区。

## 3. 选型决策

**SkyWalking**（OAP + UI，随 `deploy/monitoring` docker compose 部署）。

- 一套 compose 起全套（与 Prometheus 部署模式一致）；自带 UI 可 deep-link 兜底；GraphQL 查询 API 稳定；Java agent 对主流框架覆盖全。
- 例外：被测应用已挂存量 APM agent（公司统一基建）时，适配存量系统而非强推 SkyWalking——按同一 `DeepEvidenceProbe` 契约写适配器即可。
- agent 开销与压测轮次解耦（见 §6），容量轮不背观测开销。

## 4. 架构与数据流

```
被测应用（-javaagent:skywalking-agent.jar，backend_service 指向 OAP :11800）
        │ gRPC 上报
        ▼
SkyWalking OAP + UI（deploy/monitoring compose，UI :8080）
        │ GraphQL（queryBasicTraces / queryTrace）
        ▼
平台后端 SkyWalkingTraceProbe（implements DeepEvidenceProbe）
        │                                   │
        │ 实时/按需：列表 + span 树直连查询     │ 执行终态：固化 trace 摘要列表
        ▼                                   ▼
执行详情 REST / agent 面证据链        execution_trace_snapshot（对齐
                                    execution_target_metrics_snapshot 模式）
```

决策：

- **直连为主**：平台不落地全量 span，按 executionId + 时间窗 + 服务即时查询，新鲜度最好、零存储膨胀。
- **终态固化摘要**：执行结束时把 trace 列表摘要（不含 span 树）固化进 MySQL，防 OAP 保留期（默认 7 天）过后复查变空——与 Prometheus 快照同一套路。span 树详情始终按需直连。
- **可用性声明**：OAP 不可达 → `SOURCE_UNAVAILABLE`（missingReason=「OAP 不可达」），未配置 → 面板显式空态，不伪造空成功。
- GraphQL 查询面（两个够用）：`queryBasicTraces(service, start, end, minTraceDuration, traceState, order, paging)` 取列表；`queryTrace(traceId)` 取 span 树。列表查询带分页（`paging: pageNum/pageSize/needTotal`），默认 `queryOrder=BY_DURATION`（最慢优先——首页即最有诊断价值的慢请求）；超时与分页受 `PageBudget` 有界约束。

## 5. 失败样本 traceId 贯通

- **提取**（注入的 Groovy Failure Sample Collector 增强）：从响应头解析——
  1. `sw8` header：`1-<client>-<traceId>-<segmentId>-...`，第 3 段为明文 traceId；
  2. fallback `X-Trace-Id`（应用自定义回写）。
  提取到才写，低采样轮次下允许为空。
- **存储**：失败样本 SQLite `samples` 表加 `trace_id TEXT NULL` 列（建表语句升级，旧行自然为 null）；JSONL 行同步加字段。
- **查询与展示**：样本 API、详情返回带 traceId；无 traceId 的样本「查看链路」置灰，tooltip 说明「容量轮低采样，未提取到 traceId」。

## 6. 观测轮次（容量轮 / 诊断轮）

关键认知：**agent 挂载在被测应用侧，平台无法远程控制**（javaagent 只能启动时挂）。因此轮次是执行元数据 + 引导，不是远程开关：

- 场景执行设置新增「观测轮次」单选：`容量轮`（低采样 1%，数字进容量结论）/ `诊断轮`（全量 trace，数字不进容量结论），执行时可覆盖。
- 落地为执行记录配置字段（observabilityProfile: OFF / CAPACITY / DIAGNOSTIC，存配置 JSON 快照）。
- 展示：执行详情页链路追踪面板头部徽标；报告页后续随 P3-5 补。
- precheck 新增软检查：诊断轮启动前查 OAP 该服务近期是否有 trace 上报，无 → WARNING「agent 未挂载或采样率为 0」。

## 7. 界面设计与入口

> 原则：所有入口都在**执行详情页**；报告页本期不动。

| 功能 | 位置 | 交互 |
|---|---|---|
| 链路追踪面板 | 执行详情页，被测目标监控之后、异常样本工作台之前 | panel 结构与聚合报告/被测目标监控一致 |
| 数据源可用性徽标 | 面板头部 | 已连接（绿点）/ 未配置（引导文案）/ OAP 不可达（警告色 + missingReason） |
| 轮次徽标 | 面板头部 | 容量轮/诊断轮 + 采样说明 |
| 筛选工具条 | 面板头部下方 | 服务下拉（语义=**该服务参与的链路**，对齐 `queryBasicTraces(service=)`，非仅入口）、接口下拉（可搜索，按入口接口）、仅看错误开关、慢请求阈值（全部/≥200ms/≥500ms/≥1s）；时间窗默认执行区间 |
| trace 列表 | 面板主体 | 列：时间/服务/入口接口/耗时（色阶）/状态/Span 数/traceId（点击复制）；行点击开抽屉。默认按耗时降序（可切最新优先），底部分页 10/20/50 每页（对齐异常样本分页风格）——诊断轮全量采样 5 分钟可达十万级 trace，不分页不可用 |
| 链路详情抽屉 | 右侧抽屉（覆盖层） | 头部：接口名 + traceId + 状态 + 总耗时 + 「在 SkyWalking 中打开」deep-link；摘要条：总耗时/Span/服务数/错误 Span；主体：span 瀑布图（服务色块、嵌套缩进、错误红 + 标签、hover 出耗时 tooltip） |
| 服务耗时分布 | 面板主体顶部 | 按服务聚合 span 自身耗时的堆叠条（服务配色一致）；hover 出占比/参与 trace 数/错误 span 数；点击色块联动服务筛选——**跨服务链路的聚合视图**：单条链路看抽屉瀑布图，服务维度瓶颈看堆叠条（Phase 1 为入口服务维度，Phase 2 升级 span 维度） |
| SkyWalking 配置 | 设置页「观测数据源」Tab（平台级，ADMIN） | OAP GraphQL 端点、SkyWalking UI 地址（deep-link 跳转目标）、执行终态快照开关、测试连接；Phase 1 实际生效配置为 application.yml + 面板未配置态引导，配置 UI 随 Phase 2 落地 |
| 样本 traceId chip | 异常样本详情区（右侧三 Tab 上方） | 有 traceId：chip + 「查看链路」按钮（复用同一抽屉）；无：置灰 + 原因提示 |

瀑布图实现分期：Phase 1 抽屉放摘要 + SkyWalking deep-link；Phase 2 平台自研瀑布图（ECharts custom series，Gantt 式）。原型按 Phase 2 目标态绘制。

## 8. API 与配置

REST（人类 UI 面）：

- `GET /api/executions/{id}/traces?from&to&service&endpoint&onlyError&minDurationMs&sort=duration|time&page&size` —— 列表（运行中直连 OAP，已结束优先读终态快照，详情仍直连；`endpoint` 按入口接口名过滤；分页透传 OAP paging，默认 size=20、sort=duration）。
- `GET /api/executions/{id}/traces/{traceId}` —— span 树。
- 样本查询 API 响应增加 `traceId` 字段（向后兼容，可空）。

Agent 面：`SkyWalkingTraceProbe` 注册后自动进入既有 `GET /api/agent/executions/{id}/evidence` 证据链（每源独立可用性声明）。

配置：

```yaml
platform:
  evidence:
    deep:
      kinds:
        trace:
          enabled: true            # TRACE 为高影响源，显式开启即视为审批（既有语义）
          endpoint: http://skywalking-oap:12800   # OAP GraphQL 端点
          retention-days: 7
```

## 9. 错误处理与边界

| 场景 | 行为 |
|---|---|
| OAP 不可达 | 列表接口返回可用性声明（SOURCE_UNAVAILABLE + 原因），前端显式警告态 |
| 未配置 | 面板空态 + 配置引导文案，不报错 |
| OAP 数据超保留期 | 终态快照兜底列表；span 树不可得时抽屉提示「详情已过保留期」 |
| 超大 trace（span 爆炸） | span 树按 PageBudget 截断 + truncated 标记 |
| 容量轮低采样 | 样本无 traceId 属预期，置灰并说明 |
| 时钟偏移 | 复用证据链既有 clock:skew-suspected 警告 |

## 10. 分期

- **Phase 1（可上线）**：SkyWalking compose 部署文档；`SkyWalkingTraceProbe`；配置接线（application.yml）；样本 traceId 提取与存储；轮次字段与 precheck 软检查；链路追踪面板（列表 + 服务耗时分布堆叠条 + 接口筛选）+ 抽屉（摘要 + SkyWalking deep-link）+ 样本 chip 贯通。
- **Phase 2**：抽屉内置瀑布图（平台自研）；设置页「观测数据源」配置界面；报告页 trace/资源曲线（随 P3-5）。

## 11. 测试策略

- `SkyWalkingTraceProbe` 单测：GraphQL 响应解析、超时、不可达 → 可用性声明、分页截断。
- sw8 / X-Trace-Id 提取单测（含多值、格式异常 header）。
- SQLite schema 演进兼容测试（旧库无 trace_id 列可读）。
- 既有 conformance/回归不受影响（JMX 注入脚本改动仅新增字段，JMeter 导入导出兼容约束不触碰 JMX 结构）。

## 12. 原型说明

`design-mockups/trace-integration.html`（单文件、纯浏览器打开）覆盖五处可交互：链路追踪面板（筛选/排序/分页联动、可用性三态切换、行点击）、链路详情瀑布图抽屉（hover tooltip、错误 span 标注、SkyWalking deep-link 位）、异常样本 traceId 贯通（有/无 traceId 两态）、执行设置观测轮次选择器。

## 13. 原型载体约定（已拍板并落地）

用户拍板：后续设计迭代采用**真实产品内原型**，不再维护独立复刻原型。规则：

- 开关：`frontend/src/composables/usePrototype.ts`，URL `?proto=<name>`（`all` 全开），页面加载解析一次；编译期管控（`VITE_PROTO`）待补 vite-env.d.ts 后扩展。
- 新功能以 flag + mock 挂进真实页面位置；**mock 的是数据来源不是数据结构**（对齐 §8 REST 字段），实施时仅替换数据来源函数。
- 交界点用真实值喂（时间窗取真实执行区间；样本 chip 按 5xx 派生演示 traceId），并在界面保留「演示数据」标记。
- 设计探索期改动随工作树/分支管理，未定稿不合主干；功能接真实 API 后 flag 与 v-if 删除。

本次落地（2026-09-22）：

| 文件 | 职责 |
|---|---|
| `composables/usePrototype.ts` | 原型开关 |
| `components/tasks/trace/trace-mock.ts` | mock 数据 + 类型（对齐 REST 字段）+ 样本→trace 演示派生 |
| `components/tasks/trace/TracePanel.vue` | 链路追踪面板（筛选/排序/分页/可用性三态演示） |
| `components/tasks/trace/TraceDetailDrawer.vue` | 链路详情抽屉（瀑布图目标态 + SkyWalking deep-link 位） |
| `task-plans/ExecutionDetailView.vue` | 挂载：被测目标监控之后；异常样本详情 traceId chip |

查看方式：登录平台 → 任一执行详情页 URL 加 `?proto=trace`。`design-mockups/trace-integration.html` 转为存档（视觉规格书）。

2026-09-22 Phase 1 实施完成后 flag 已退役，TracePanel 常驻；HTML 原型仍为视觉规格书存档。
