# 实现任务：M3 数据链底座（T5 有界查询 + T6 关联骨架）

> 全局约束：
> - Java 17，运行后端命令须 `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`。
> - 统一验证命令：`JAVA_HOME=<jdk17> ./gradlew :backend:test`（仓库根执行）；单类过滤用 `--tests '*Xxx'`。
> - 不新增第三方依赖；不改 `/api/**` UI 接口（含失败样本 offset 分页）、`PlatformExceptionHandler`、前端。
> - agent 面包：`com.yr.perftest.platform.agent`；Facade 包：`com.yr.perftest.platform.facade`；新建 `evidence` 包：`com.yr.perftest.platform.evidence`。
> - agent controller MUST 只依赖 `facade`；游标编解码/预算裁剪/可用性组装在 facade/support 内完成。
> - TDD：每组先写失败测试→跑红→最小实现→跑绿。
> - 关键类型契约（跨任务一致）：
>   - `facade.query.PageBudget(int maxItems, long maxBytes, long maxMillis)`，默认 `1000 / 1_048_576 / 3000`。
>   - `facade.query.Availability(boolean present, Instant from, Instant to, String granularity, boolean truncated, String sourceRef, MissingReason missingReason)`；`enum MissingReason { SOURCE_UNAVAILABLE, NO_DATA, DELETED }`。
>   - `facade.query.BoundedPage<T>(List<T> items, boolean truncated, String nextCursor, List<String> warnings, Availability availability)`。
>   - `facade.query.CursorCodec`：`String encode(String payload)`（base64）/ `String decode(String cursor)`；各源自定义 payload 格式。
>   - `evidence.CorrelationKey(long executionId, Instant from, Instant to, List<String> targetInstances, String requestLabel, String traceId)`。
>   - `evidence.EvidenceSummary(CorrelationKey key, String sourceType, Availability availability, Map<String,Object> summary, String sourceRef, String sourceClock)`。

## 1. 有界查询基础设施（游标 + 预算 + 可用性）

- [x] 1.1 先失败测试：预算/游标/可用性/封套映射
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/facade/query/BoundedQuerySupportTest.java`
  - 行为：`PageBudget` 默认值正确、非法值（负 `maxItems`）可被校验方法识别为非法；`CursorCodec.encode/decode` 往返一致且输出为不透明字符串；`Availability` 缺失场景 `present=false`+`missingReason`；`ApiResponse.paged(...)` 能填 `truncated`/`nextCursor`/`warnings`（`success/error` 旧行为不变）。
  - 验证：`... --tests '*BoundedQuerySupportTest'` → 编译失败/红。

- [x] 1.2 实现基础设施类型 + 封套分页工厂
  - Files（Create）：`.../facade/query/PageBudget.java`、`.../facade/query/Availability.java`（含 `MissingReason`）、`.../facade/query/BoundedPage.java`、`.../facade/query/CursorCodec.java`
  - Files（Modify）：`backend/src/main/java/com/yr/perftest/platform/agent/contract/ApiResponse.java`（新增静态 `paged(requestId, schemaVersion, data, warnings, truncated, nextCursor)`，不改字段与既有 `success/error`）
  - 行为：见类型契约；`CursorCodec` 用 base64 编码 payload；预算校验方法拒绝非法参数。
  - 验证：`... --tests '*BoundedQuerySupportTest'` → 绿。

## 2. 失败样本 keyset 游标端点（先打通最全雏形）

- [x] 2.1 先失败测试：`GET /api/agent/executions/{id}/failure-samples`
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/agent/AgentFailureSampleApiTest.java`
  - 行为：有效身份分页请求 → 200 + 封套；样本按 id keyset 有序；达 `maxItems` 或 `maxBytes` → `truncated=true`+`nextCursor`+`warnings` 含触顶维度（`budget:items`/`budget:bytes`）；用 `nextCursor` 续拉不重不漏；非法预算参数 → 400 `VALIDATION_FAILED`；不存在 execution → 404 `NOT_FOUND`；无身份 → 401。
  - 验证：`... --tests '*AgentFailureSampleApiTest'` → 红。

- [x] 2.2 实现 Facade 失败样本有界查询方法
  - Files（Create）：`.../facade/DataFacade.java` 新增 `queryFailureSamples(long executionId, String cursor, PageBudget budget)` 返回 `BoundedPage<...>`（复用现有 `FailureSampleStore.listSummariesAfter(lastEventId, limit)` 作 keyset 取数，源不可达→`DATA_SOURCE_UNAVAILABLE`+`Availability.present=false`）
  - 行为：经 `FacadeGuard` 主体校验；游标 payload 记 `lastId`；按三维预算累加裁剪（字节按已序列化条目近似，触顶回退最后一条到游标，宁少不超发）；组装 `Availability`（`sourceRef`=DB 文件+id 段）。
  - 验证：`... --tests '*AgentFailureSampleApiTest'`（controller 未建前仅编译/facade 单测）→ 部分绿。

- [x] 2.3 实现 agent 端点
  - Files（Create）：`.../agent/execution/AgentFailureSampleController.java`（`@RequestMapping("/api/agent/executions")`，仅依赖 `DataFacade`，映射 `BoundedPage`→`ApiResponse.paged`）
  - 验证：`... --tests '*AgentFailureSampleApiTest'` → 全绿。

## 3. 聚合行有界端点

- [x] 3.1 先失败测试：`GET /api/agent/executions/{id}/aggregate`
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/agent/AgentAggregateApiTest.java`
  - 行为：有效身份 → 200 + 封套聚合行；稳定序号 keyset 游标续拉正确；超预算截断+游标+warnings；无聚合结果（源存在但无数据）→ `Availability.present=false`+`missingReason=NO_DATA`（非报错、非空成功伪装）；不存在 execution → 404。
  - 验证：`... --tests '*AgentAggregateApiTest'` → 红。

- [x] 3.2 实现 Facade 聚合行方法 + 端点
  - Files（Modify）：`.../facade/DataFacade.java` 新增 `queryAggregateRows(...)`（复用 `ScenarioExecutionService.getResult` 的 `AggregateRow`，游标 payload 记稳定序号）
  - Files（Create）：`.../agent/execution/AgentAggregateController.java`（仅依赖 `DataFacade`）
  - 验证：`... --tests '*AgentAggregateApiTest'` → 绿。

## 4. 秒级指标有界端点（时间窗 + 粒度）

- [x] 4.1 先失败测试：`GET /api/agent/executions/{id}/metrics/series`
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/agent/AgentMetricSeriesApiTest.java`
  - 行为：入参含时间窗 + 粒度（granularity）；返回 `MetricSeries` 点按 `MetricSeriesPoint.ts` keyset 游标；`Availability.timeRange` 反映实际覆盖、`granularity` 回填；超预算截断+游标；请求窗内无点→`present=false`+`NO_DATA`（不静默替代其他窗）。
  - 验证：`... --tests '*AgentMetricSeriesApiTest'` → 红。

- [x] 4.2 实现 Facade 秒级指标方法 + 端点
  - Files（Modify）：`.../facade/DataFacade.java` 新增 `queryMetricSeries(long executionId, Instant from, Instant to, String granularity, String cursor, PageBudget budget)`
  - Files（Create）：`.../agent/execution/AgentMetricSeriesController.java`
  - 行为：复用现有秒级指标来源（`MetricSeriesService`/`AggregateReportService` 秒级产物）；禁止用请求窗外数据填充。
  - 验证：`... --tests '*AgentMetricSeriesApiTest'` → 绿。

## 5. Prometheus 有界端点（监控绑定拼 promql，拒绝裸 promql）

- [x] 5.1 先失败测试：`GET /api/agent/executions/{id}/prometheus`
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/agent/AgentPrometheusApiTest.java`
  - 行为：入参为指标选择（绑定项/枚举）+ 时间窗 + 粒度（step），**无 promql 入参**；平台按 `ExecutionMonitorBindingService` 绑定内部拼 promql 调 `PrometheusQueryClient.queryRange`；点数超预算→按时间窗切分游标续拉（游标记下一段 start，step 对齐）；Prometheus 不可达→`DATA_SOURCE_UNAVAILABLE`+`present=false`+`SOURCE_UNAVAILABLE`；若接口暴露了任何接受裸 promql 的路径则测试失败。
  - 验证：`... --tests '*AgentPrometheusApiTest'` → 红。

- [x] 5.2 实现 Facade Prometheus 方法 + 端点
  - Files（Modify）：`.../facade/DataFacade.java` 新增 `queryPrometheus(long executionId, String metricSelector, Instant from, Instant to, int stepSeconds, String cursor, PageBudget budget)`
  - Files（Create）：`.../agent/execution/AgentPrometheusController.java`（入参 DTO 无 promql 字段）
  - 行为：facade 内借监控绑定生成 promql（调用方不可注入）；时间窗切分游标；`sourceRef`=promql+step。
  - 验证：`... --tests '*AgentPrometheusApiTest'` → 绿。

## 6. 分层守护扩展

- [x] 6.1 先失败测试：新端点不直连底层资源
  - Files（Modify Test）：`backend/src/test/java/com/yr/perftest/platform/agent/AgentLayeringConstraintTest.java`（扩展覆盖 6.x 全部新 controller）
  - 行为：断言四个新 controller 不直接依赖 `*Repository`、`java.nio.file`、`PrometheusQueryClient`，只经 `facade`。
  - 验证：`... --tests '*AgentLayeringConstraintTest'` → 红（若有越界）。

- [x] 6.2 修正越界依赖使守护通过
  - Files（Modify）：按测试反馈调整 controller import
  - 验证：`... --tests '*AgentLayeringConstraintTest'` → 绿。

## 7. 关联键 + evidence 适配层（T6）

- [x] 7.1 先失败测试：跨源按关联键对齐
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/evidence/EvidenceCorrelationTest.java`
  - 行为：以 `executionId`+时间窗构造 `CorrelationKey`，`EvidenceService` 能对齐聚合/秒级/失败样本/Prometheus 四源，返回各 `EvidenceSummary` 携带同一 key；指定目标实例时仅对齐该实例；`EvidenceSummary` 只含摘要+`sourceRef`，不内联原始大数据；未声明关联键绑定的源不接入。
  - 验证：`... --tests '*EvidenceCorrelationTest'` → 红。

- [x] 7.2 实现关联键 + 证据源接口 + 适配器 + 服务
  - Files（Create）：`.../evidence/CorrelationKey.java`、`.../evidence/EvidenceSummary.java`、`.../evidence/EvidenceSource.java`（`boolean supports(CorrelationKey)` + `EvidenceSummary summarize(CorrelationKey, PageBudget)`）、各源适配器（execution/aggregate/series/failure-sample/prometheus）、`.../evidence/EvidenceService.java`
  - 行为：适配器复用 facade/既有 service 取数，声明其可填充维度；`EvidenceService` 汇聚各源并强制关联键绑定校验。
  - 验证：`... --tests '*EvidenceCorrelationTest'` → 绿。

- [x] 7.3 校验：evidence 不对外暴露端点
  - Files（Test）：同 `EvidenceCorrelationTest` 或分层测试补一条
  - 行为：断言本变更新增的 agent 面端点中不存在直接返回 `EvidenceSummary` 的对外端点。
  - 验证：`... --tests '*EvidenceCorrelationTest'` → 绿。

## 8. 时钟对齐告警 + 证据失效

- [x] 8.1 先失败测试：偏移告警与失效语义
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/evidence/EvidenceClockAndLifecycleTest.java`
  - 行为：execution 窗与 Prometheus 覆盖窗错位超阈值（默认 2×step）→ `warnings` 含 `clock:skew-suspected` 且时间戳不被修改；每条 `EvidenceSummary` 记 `sourceClock`（load/target/prometheus）；对已删除 execution 请求证据 → `present=false`+`missingReason=DELETED`（非空成功伪装）。
  - 验证：`... --tests '*EvidenceClockAndLifecycleTest'` → 红。

- [x] 8.2 实现偏移检测 + sourceClock 标注 + 失效判定
  - Files（Modify）：`.../evidence/EvidenceService.java` 及相关适配器
  - 行为：仅告警不校正；execution 缺失/删除→标注失效。
  - 验证：`... --tests '*EvidenceClockAndLifecycleTest'` → 绿。

## 9. 收尾验证

- [x] 9.1 全量后端测试通过（证明零回归：UI/认证/M2 用例仍绿）
  - 验证：`JAVA_HOME=<jdk17> ./gradlew :backend:test` 全绿。
- [x] 9.2 OpenAPI 含新增 agent 端点（复用 M2 springdoc 分组，不改配置）
  - Files（Test）：可在既有 `AgentOpenApiTest` 补断言，或新增最小断言
  - 验证：`... --tests '*AgentOpenApiTest'` → 绿。
- [x] 9.3 `openspec validate add-m3-data-chain --strict` 通过
