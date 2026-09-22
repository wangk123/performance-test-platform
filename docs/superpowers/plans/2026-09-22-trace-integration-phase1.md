# Trace 链路追踪接入（Phase 1）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 trace 深度源从 `UnavailableDeepProbe` 占位变为真实 SkyWalking 实现：GraphQL 探针 + 执行详情 REST + 失败样本 traceId 贯通 + 观测轮次标记，前端原型（`?proto=trace`）换真数据。

**Architecture:** 直连查询为主（OAP GraphQL）+ 执行终态固化摘要快照兜底（对齐 Prometheus 快照模式）；失败样本管线加响应头提取；轮次为执行配置元数据（非远程开关）。前端复用已挂载的 TracePanel/TraceDetailDrawer，仅替换数据来源。

**Tech Stack:** Spring Boot 3.5（JDK `java.net.http.HttpClient`，不新增依赖）、Flyway V14、Vue 3 + TS（不新增依赖）、SkyWalking OAP/UI 9.7（docker compose）。

**Spec:** `docs/superpowers/specs/2026-09-21-trace-integration-design.md`（本计划从 spec 立论，执行者需同时读 spec）

## Global Constraints

- 后端构建/测试：`JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew backend:test`（已排除 `mysql` tag）；不新增依赖（无 MockWebServer/WireMock/Groovy-test）。
- 前端验证：`cd frontend && npx vue-tsc --noEmit && npm test`；无 @vue/test-utils——组件逻辑抽纯函数 ts 模块测试。
- 行数上限：Java/前端组件 500 行；共用逻辑抽 utils/helpers。
- 用户可见文案中文，技术名词英文。
- TRACE 是高影响源：`application.yml` 默认 `enabled=false`，显式配置即审批（既有语义，不得改为默认开）。
- 既有 API/字段不破坏：`ExecutionConfig` 是 record，新字段追加尾部，靠 `@JsonIgnoreProperties(ignoreUnknown=true)` 兼容旧 JSON。
- JMX 注入只改 JSR223 脚本文本，不动 JMX 结构；每个改注入器的任务必须跑 `ExecutionScriptAssemblerTest`。
- commit 格式：`<type>：<描述>`（feat/fix/chore/docs…）。
- 本地服务注意：系统代理 7897 会拦 localhost，curl 探活加 `--noproxy '*'`。

## Review Focus

1. **OAP 不可达/超时**：列表 REST 应返回 HTTP 200 + `available=false` + `missingReason=source-unavailable`，agent 证据链报 `SOURCE_UNAVAILABLE`，绝不 5xx。—— Task 3/4 用 `http://127.0.0.1:1` + 短超时套路（样板 `AgentEvidenceDeepProbeApiTest`）。
2. **sw8/X-Trace-Id 响应头变体**：大小写混写、多行同名头、sw8 段数 <3、值为空、两者皆无——提取一律返回 null，绝不抛异常中断采样。—— Task 5 `TraceIdHeaderExtractorTest` 变体矩阵。
3. **旧失败样本 SQLite 无 `trace_id` 列**：历史执行库文件在 `initialize()` 后可继续写入/读取，旧行 traceId 读出 null。—— Task 5 先建旧 schema 再升级的测试。
4. **终态执行 + OAP 保留期外**：列表必须从终态快照出数据；span 详情不可得时返回 `available=false`（`retention-expired`）而非空成功。—— Task 4/6 组合测试（插入快照 + 不可达 endpoint）。
5. **旧执行 configJson 无 `observabilityProfile`**：反序列化 null → 归一 `OFF`，REST 序列化与前端徽标不炸。—— Task 7 `normalizeConfig` 测试。

---

### Task 1: SkyWalking 部署 compose 与 README

**Files:**
- Create: `deploy/monitoring/skywalking/docker-compose.yml`
- Modify: `README.md`（监控方案章节）

**Interfaces:**
- Consumes: 无
- Produces: 本地 OAP GraphQL `http://localhost:12800/graphql`、UI `http://localhost:8080`（Task 2/7 验证依赖）

- [ ] **Step 1: 写 compose（OAP 用 H2 存储，单机零依赖；ES 选项注释保留）**

```yaml
# deploy/monitoring/skywalking/docker-compose.yml
services:
  skywalking-oap:
    image: apache/skywalking-oap-server:9.7.0
    ports: ["12800:12800", "11800:11800"]
    environment:
      SW_STORAGE: h2
      SW_HEALTH_CHECKER: default
      # 生产换 ES：SW_STORAGE: elasticsearch + SW_STORAGE_ES_CLUSTER_NODES: es:9200
    healthcheck:
      test: ["CMD", "sh", "-c", "curl -sf http://localhost:12800/healthcheck || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
  skywalking-ui:
    image: apache/skywalking-ui:9.7.0
    ports: ["8080:8080"]
    environment:
      SW_OAP_ADDRESS: http://skywalking-oap:12800
    depends_on:
      skywalking-oap:
        condition: service_healthy
```

- [ ] **Step 2: 校验 compose 语法**

Run: `docker compose -f deploy/monitoring/skywalking/docker-compose.yml config --quiet`
Expected: 退出码 0。

- [ ] **Step 3: README「监控方案」补通道三**

在「通道二」小节后追加「通道三：链路追踪（SkyWalking）」：compose 启动命令、被测应用 `-javaagent` 挂载与采样率配置示例（容量轮 ≤1%、诊断轮全量）、平台侧 `platform.evidence.deep.kinds.trace.*` 配置键表（enabled/endpoint/retention-days）。内容取自 spec §3/§6/§8。

- [ ] **Step 4: Commit**

```bash
git add deploy/monitoring/skywalking/docker-compose.yml README.md
git commit -m "feat：SkyWalking OAP/UI 部署 compose 与 README 通道三说明"
```

---

### Task 2: SkyWalking GraphQL 客户端与响应解析

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/evidence/deep/SkyWalkingTraceModels.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/evidence/deep/SkyWalkingGraphqlClient.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/evidence/deep/SkyWalkingQueryException.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/evidence/deep/SkyWalkingTraceModelsTest.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/evidence/deep/SkyWalkingGraphqlClientTest.java`

**Interfaces:**
- Consumes: `DeepEvidenceProperties.forKind(TRACE).getEndpoint()`（既有）。
- Produces（后续任务的依赖契约）:
  - `record TraceBrief(String traceId, String service, String endpointName, long startEpochMs, long durationMs, boolean isError, int spanCount)`
  - `record TraceSpanView(String segmentId, int spanId, int parentSpanId, String service, String endpointName, long startTimeMillis, long endTimeMillis, boolean isError, String errorMessage)`
  - `record TraceDetail(TraceBrief brief, List<TraceSpanView> spans)`
  - `SkyWalkingGraphqlClient#queryBasicTraces(String service, String endpointName, Instant from, Instant to, Long minDurationMs, Boolean onlyError, boolean orderByDuration, int pageNum, int pageSize) → List<TraceBrief>`（异常抛 `SkyWalkingQueryException extends RuntimeException`）
  - `SkyWalkingGraphqlClient#queryTrace(String traceId) → TraceDetail`

- [ ] **Step 1: 写解析失败测试（纯函数）**

```java
// SkyWalkingTraceModelsTest.java
class SkyWalkingTraceModelsTest {
  @Test
  void parsesBasicTracesFixture() {
    String body = """
      {"data":{"queryBasicTraces":{"traces":[
        {"traceIds":["a1b2"],"endpointNames":["POST /api/checkout"],"service":"api-gateway",
         "duration":2034,"start":"2026-09-22 143541","isError":true}
      ],"total":1}}}""";
    var list = SkyWalkingTraceModels.parseBasicTraces(body);
    assertThat(list).hasSize(1);
    assertThat(list.get(0).traceId()).isEqualTo("a1b2");
    assertThat(list.get(0).isError()).isTrue();
    assertThat(list.get(0).durationMs()).isEqualTo(2034);
  }

  @Test
  void parsesTraceSpanTreeFixture() {
    String body = """
      {"data":{"queryTrace":{"segments":[
        {"service":"api-gateway","endpointNames":["GatewayFilterChain"],"spans":[
          {"spanId":0,"parentSpanId":-1,"endpointName":"GatewayFilterChain",
           "startTime":1700000000000,"endTime":1700000002034,"isError":false}
        ]}
      ]}}}""";
    var detail = SkyWalkingTraceModels.parseTrace(body);
    assertThat(detail.spans()).hasSize(1);
    assertThat(detail.spans().get(0).parentSpanId()).isEqualTo(-1);
  }

  @Test
  void malformedJsonThrowsQueryException() {
    assertThatThrownBy(() -> SkyWalkingTraceModels.parseBasicTraces("{\"data\":null}"))
      .isInstanceOf(SkyWalkingQueryException.class);
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew backend:test --tests '*SkyWalkingTraceModelsTest*'`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现 models + client**

`SkyWalkingTraceModels.java`：final 工具类，含上述三个 record + `ObjectMapper`（static，`FAIL_ON_UNKNOWN_PROPERTIES` 关闭）+ 两个静态解析方法（`data.queryBasicTraces.traces[]` 逐条取 `traceIds[0]`/`endpointNames[0]`/`service`/`duration`/`start`（`yyyy-MM-dd HHmmss` 解析回 epochMs，失败取 0）/`isError`；`spanCount` 缺省 0）。

`SkyWalkingGraphqlClient.java`（照 `PrometheusQueryClient` 模式：JDK HttpClient + 固定 connect 3s / request 10s）：

```java
@Component
public class SkyWalkingGraphqlClient {
  private static final DateTimeFormatter SW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss");
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final String endpoint; // 来自 properties.forKind(TRACE).getEndpoint()

  public SkyWalkingGraphqlClient(DeepEvidenceProperties properties) { ... }

  public List<TraceBrief> queryBasicTraces(...) {
    var condition = Map.of("service", nvl(service), "endpointName", nvl(endpointName),
      "queryDuration", Map.of("start", SW_TIME.format(from.atZone(ZoneId.systemDefault())),
                              "end", SW_TIME.format(to.atZone(ZoneId.systemDefault())), "step", "SECOND"),
      "queryOrder", orderByDuration ? "BY_DURATION" : "BY_START_TIME",
      "minTraceDuration", minDurationMs == null ? 0 : minDurationMs,
      "traceState", Boolean.TRUE.equals(onlyError) ? "ERROR" : "ALL",
      "paging", Map.of("pageNum", pageNum, "pageSize", pageSize), "queryDurationStep", "SECOND");
    String resp = post("""
      query basicTraces($condition: TraceQueryCondition) {
        queryBasicTraces(condition: $condition) {
          traces { traceIds endpointNames service duration start isError }
        }
      }""", Map.of("condition", condition));
    return SkyWalkingTraceModels.parseBasicTraces(resp);
  }

  public TraceDetail queryTrace(String traceId) {
    String resp = post("""
      query trace($traceId: ID!) {
        queryTrace(traceId: $traceId) {
          segments { service endpointNames
            spans { spanId parentSpanId endpointName startTime endTime isError } }
        }
      }""", Map.of("traceId", traceId));
    return SkyWalkingTraceModels.parseTrace(resp);
  }
  // post(): POST {endpoint}/graphql, Content-Type application/json
  //        body {"query": q, "variables": v}；非 200 / "errors" 非空 / 解析异常 → SkyWalkingQueryException
}
```

> 注：GraphQL 字段名以部署的 OAP 9.7 实际 schema 为准。Task 1 compose 起服后 Step 6 手工 curl 校验，字段有出入只改 query 字符串，不改本任务的类型契约。

- [ ] **Step 4: 跑解析测试通过**

Run: 同 Step 2。Expected: PASS。

- [ ] **Step 5: 客户端不可达测试**

```java
// SkyWalkingGraphqlClientTest.java
class SkyWalkingGraphqlClientTest {
  @Test
  void unreachableEndpointThrowsWithinTimeout() {
    var props = new DeepEvidenceProperties();
    props.forKind(DeepEvidenceKind.TRACE).setEndpoint("http://127.0.0.1:1");
    var client = new SkyWalkingGraphqlClient(props);
    assertThatThrownBy(() -> client.queryBasicTraces(null, null,
        Instant.now().minusSeconds(60), Instant.now(), null, null, true, 1, 20))
      .isInstanceOf(SkyWalkingQueryException.class);
  }
}
```

Run + 确认 PASS（1s 内失败）。

- [ ] **Step 6:（可选，若本地已起 Task 1 compose）对真 OAP curl 校验字段名**

```bash
curl --noproxy '*' -s http://localhost:12800/graphql -H 'Content-Type: application/json' \
  -d '{"query":"{queryBasicTraces(condition:{queryDuration:{start:\"20260101000000\",end:\"20261231235959\",step:SECOND},paging:{pageNum:1,pageSize:1}}){total}}"}'
```
Expected: `{"data":{"queryBasicTraces":{"total":0}}}`；报字段错误则修 query 字符串后重跑 Step 4。

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/evidence/deep/ backend/src/test/java/com/yr/perftest/platform/evidence/deep/
git commit -m "feat：SkyWalking GraphQL 客户端与 trace 解析（queryBasicTraces/queryTrace）"
```

---

### Task 3: SkyWalkingTraceProbe 替换占位 + agent 证据链

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/evidence/deep/SkyWalkingTraceProbe.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/evidence/deep/DeepEvidenceConfiguration.java:33-40`（trace bean 换真实探针）
- Test: `backend/src/test/java/com/yr/perftest/platform/evidence/deep/SkyWalkingTraceProbeTest.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/agent/AgentEvidenceTraceSourceApiTest.java`（样板：`AgentEvidenceDeepProbeApiTest`）

**Interfaces:**
- Consumes: Task 2 的 `SkyWalkingGraphqlClient`、既有 `DeepEvidenceProbe/DeepProbeResult/Availability/PageBudget/CorrelationKey`。
- Produces: `SkyWalkingTraceProbe`（`@Bean`，Task 4 的 service 注入它取 endpoint 可用性）。

- [ ] **Step 1: 写探针单测（mock client）**

```java
class SkyWalkingTraceProbeTest {
  @Test
  void listPathBuildsAvailabilityAndBudgetTruncation() {
    var client = Mockito.mock(SkyWalkingGraphqlClient.class);
    when(client.queryBasicTraces(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(1), eq(100)))
      .thenReturn(List.of(new TraceBrief("t1", "api-gateway", "GET /a", 1L, 500, false, 3)));
    var probe = new SkyWalkingTraceProbe(client);
    var result = probe.probe(new CorrelationKey(1L, Instant.now().minusSeconds(60), Instant.now(),
        List.of(), null, null), PageBudget.defaults());
    assertThat(result.availability().present()).isTrue();
    assertThat(result.availability().missingReason()).isNull();
  }

  @Test
  void emptyResultReportsNoData() { /* client 返回空列表 → present=false, NO_DATA */ }

  @Test
  void runtimeFailureReportsSourceUnavailable() { /* client 抛 SkyWalkingQueryException
    → present=false, SOURCE_UNAVAILABLE, summary 含 error */ }

  @Test
  void spanTreeTruncatedToBudget() { /* queryTrace 返回 1200 spans, budget.maxItems=1000
    → truncated=true, spans=1000 */ }
}
```

- [ ] **Step 2: 跑测试确认失败**（类不存在）。

- [ ] **Step 3: 实现探针 + 换装配**

```java
public class SkyWalkingTraceProbe implements DeepEvidenceProbe {
  private final SkyWalkingGraphqlClient client;

  @Override public DeepEvidenceKind kind() { return DeepEvidenceKind.TRACE; }

  @Override public DeepProbeResult probe(CorrelationKey key, PageBudget budget) {
    try {
      if (key.traceId() != null) {
        TraceDetail detail = client.queryTrace(key.traceId());
        List<TraceSpanView> spans = detail.spans().size() > budget.maxItems()
            ? detail.spans().subList(0, budget.maxItems()) : detail.spans();
        boolean truncated = spans.size() < detail.spans().size();
        // Availability: present=true, from/to 取 span 最早/最晚时间, granularity "span", truncated
        // summary: {spans, services, errorSpans, truncated}
      }
      var briefs = client.queryBasicTraces(null, null, key.from(), key.to(), null, null,
          true, 1, Math.min(budget.maxItems(), 100));
      // briefs 空 → present=false + NO_DATA；非空 → present=true + from/to 取首末 startEpochMs
      // sourceRef: "skywalking:trace?from=<epoch>&to=<epoch>#<first>-<last>"（对齐 PrometheusDeepProbe 风格）
      // summary: {traceCount: briefs.size(), errorCount, slowestMs}
    } catch (RuntimeException e) {
      return new DeepProbeResult(new Availability(false, null, null, null, false,
          "skywalking:trace", Availability.MissingReason.SOURCE_UNAVAILABLE),
          Map.of("error", String.valueOf(e.getMessage())), "skywalking:trace");
    }
  }
}
```

`DeepEvidenceConfiguration`：删 `new UnavailableDeepProbe(DeepEvidenceKind.TRACE, "pending-otel-or-skywalking-selection")`，改为：

```java
@Bean
public SkyWalkingTraceProbe skyWalkingTraceProbe(DeepEvidenceProperties properties) {
  return new SkyWalkingTraceProbe(new SkyWalkingGraphqlClient(properties));
}
@Bean
public EvidenceSource traceDeepEvidenceSource(DeepEvidenceProperties properties, SkyWalkingTraceProbe probe) {
  return new DeepEvidenceSource(DeepEvidenceKind.TRACE, properties, probe);
}
```

- [ ] **Step 4: 探针单测 PASS**（Run 同 Task 2 方式）。

- [ ] **Step 5: agent 证据链 API 测试（不可达 + 未启用两态）**

仿 `AgentEvidenceDeepProbeApiTest`：`@SpringBootTest`(H2) + `@AutoConfigureMockMvc`，properties：

```java
"platform.evidence.deep.kinds.trace.enabled=true",
"platform.evidence.deep.kinds.trace.endpoint=http://127.0.0.1:1"
```

`GET /api/agent/executions/{id}/evidence`（X-API-Key 头，样板取自该测试）断言：`deep:trace` 源 `present=false`、`missingReason=SOURCE_UNAVAILABLE`。第二个测试类实例不配 endpoint/enabled → 断言 unavailable 且 summary 含 `requiresApproval=true`。

- [ ] **Step 6: 全量回归**

Run: `JAVA_HOME=... ./gradlew backend:test`
Expected: 全 PASS（既有 `AgentEvidenceDeepProbeApiTest` 中对 trace 占位 reason 的断言若存在需同步更新——grep `pending-otel-or-skywalking` 确认无残留）。

- [ ] **Step 7: Commit** `feat：SkyWalkingTraceProbe 替换 trace 深度源占位，agent 证据链接入真实探针`

---

### Task 4: REST 端点 /executions/{id}/traces（快照优先）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionTraceViews.java`（响应 record）
- Create: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionTraceQueryService.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/api/ExecutionTraceController.java`
- Modify: `backend/src/main/resources/application.yml`（`deep.kinds.trace` 补注释示例，默认仍 false）
- Test: `backend/src/test/java/com/yr/perftest/platform/api/ExecutionTraceApiTest.java`

**Interfaces:**
- Consumes: Task 2 client；Task 6 的快照 repository（本任务先写快照读接口调用，Task 6 落地实体——**顺序依赖：Task 6 在本任务前执行**，见任务排序说明）。
- Produces（前端 Task 8 契约）:
  - `GET /api/executions/{id}/traces?service&endpoint&onlyError&minDurationMs&sort=duration|time&page=1&size=20` → `{"available":true,"missingReason":null,"traces":[{traceId,time,service,entry,durationMs,error,spanCount}],"total":26,"page":1,"size":20}`
  - `GET /api/executions/{id}/traces/{traceId}` → `{"available":true,"missingReason":null,"trace":{traceId,entry,service,durationMs,error,spans:[TraceSpanView]}}`
  - `missingReason` 取值：`unconfigured`（endpoint 空）/ `source-unavailable` / `retention-expired`（快照存在但 span 详情查询失败）。

> **执行顺序说明**：Task 4 依赖 Task 6 的快照表先存在。按 Task 6 → Task 4 顺序执行；文档编号仅为叙述顺序。

- [ ] **Step 1: 写 API 测试（三态）**

```java
@SpringBootTest(properties = {
  "platform.evidence.deep.kinds.trace.enabled=true",
  "platform.evidence.deep.kinds.trace.endpoint=http://127.0.0.1:1"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionTraceApiTest {
  // 登录：AuthTestSupport.loginToken(mockMvc, objectMapper)（样板 MonitoringApiBehaviorTest:40-42）

  @Test void unreachableOapReturnsAvailableFalseWithSourceUnavailable() {
    // GET /api/executions/{runningExecutionId}/traces → 200
    // available=false, missingReason=source-unavailable, traces 空数组
  }
  @Test void unconfiguredEndpointReturnsUnconfigured() { /* 不配 endpoint → missingReason=unconfigured */ }
  @Test void snapshotServesTerminalExecutionWithoutOap() {
    // 插入 PersistentExecutionTraceSnapshotRecord(executionId, tracesJson=26 条 fixture, total=26)
    // GET .../traces?sort=duration → 200 available=true，traces 来自快照（时间倒序首条=最慢）
    // GET .../traces?page=2&size=20 → 第 2 页 6 条
    // GET .../traces?service=api-gateway → 过滤
  }
  @Test void pageSizeClampedTo100() { /* size=500 → 响应 size=100 */ }
}
```

- [ ] **Step 2: 跑测试确认失败**（404）。

- [ ] **Step 3: 实现 views + service + controller**

`ExecutionTraceViews.java`：上节 Produces 的 4 个 record。

`ExecutionTraceQueryService`（注入 `PersistentScenarioExecutionRepository`、`SkyWalkingGraphqlClient`、`DeepEvidenceProperties`、Task 6 的 `PersistentExecutionTraceSnapshotRepository`）：

```java
public ExecutionTracesPageView queryTraces(long executionId, String service, String endpoint,
    Boolean onlyError, Long minDurationMs, String sort, int page, int size) {
  size = Math.min(Math.max(size, 1), 100); page = Math.max(page, 1);
  var execution = executionRepository.findById(executionId).orElseThrow(...);
  boolean terminal = Set.of("SUCCESS","FAILED","INTERRUPTED","CANCELLED").contains(execution.getStatus());
  var snapshot = snapshotRepository.findByExecutionId(executionId);
  if (terminal && snapshot.isPresent()) {
    // 内存过滤（service/endpoint/onlyError/minDurationMs）→ sort(duration 按持续时间降序 / time 按时间降序)
    // → 分页切片 → available=true
  }
  String oap = properties.forKind(DeepEvidenceKind.TRACE).getEndpoint();
  if (oap == null || oap.isBlank()) return unavailable("unconfigured");
  try {
    Instant from = parse(execution.getStartedAt()); Instant to = execution.getEndedAt()!=null ? parse(...) : Instant.now();
    var briefs = client.queryBasicTraces(service, endpoint, from, to, minDurationMs, onlyError,
        !"time".equals(sort), page, size);
    // → available=true（空列表也是 ok，无 missingReason）
  } catch (RuntimeException e) { return unavailable("source-unavailable"); }
}
```

`ExecutionTraceController`：`@RestController @RequestMapping("/api")`，两个 `@GetMapping`，参数注解照抄 `TaskPlanController.getTargetMonitoringSeries`（:336-345）的默认值风格（page=1、size=20）。鉴权：仅登录（对齐既有 executions 端点现状）。

- [ ] **Step 4: 测试 PASS**（Run：`./gradlew backend:test --tests '*ExecutionTraceApiTest*'`）。

- [ ] **Step 5: Commit** `feat：执行详情 trace 列表/详情 REST——终态快照优先、OAP 直连兜底、可用性声明`

---

### Task 5: 失败样本 traceId 提取与存储贯通

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/execution/failure/TraceIdHeaderExtractor.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/distributed/JmeterBackendListenerInjector.java`（Groovy 脚本 :174-190 的 row 构造）
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/failure/FailureSampleRecord.java`（加 `traceId`）
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/failure/FailureSampleStore.java`（:37-58 建表 + ALTER 升级 + 插入/查询列）
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/TaskExecutionResult.java`（`Sample` 加 `traceId`）
- Test: `backend/src/test/java/com/yr/perftest/platform/execution/failure/TraceIdHeaderExtractorTest.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/execution/failure/FailureSampleStoreTraceIdUpgradeTest.java`
- Test: Modify `backend/src/test/java/com/yr/perftest/platform/execution/distributed/ExecutionScriptAssemblerTest.java`

**Interfaces:**
- Consumes: 既有 JSONL/SQLite 管线。
- Produces: `TraceIdHeaderExtractor.extract(String responseHeaders) → String|null`；`FailureSampleRecord(..., String traceId)`（尾部追加）；`TaskExecutionResult.Sample(..., String traceId)`（尾部追加）——Task 8 前端 `TaskSample.traceId` 的来源。

- [ ] **Step 1: 写提取器测试（变体矩阵）**

```java
class TraceIdHeaderExtractorTest {
  @Test void extractsFromSw8ThirdSegment() {
    var headers = "Content-Type: application/json\r\nsw8: 1-AMVnR2VudA==-e9f3a2b7c1d84f05-a1b2-3-0-1-\r\n";
    assertThat(TraceIdHeaderExtractor.extract(headers)).isEqualTo("e9f3a2b7c1d84f05");
  }
  @Test void extractsFromXTraceIdFallback() { /* "X-Trace-Id: abc123" → "abc123" */ }
  @Test void headerNameCaseInsensitive() { /* "SW8: ..." / "x-trace-id: ..." */ }
  @Test void malformedSw8FallsBackThenNull() { /* 段数<3 的 sw8 + 无 X-Trace-Id → null（不抛） */ }
  @Test void emptySegmentYieldsNull() { /* sw8: 1--  → 第3段空 → null */ }
  @Test void nullSafe() { assertThat(TraceIdHeaderExtractor.extract(null)).isNull(); }
  @Test void multipleHeaderLinesTakesFirstNonEmpty() {}
}
```

- [ ] **Step 2: 确认失败 → 实现提取器**（逐行 split，冒号前 trim 忽略大小写匹配 `sw8`/`x-trace-id`，sw8 按 `-` split 取 index 2，`isBlank` 与段数不足返回继续找）。测试 PASS。

- [ ] **Step 3: 写 SQLite 升级测试**

```java
class FailureSampleStoreTraceIdUpgradeTest {
  @Test void legacyDbWithoutTraceIdColumnUpgradesOnInitialize(@TempDir Path dir) throws Exception {
    Path db = dir.resolve("failure-samples.db");
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
      s.execute("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT, external_id INTEGER NOT NULL, " +
        "host TEXT NOT NULL, ts INTEGER NOT NULL, label TEXT NOT NULL, code TEXT NOT NULL, success INTEGER NOT NULL, " +
        "elapsed INTEGER NOT NULL, message TEXT, thread_name TEXT, url TEXT, request_headers TEXT, " +
        "request_body TEXT, response_headers TEXT, response_body TEXT, failure_message TEXT, " +
        "UNIQUE(host, external_id))");
      s.execute("INSERT INTO samples(external_id, host, ts, label, code, success, elapsed) VALUES(1,'h1',1,'L','500',0,10)");
    }
    var store = new FailureSampleStore();
    store.initialize(db); // 触发 ALTER
    Long newId = store.insertReturningId(db, new FailureSampleRecord(2L, 2L, "L", "500", false, 10L,
      "", "", "h1", "", "", "", "sw8: 1-x-e9f3a2b7c1d84f05-a-1-0-1-", "", "", "", ""));
    assertThat(newId).isNotNull();
    assertThat(store.<TaskSamplePage>querySummaries(db, new FailureSampleQuery(), 1, 10).samples()
      .stream().map(s -> s.traceId())).containsExactly(null, "e9f3a2b7c1d84f05");
  }
}
```

- [ ] **Step 4: 改 store/record/Sample**

`FailureSampleStore.initialize`：建表语句加 `trace_id TEXT`（`response_headers` 列后）；建表后执行升级探测：

```java
try (ResultSet rs = meta.getColumns(null, null, "samples", "trace_id")) {
  if (!rs.next()) stmt.execute("ALTER TABLE samples ADD COLUMN trace_id TEXT");
}
```

`insertReturningId` 的 INSERT 加列；`querySummaries`/`findDetail`/`listDetailsAfter` 的 SELECT 加 `trace_id` 并映射。`FailureSampleRecord` 与 `TaskExecutionResult.Sample` 尾部加 `String traceId`（Jackson 旧行无此键 → null，`@JsonIgnoreProperties(ignoreUnknown=true)` 已有）。测试 PASS。

- [ ] **Step 5: Groovy 脚本提取 + 注入测试断言**

`JmeterBackendListenerInjector` 的 Failure Sample Collector 脚本（:174-190 row 构造处）加：

```groovy
def traceId = null
def hd = result.getResponseHeaders()
if (hd != null) {
  hd.split("\r?\n").each { line ->
    def idx = line.indexOf(':')
    if (idx > 0) {
      def name = line.substring(0, idx).trim().toLowerCase()
      def value = line.substring(idx + 1).trim()
      if (traceId == null && name == 'sw8') {
        def segs = value.split('-')
        if (segs.length > 2 && !segs[2].isEmpty()) traceId = segs[2]
      } else if (traceId == null && name == 'x-trace-id' && !value.isEmpty()) traceId = value
    }
  }
}
// row 里追加 "traceId": traceId（JSON nil-safe——groovy JsonOutput 对 null 输出 null）
```

`ExecutionScriptAssemblerTest` 加断言：注入产物 contains `"sw8"` 且 contains `"x-trace-id"`（保接线）。

- [ ] **Step 6: 全量回归**

Run: `./gradlew backend:test`（含 `AgentFailureSampleApiTest`——record 加字段后其构造调用处需补 null 实参，逐个修）。
Expected: 全 PASS。

- [ ] **Step 7: Commit** `feat：失败样本提取 sw8/X-Trace-Id 响应头 traceId——SQLite 列升级、样本 API 透出`

---

### Task 6: 终态快照表 + 执行结束挂钩 + 级联删除

> 在 Task 4 之前执行。

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__execution_trace_snapshot.sql`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/PersistentExecutionTraceSnapshotRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/PersistentExecutionTraceSnapshotRepository.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionTraceQueryService.java`（captureSnapshot/loadSnapshot，Task 4 建的文件）
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/distributed/DistributedJmeterExecutionRunner.java:223` 附近（`captureTargetMetricsQuietly` 后加一行）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/ScenarioExecutionService.java:118` 附近（级联删除）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/ExecutionTraceSnapshotTest.java`

**Interfaces:**
- Consumes: Task 2 client（capture 时拉列表）。
- Produces: `PersistentExecutionTraceSnapshotRepository#findByExecutionId(long) → Optional<...>`、`#deleteByExecutionId(long)`；`ExecutionTraceQueryService#captureSnapshot(long)`。

- [ ] **Step 1: 写快照测试**

```java
@SpringBootTest @AutoConfigureMockMvc @Transactional
class ExecutionTraceSnapshotTest {
  @MockBean SkyWalkingGraphqlClient client; // 无 @MockBean? 项目用 Mockito.mock——
  // 依项目风格：直接构造 service 注入 mock client + repository（参照 EvidenceClockAndLifecycleTest 的纯单测套路 + @DataJpaTest 落库断言分开两个测试）
  @Test void capturePullsTop500ByDurationAndPersistsJson() {
    // client.queryBasicTraces(..., true, 1, 500) 返回 3 条 fixture
    // service.captureSnapshot(executionId) → repository 行存在，traces_json 含 3 条、captured_at 非空
  }
  @Test void captureSilentlyIgnoresOapFailure() { /* client 抛异常 → 不落行、不抛 */ }
}
```

（若项目无 `@MockBean` 惯例，则拆纯单测：mock client + 真实 repository 用 H2 `@DataJpaTest`；以 `TargetMetricsServiceFilterTest` + 既有 repository 测试为准。）

- [ ] **Step 2: SQL 迁移 + 实体**

```sql
-- V14__execution_trace_snapshot.sql
CREATE TABLE execution_trace_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  execution_id BIGINT NOT NULL,
  traces_json LONGTEXT NOT NULL,
  total INT NOT NULL,
  captured_at TIMESTAMP NOT NULL,
  CONSTRAINT uk_exec_trace_snapshot UNIQUE (execution_id)
);
```

实体照抄 `PersistentTargetMetricsSnapshotRecord`（列改名，`@Lob String tracesJson`）。H2/MySQL 双方言兼容由 MODE=MySQL 保证（对齐 V1 基线写法）。

- [ ] **Step 3: captureSnapshot 实现**

```java
@Transactional
public void captureSnapshot(long executionId) {
  String oap = properties.forKind(DeepEvidenceKind.TRACE).getEndpoint();
  if (oap == null || oap.isBlank()) return;
  try {
    var briefs = client.queryBasicTraces(null, null, fromOf(executionId), toOf(executionId), null, null, true, 1, 500);
    repository.deleteByExecutionId(executionId);
    repository.save(new PersistentExecutionTraceSnapshotRecord(executionId,
      objectMapper.writeValueAsString(briefs), briefs.size(), Instant.now()));
  } catch (RuntimeException ignored) { } // 终态收尾静默，对齐 captureTargetMetricsQuietly
}
```

Runner `finally`（:223 `captureTargetMetricsQuietly(executionId);` 后）加 `executionTraceQueryService.captureSnapshot(executionId);`（service 内已吞异常；注意 Runner 构造器注入新依赖）。

`ScenarioExecutionService` 删除级联（:118 `aggregateReportService.deleteByExecutionId` 旁）加 `executionTraceQueryService.deleteByExecutionId(executionId);`。

- [ ] **Step 4: 测试 PASS + 全量回归**（`./gradlew backend:test`）。

- [ ] **Step 5: Commit** `feat：执行终态 trace 摘要快照——V14 表、收尾挂钩、删除级联`

---

### Task 7: 观测轮次 observabilityProfile + precheck 软检查

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/ExecutionConfig.java`（加 enum + 字段）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/ScenarioExecutionService.java`（triggerExecution 链 + normalizeConfig null→OFF）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionControlService.java`（StartCommand 加字段）
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/TaskPlanController.java:226` 附近（trigger 请求体加字段）
- Modify: `backend/src/main/java/com/yr/perftest/platform/envcheck/EnvCheckCategory.java`（加 `OBSERVABILITY`）
- Create: `backend/src/main/java/com/yr/perftest/platform/envcheck/items/TraceAgentReportingItem.java`
- Modify: `frontend/src/components/env-check/envCheckSettings.ts:11-15`（`CATEGORY_LABELS` 加 `OBSERVABILITY: '可观测'`）
- Test: Modify `backend/src/test/java/com/yr/perftest/platform/task/ExecutionConfigMergerTest.java`（或 normalize 所在测试）
- Test: `backend/src/test/java/com/yr/perftest/platform/envcheck/items/TraceAgentReportingItemTest.java`

**Interfaces:**
- Consumes: Task 2 client（precheck 探测）。
- Produces: `ExecutionConfig.observabilityProfile: ObservabilityProfile{OFF, CAPACITY, DIAGNOSTIC}`（经既有 config 序列化直达前端 `execution.config.observabilityProfile`）；trigger 请求体可选字段 `observabilityProfile`。

- [ ] **Step 1: 写归一化测试**

```java
// ExecutionConfigMergerTest 追加
@Test void legacyConfigWithoutProfileNormalizesToOff() {
  // readConfig("{\"threads\":10,...无 observabilityProfile...}") → config.observabilityProfile() == OFF
}
@Test void triggerWithDiagnosticProfilePersistedIntoConfigJson() {
  // triggerExecution(..., DIAGNOSTIC) → 落库 configJson 反序列化回 DIAGNOSTIC
}
```

- [ ] **Step 2: 实现 record 字段与链路透传**

`ExecutionConfig`：嵌套 `public enum ObservabilityProfile { OFF, CAPACITY, DIAGNOSTIC }`，record 组件尾部追加 `ObservabilityProfile observabilityProfile`；`normalizeConfig`/merger 输出处 null → OFF；`ExecutionConfigMerger` 既有调用 `new ExecutionConfig(...)` 处补实参（grep 全部构造点补 `OFF` 或透传）。trigger 链（Controller 请求 record → `StartCommand` → `triggerExecution` 参数）加 `ObservabilityProfile observabilityProfile`（可空 → OFF）。前端暂不动（Task 8）。

- [ ] **Step 3: 写 precheck item 测试**

```java
class TraceAgentReportingItemTest {
  @Test void disabledSourceSkipsOk() { /* enabled=false → ok=true, detail 含「未启用」 */ }
  @Test void recentTracesReportedOk() { /* client 返回非空 → ok=true */ }
  @Test void noRecentTracesWarns() { /* client 空 → ok=false, detail 含「agent 未挂载或采样率为 0」（渲染 WARNING，非阻断，见 EnvironmentCheckRunner:209） */ }
  @Test void oapUnreachableWarns() { /* client 抛 → ok=false, detail 含 OAP 不可达 */ }
}
```

- [ ] **Step 4: 实现 item**

```java
@Component
public class TraceAgentReportingItem implements LocalCheckItem {
  // key: "trace.agent-reporting"; label: "链路上报探测"; category: OBSERVABILITY; kind: LOCAL;
  // appliesTo: Set.of(); sortOrder: 大于既有项（查 items/ 下最大值 +100）
  // check(ctx): trace KindConfig 未启用 → ok=true detail "trace 深度源未启用，跳过"
  //   client.queryBasicTraces(null, null, now-10min, now, null, null, true, 1, 1)
  //   空列表 → ok=false detail "未检测到近 10 分钟 trace 上报：agent 未挂载或采样率为 0"
  //   RuntimeException → ok=false detail "OAP 不可达：" + msg
}
```

`EnvCheckCategory` 加 `OBSERVABILITY`；`envCheckSettings.ts` 的 `CATEGORY_LABELS` 加一行（Record 有 `?? category` 兜底，安全）。

- [ ] **Step 5: 测试 PASS + 全量回归**。

- [ ] **Step 6: Commit** `feat：观测轮次标记（容量轮/诊断轮）贯通执行配置 + precheck 链路上报探测软检查`

---

### Task 8: 前端接真实 API + flag 退役

**Files:**
- Create: `frontend/src/api/traces.ts`
- Create: `frontend/src/utils/traces-view.ts`（纯函数：状态映射/入口服务聚合/span 层级推导）
- Modify: `frontend/src/types/index.ts`（`TaskSample` 加 `traceId?: string | null`；`ExecutionConfig` 前端类型加 `observabilityProfile`；新增 trace 视图类型）
- Modify: `frontend/src/components/tasks/trace/TracePanel.vue`（mock → API）
- Modify: `frontend/src/components/tasks/trace/TraceDetailDrawer.vue`（详情拉取 + span level 推导）
- Modify: `frontend/src/components/task-plans/ExecutionDetailView.vue`（去 flag、真实轮次/traceId）
- Modify: `frontend/src/components/task-plans/method/MethodExecTable.vue`（执行表单加轮次选择）
- Modify: `frontend/src/api/task-plans.ts`（triggerExecutionApi body 加 observabilityProfile）
- Delete: `frontend/src/components/tasks/trace/trace-mock.ts`
- Test: `frontend/src/utils/traces-view.test.ts`（项目惯例：不 mount 组件，纯函数断言）

**Interfaces:**
- Consumes: Task 4 REST 契约（本文件 Produces 与 Task 4 一一对应）；Task 5 `TaskSample.traceId`；Task 7 `config.observabilityProfile`。
- Produces: 无（终端任务）。

- [ ] **Step 1: 写纯函数测试**

```ts
// traces-view.test.ts
describe('traces-view', () => {
  it('maps missingReason to panel state', () => {
    expect(panelStateOf({ available: true, traces: [], total: 0, page: 1, size: 20 })).toBe('ok');
    expect(panelStateOf({ available: false, missingReason: 'unconfigured', traces: [], total: 0, page: 1, size: 20 })).toBe('unconfigured');
    expect(panelStateOf({ available: false, missingReason: 'source-unavailable', traces: [], total: 0, page: 1, size: 20 })).toBe('down');
  });
  it('aggregates entry-service share', () => {
    const agg = aggregateServiceShare([{ service: 'a', durationMs: 300, error: false }, { service: 'b', durationMs: 100, error: true }]);
    expect(agg).toEqual([{ service: 'a', totalMs: 300, sharePct: 75, errorTraces: 0 }, { service: 'b', totalMs: 100, sharePct: 25, errorTraces: 1 }]);
  });
  it('derives span levels from parent chain', () => {
    const levels = computeSpanLevels([{ spanId: 0, parentSpanId: -1 }, { spanId: 1, parentSpanId: 0 }]);
    expect(levels).toEqual([0, 1]);
  });
});
```

- [ ] **Step 2: 实现 traces-view.ts + api/traces.ts**（`getExecutionTracesApi`/`getExecutionTraceDetailApi`，request 封装照 `target-monitoring.ts`；类型在 `types/index.ts` 定义后导入）。

- [ ] **Step 3: 改 TracePanel**：删 `MOCK_TRACES`/三态演示 radio/「演示数据」tag；`onMounted` + 筛选 `watch` → `getExecutionTracesApi`；响应状态驱动 `unconfigured/down/ok` 三态（复用既有模板分支）；分页参数化；「服务耗时分布」改用 `aggregateServiceShare(traces)`（**入口服务维度，Phase 1**——spec §7 的 span 维度 Phase 2 升级）；轮次徽标 `props.observabilityProfile`（DIAGNOSTIC→「诊断轮 · 全量采样」，CAPACITY/OFF→「容量轮 · 低采样」）。行数仍 < 500（删 mock 数据后余量充足）。

- [ ] **Step 4: 改 Drawer**：`openTrace(traceId)` → `getExecutionTraceDetailApi`；`computeSpanLevels` 供瀑布图缩进（`startTimeMillis/endTimeMillis` 字段名与后端 `TraceSpanView` 对齐，条形计算从 `startMs/durationMs` 改为 `start/endTimeMillis`）；`available=false && missingReason=retention-expired` → 抽屉显示「详情已过保留期」占位。

- [ ] **Step 5: 改 ExecutionDetailView + 执行表单**：删 `isProto`/`traceProto` 与 `demoTraceIdForSample`；`<TracePanel>` 常驻（v-if 去掉），传 `:observability-profile="execution.config?.observabilityProfile ?? 'CAPACITY'"`；样本 chip 条件 `selectedSample?.traceId`（有→chip+按钮调 `openTrace(traceId)`；无→置灰+tooltip 文案不变）。`MethodExecTable` 表单加「观测轮次」select（容量轮=CAPACITY/诊断轮=DIAGNOSTIC，默认容量轮）进 trigger payload。删除 `trace-mock.ts`（`usePrototype.ts` 保留——载体基础设施，后续原型复用）。

- [ ] **Step 6: 验证**

Run: `cd frontend && npx vue-tsc --noEmit && npm test`
Expected: 0 错误、全 PASS。手工：关掉开关（无 `?proto`）打开执行详情页——面板出现且未配置态正确（本地 OAP 未部署时为 down/unconfigured 态）。

- [ ] **Step 7: Commit** `feat：链路追踪面板接真实 API——mock 退役、轮次/样本 traceId 真实化、flag 下线`

---

### Task 9: 文档与缺口清单收尾

**Files:**
- Modify: `docs/data-collection-gaps.md`（P0-1 打勾，注明 Phase 2 余项）
- Modify: `docs/superpowers/specs/2026-09-21-trace-integration-design.md`（§7「服务耗时分布」行加注「Phase 1 为入口服务维度，Phase 2 升级 span 维度」；§13 落地记录补 flag 已退役）

**Interfaces:** 无。

- [ ] **Step 1:** 更新两文档上述三处；P0-1 勾选后追加一行「Phase 2 余项：内置瀑布图（抽屉现为摘要+deep-link）、设置页观测数据源 UI、报告页曲线、span 维度服务分布」。
- [ ] **Step 2:** Commit `docs：trace 接入 Phase 1 收尾——缺口清单打勾与 spec 分期注记`

---

## Self-Review 记录

- **Spec coverage**：§3 选型→Task 1；§4 数据流→Task 2/3/6；§5 traceId→Task 5/8；§6 轮次→Task 7；§7 界面→Task 8（含 §7 位置表全部入口）；§8 API→Task 4；§9 边界→Review Focus 5 条全覆盖；§10 Phase 1 全项落任务，Phase 2 明确不在本计划；§11 测试→各任务 TDD 步骤；§12/§13 原型→Task 8 退役。无缺口。
- **Placeholder scan**：GraphQL 字段名标注了「以 OAP 9.7 实测为准 + curl 验证步骤」，其余无 TBD/空泛步骤。
- **Type consistency**：`TraceBrief/TraceSpanView/TraceDetail`（Task 2 定义，3/4/6/8 消费）、`missingReason` 枚举（Task 4 定义、Task 8 映射）、`observabilityProfile`（Task 7 定义、Task 8 消费）已交叉核对一致；前端 `TraceSpanView` 字段名 `startTimeMillis/endTimeMillis` 与后端一致。
- **Review Focus**：5 条均有归属测试任务。
