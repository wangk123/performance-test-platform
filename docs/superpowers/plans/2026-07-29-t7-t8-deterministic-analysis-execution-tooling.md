# T7 确定性分析 + T8 压测执行工具化 实施计划

> **状态：✅ 已完成（2026-07-30）**。全部任务已提交：`221b41f`…`2cf7b47`（T7/T8 主体）与 `5ebb2a7`、`dc00ad5`（审查修复）。任务清单见 `docs/agent-platform-buildout-tasks.md`。
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** T7 新建 `analysis` 模块，用带版本号的确定性算法产出事实（趋势/异常/错误聚类/资源饱和/执行对比），经 Facade 暴露给 agent 面；T8 给压测执行补 Agent 驱动语义（幂等启动、预检、统一启停取消、稳定 ID + 状态查询）。

**Architecture:** T7 的算法类是无 Spring 依赖的纯函数（输入内存数据、输出 `AnalysisFact`），`AnalysisService` 经 `DataFacade` 有界加载数据并装配 `AnalysisReport`，`AnalysisFacade` 做主体校验，`AgentAnalysisController` 暴露 REST。T8 在 `execution` 包新增幂等存储与冲突异常，在 `task` 包新增 `ExecutionControlService`（统一启动/停止/取消状态机）与 `ExecutionPrecheckService`（预检 + 影响评估），经 `ExecutionFacade` + `AgentExecutionControlController` 暴露。

**Tech Stack:** Java 17、Spring Boot 3（Web/Security/Data JPA/Validation）、JUnit 5 + AssertJ + MockMvc（`spring-boot-starter-test`）、H2（测试库）。

## Global Constraints

- 只做 T7、T8 清单内的事；不改 UI 面既有接口行为（`api/` 包不动）。
- agent 包分层约束（`AgentLayeringConstraintTest` 强制）：`com.yr.perftest.platform.agent` 下任何 import 不得含 `Repository`、`java.nio.file.`、`prometheus`/`Prometheus`；agent controller 只调 Facade。
- 响应契约：agent 面返回 `ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, data)`；错误经 `AgentExceptionHandler` 映射稳定错误码。
- 确定性：算法类不依赖 `Instant.now()` / 随机数 / 外部 IO；输出排序必须稳定（计数相同按 key 字典序）；浮点输出统一 `AnalysisMath.round4`。
- 缺失数据显式声明（沿用 T5 约束）：数据源不可用时在 `completeness` 标记 `SOURCE_UNAVAILABLE`，不用空成功冒充，不用其他时段/源静默替代。
- 输出只含事实 + 算法版本 + 输入范围 + 完整度 + 证据定位，不含根因结论。
- 行数上限：模块 ≤ 500 行；使用项目既有风格（record、包结构、`ExecutionValidationException` 语义）。
- 测试命令前先 `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`。
- Commit 格式：`<type>：<描述>`（全角冒号，与 git log 一致）。

## 文件结构

**T7 新增：**
- `analysis/AnalysisFact.java` — 单条事实（算法 ID + 版本 + kind + 数据 + 证据定位）
- `analysis/AnalysisMath.java` — round4 / deltaPct / direction 共用纯函数
- `analysis/TrendAnalysis.java` — 趋势（`trend` v1）
- `analysis/AnomalyDetection.java` — 异常区间 + 拐点（`anomaly` v1）
- `analysis/ErrorClustering.java` — 错误聚类 + 标签贡献度（`error-cluster` v1）
- `analysis/ResourceSaturation.java` — 资源饱和窗口 + 与吞吐量相关性（`resource-saturation` v1）
- `analysis/ExecutionComparison.java` — 基线 vs 候选可比性 + 差异（`execution-compare` v1）
- `analysis/SourceCompleteness.java` / `analysis/AnalysisReport.java` — 输出结构
- `analysis/AnalysisService.java` — 经 DataFacade 加载数据、运行算法、装配报告
- `facade/AnalysisFacade.java` — 主体校验 + 委托
- `agent/analysis/AgentAnalysisController.java` — REST 入口

**T8 新增/修改：**
- `execution/IdempotencyConflictException.java` / `ExecutionConflictException.java` / `RequestHashing.java`
- `execution/PersistentIdempotencyRecord.java` / `PersistentIdempotencyRepository.java` / `IdempotencyService.java`
- `task/ExecutionControlService.java` — 统一启动（幂等）/ 停止（优雅）/ 取消（立即）
- `task/ExecutionPrecheckService.java` — 预检 + 影响评估
- 修改 `task/PersistentScenarioExecutionRecord.java`（+`markCancelled()`）、`task/PersistentScenarioExecutionRepository.java`（+`countByStatusIn`）、`execution/distributed/DistributedJmeterExecutionRunner.java`（`markInterrupted` 跳过终态）
- `facade/ExecutionFacade.java` + `facade/data/ExecutionStartResult.java` / `ExecutionStatusView.java` / `ExecutionPrecheckView.java`
- `agent/execution/AgentExecutionControlController.java`
- 修改 `agent/AgentExceptionHandler.java`（+409 映射）

---

## T7 确定性分析

### Task 1: analysis 骨架 + 趋势分析（trend v1）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/analysis/AnalysisFact.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/analysis/AnalysisMath.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/analysis/TrendAnalysis.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/analysis/TrendAnalysisTest.java`

**Interfaces:**
- Consumes: `com.yr.perftest.platform.execution.aggregate.MetricTick`（既有 record：`bucketTimeMs()`、`overall()` → `LabelMetric{label,samples,errorSamples,throughput,avgRtMs,p95RtMs}`）
- Produces:
  - `record AnalysisFact(String algorithmId, String algorithmVersion, String kind, String summary, Map<String,Object> data, List<String> evidenceRefs)`
  - `AnalysisMath.round4(double) -> double`、`AnalysisMath.deltaPct(double first, double second) -> Double`（first==0 时 second==0 返回 0.0，否则 null）、`AnalysisMath.direction(Double) -> String`（>5 INCREASING / <-5 DECREASING / 否则 STABLE，null 视为 INCREASING）
  - `TrendAnalysis.ALGORITHM_ID="trend"`、`TrendAnalysis.VERSION="1"`、`new TrendAnalysis().analyze(List<MetricTick> ticks, List<String> evidenceRefs) -> AnalysisFact`
  - trend `data` 键：`tickCount`(int)、`avgRtMs`/`throughput`/`errorRate` 各为 `{first, second, deltaPct, direction}`

- [x] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrendAnalysisTest {
    private static List<MetricTick> goldenTicks() {
        List<MetricTick> ticks = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            long avgRt = index < 5 ? 100 : 200;
            ticks.add(new MetricTick(
                    1_000L * (index + 1),
                    List.of(),
                    new MetricTick.LabelMetric("__total__", 10, 0, 50.0, avgRt, avgRt + 20)
            ));
        }
        return ticks;
    }

    @Test
    void goldenDatasetProducesDeterministicTrend() {
        AnalysisFact first = new TrendAnalysis().analyze(goldenTicks(), List.of("metric-series#1000-10000"));
        AnalysisFact second = new TrendAnalysis().analyze(goldenTicks(), List.of("metric-series#1000-10000"));

        assertThat(first).isEqualTo(second);
        assertThat(first.algorithmId()).isEqualTo("trend");
        assertThat(first.algorithmVersion()).isEqualTo("1");
        assertThat(first.kind()).isEqualTo("trend");
        assertThat(first.evidenceRefs()).containsExactly("metric-series#1000-10000");
        assertThat(first.data()).containsEntry("tickCount", 10);
        @SuppressWarnings("unchecked")
        Map<String, Object> avgRt = (Map<String, Object>) first.data().get("avgRtMs");
        assertThat(avgRt)
                .containsEntry("first", 100.0)
                .containsEntry("second", 200.0)
                .containsEntry("deltaPct", 100.0)
                .containsEntry("direction", "INCREASING");
        @SuppressWarnings("unchecked")
        Map<String, Object> throughput = (Map<String, Object>) first.data().get("throughput");
        assertThat(throughput)
                .containsEntry("first", 50.0)
                .containsEntry("second", 50.0)
                .containsEntry("deltaPct", 0.0)
                .containsEntry("direction", "STABLE");
        @SuppressWarnings("unchecked")
        Map<String, Object> errorRate = (Map<String, Object>) first.data().get("errorRate");
        assertThat(errorRate).containsEntry("direction", "STABLE");
    }

    @Test
    void emptyInputProducesStableZeroTrend() {
        AnalysisFact fact = new TrendAnalysis().analyze(List.of(), List.of());

        assertThat(fact.data()).containsEntry("tickCount", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> avgRt = (Map<String, Object>) fact.data().get("avgRtMs");
        assertThat(avgRt)
                .containsEntry("first", 0.0)
                .containsEntry("second", 0.0)
                .containsEntry("deltaPct", 0.0)
                .containsEntry("direction", "STABLE");
    }

    @Test
    void unsortedInputIsSortedByBucketTime() {
        List<MetricTick> ticks = goldenTicks();
        List<MetricTick> shuffled = new ArrayList<>();
        for (int index = ticks.size() - 1; index >= 0; index--) {
            shuffled.add(ticks.get(index));
        }

        AnalysisFact fact = new TrendAnalysis().analyze(shuffled, List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> avgRt = (Map<String, Object>) fact.data().get("avgRtMs");
        assertThat(avgRt).containsEntry("direction", "INCREASING");
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.analysis.TrendAnalysisTest"`
Expected: 编译失败（`AnalysisFact`、`TrendAnalysis` 不存在）

- [x] **Step 3: 实现**

`AnalysisFact.java`：

```java
package com.yr.perftest.platform.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AnalysisFact(
        String algorithmId,
        String algorithmVersion,
        String kind,
        String summary,
        Map<String, Object> data,
        List<String> evidenceRefs
) {
    public AnalysisFact {
        // 允许 null 值（如 kneePointMs、deltaPct），故不用 Map.copyOf
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
```

`AnalysisMath.java`：

```java
package com.yr.perftest.platform.analysis;

public final class AnalysisMath {
    private AnalysisMath() {
    }

    public static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    public static Double deltaPct(double first, double second) {
        if (first == 0) {
            return second == 0 ? 0.0 : null;
        }
        return round4((second - first) / first * 100.0);
    }

    public static String direction(Double deltaPct) {
        if (deltaPct == null) {
            return "INCREASING";
        }
        if (deltaPct > 5.0) {
            return "INCREASING";
        }
        if (deltaPct < -5.0) {
            return "DECREASING";
        }
        return "STABLE";
    }
}
```

`TrendAnalysis.java`：

```java
package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrendAnalysis {
    public static final String ALGORITHM_ID = "trend";
    public static final String VERSION = "1";

    public AnalysisFact analyze(List<MetricTick> ticks, List<String> evidenceRefs) {
        List<MetricTick> usable = (ticks == null ? List.<MetricTick>of() : ticks).stream()
                .filter(tick -> tick != null && tick.overall() != null)
                .sorted(Comparator.comparingLong(MetricTick::bucketTimeMs))
                .toList();
        int midpoint = usable.size() / 2;
        List<MetricTick> firstHalf = usable.stream().limit(midpoint).toList();
        List<MetricTick> secondHalf = usable.stream().skip(midpoint).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tickCount", usable.size());
        data.put("avgRtMs", metricTrend(avgRt(firstHalf), avgRt(secondHalf)));
        data.put("throughput", metricTrend(meanThroughput(firstHalf), meanThroughput(secondHalf)));
        data.put("errorRate", metricTrend(errorRate(firstHalf), errorRate(secondHalf)));
        return new AnalysisFact(
                ALGORITHM_ID,
                VERSION,
                "trend",
                "response time / throughput / error rate trend of second half vs first half",
                data,
                evidenceRefs
        );
    }

    private Map<String, Object> metricTrend(double first, double second) {
        Double deltaPct = AnalysisMath.deltaPct(first, second);
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("first", AnalysisMath.round4(first));
        trend.put("second", AnalysisMath.round4(second));
        trend.put("deltaPct", deltaPct);
        trend.put("direction", AnalysisMath.direction(deltaPct));
        return trend;
    }

    private double avgRt(List<MetricTick> half) {
        long samples = half.stream().mapToLong(tick -> tick.overall().samples()).sum();
        if (samples > 0) {
            double weighted = half.stream()
                    .mapToDouble(tick -> (double) tick.overall().avgRtMs() * tick.overall().samples())
                    .sum();
            return weighted / samples;
        }
        return half.stream().mapToDouble(tick -> tick.overall().avgRtMs()).average().orElse(0);
    }

    private double meanThroughput(List<MetricTick> half) {
        return half.stream().mapToDouble(tick -> tick.overall().throughput()).average().orElse(0);
    }

    private double errorRate(List<MetricTick> half) {
        long samples = half.stream().mapToLong(tick -> tick.overall().samples()).sum();
        long errors = half.stream().mapToLong(tick -> tick.overall().errorSamples()).sum();
        return samples == 0 ? 0 : (double) errors / samples;
    }
}
```

- [x] **Step 4: 跑测试确认通过**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.analysis.TrendAnalysisTest"`
Expected: PASS（3 个用例）

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/analysis backend/src/test/java/com/yr/perftest/platform/analysis
git commit -m "feat：新增 analysis 模块骨架与趋势分析算法（T7）"
```

---

### Task 2: 异常区间 + 性能拐点检测（anomaly v1）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/analysis/AnomalyDetection.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/analysis/AnomalyDetectionTest.java`

**Interfaces:**
- Consumes: `MetricTick`、`AnalysisFact`、`AnalysisMath`（Task 1）
- Produces: `AnomalyDetection.ALGORITHM_ID="anomaly"`、`AnomalyDetection.VERSION="1"`、`analyze(List<MetricTick> ticks, List<String> evidenceRefs) -> AnalysisFact`
- `data` 键：`mean`(double)、`stddev`(double)、`threshold`(double)、`intervals`(List of `{fromMs,toMs,points,maxAvgRtMs}`)、`kneePointMs`(Long，可 null)
- 规则：threshold = mean + 3*stddev（stddev==0 不报异常）；异常区间 = 连续 avgRt > threshold 的 tick 段；拐点 = 首个 i≥3 满足 `v[i] >= 2 * median(v[0..i-1])` 且其后所有值 `>= 1.5 * median`（前缀中位数基线）

- [x] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AnomalyDetectionTest {
    private static List<MetricTick> ticks(long... avgRts) {
        List<MetricTick> ticks = new ArrayList<>();
        for (int index = 0; index < avgRts.length; index++) {
            long avgRt = avgRts[index];
            ticks.add(new MetricTick(
                    1_000L * (index + 1),
                    List.of(),
                    new MetricTick.LabelMetric("__total__", 10, 0, 50.0, avgRt, avgRt + 20)
            ));
        }
        return ticks;
    }

    @Test
    void goldenSpikeProducesOneAnomalyIntervalDeterministically() {
        List<MetricTick> golden = ticks(100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 2000);

        AnalysisFact first = new AnomalyDetection().analyze(golden, List.of("metric-series#1000-11000"));
        AnalysisFact second = new AnomalyDetection().analyze(golden, List.of("metric-series#1000-11000"));

        assertThat(first).isEqualTo(second);
        assertThat(first.algorithmId()).isEqualTo("anomaly");
        assertThat(first.algorithmVersion()).isEqualTo("1");
        assertThat((Double) first.data().get("mean")).isCloseTo(272.7273, within(0.001));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> intervals = (List<Map<String, Object>>) first.data().get("intervals");
        assertThat(intervals).hasSize(1);
        assertThat(intervals.get(0))
                .containsEntry("fromMs", 11_000L)
                .containsEntry("toMs", 11_000L)
                .containsEntry("points", 1)
                .containsEntry("maxAvgRtMs", 2_000L);
    }

    @Test
    void goldenStepChangeProducesKneePointWithoutAnomaly() {
        List<MetricTick> golden = ticks(100, 100, 100, 100, 100, 100, 300, 300, 300, 300, 300, 300);

        AnalysisFact fact = new AnomalyDetection().analyze(golden, List.of());

        assertThat(fact.data().get("kneePointMs")).isEqualTo(7_000L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> intervals = (List<Map<String, Object>>) fact.data().get("intervals");
        assertThat(intervals).isEmpty();
    }

    @Test
    void shortSeriesHasNoKneePoint() {
        AnalysisFact fact = new AnomalyDetection().analyze(ticks(100, 200, 400), List.of());

        assertThat(fact.data().get("kneePointMs")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> intervals = (List<Map<String, Object>>) fact.data().get("intervals");
        assertThat(intervals).isEmpty();
    }

    @Test
    void flatSeriesHasNoAnomalyAndNoKnee() {
        AnalysisFact fact = new AnomalyDetection().analyze(ticks(100, 100, 100, 100, 100, 100), List.of());

        assertThat(fact.data().get("kneePointMs")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> intervals = (List<Map<String, Object>>) fact.data().get("intervals");
        assertThat(intervals).isEmpty();
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.analysis.AnomalyDetectionTest"`
Expected: 编译失败（`AnomalyDetection` 不存在）

- [x] **Step 3: 实现**

```java
package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnomalyDetection {
    public static final String ALGORITHM_ID = "anomaly";
    public static final String VERSION = "1";

    public AnalysisFact analyze(List<MetricTick> ticks, List<String> evidenceRefs) {
        List<MetricTick> usable = (ticks == null ? List.<MetricTick>of() : ticks).stream()
                .filter(tick -> tick != null && tick.overall() != null)
                .sorted(Comparator.comparingLong(MetricTick::bucketTimeMs))
                .toList();
        List<Double> values = usable.stream()
                .map(tick -> (double) tick.overall().avgRtMs())
                .toList();
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(value -> (value - mean) * (value - mean)).average().orElse(0);
        double stddev = Math.sqrt(variance);
        double threshold = mean + 3 * stddev;

        List<Map<String, Object>> intervals = new ArrayList<>();
        int index = 0;
        while (index < usable.size()) {
            if (stddev == 0 || values.get(index) <= threshold) {
                index++;
                continue;
            }
            int start = index;
            long max = usable.get(index).overall().avgRtMs();
            while (index < usable.size() && values.get(index) > threshold) {
                max = Math.max(max, usable.get(index).overall().avgRtMs());
                index++;
            }
            Map<String, Object> interval = new LinkedHashMap<>();
            interval.put("fromMs", usable.get(start).bucketTimeMs());
            interval.put("toMs", usable.get(index - 1).bucketTimeMs());
            interval.put("points", index - start);
            interval.put("maxAvgRtMs", max);
            intervals.add(interval);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mean", AnalysisMath.round4(mean));
        data.put("stddev", AnalysisMath.round4(stddev));
        data.put("threshold", AnalysisMath.round4(threshold));
        data.put("intervals", List.copyOf(intervals));
        data.put("kneePointMs", detectKnee(usable, values));
        return new AnalysisFact(
                ALGORITHM_ID,
                VERSION,
                "anomaly",
                "avg response time anomaly intervals and sustained knee point",
                data,
                evidenceRefs
        );
    }

    private Long detectKnee(List<MetricTick> usable, List<Double> values) {
        for (int index = 3; index < values.size(); index++) {
            double baseline = median(values.subList(0, index));
            if (baseline <= 0 || values.get(index) < 2 * baseline) {
                continue;
            }
            boolean sustained = true;
            for (int follow = index; follow < values.size(); follow++) {
                if (values.get(follow) < 1.5 * baseline) {
                    sustained = false;
                    break;
                }
            }
            if (sustained) {
                return usable.get(index).bucketTimeMs();
            }
        }
        return null;
    }

    private double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int midpoint = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(midpoint)
                : (sorted.get(midpoint - 1) + sorted.get(midpoint)) / 2;
    }
}
```

- [x] **Step 4: 跑测试确认通过**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.analysis.AnomalyDetectionTest"`
Expected: PASS（4 个用例）

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/analysis/AnomalyDetection.java backend/src/test/java/com/yr/perftest/platform/analysis/AnomalyDetectionTest.java
git commit -m "feat：新增异常区间与拐点检测算法（T7）"
```

---

### Task 3: 错误聚类 + 请求/接口贡献度（error-cluster v1）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/analysis/ErrorClustering.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/analysis/ErrorClusteringTest.java`

**Interfaces:**
- Consumes: `com.yr.perftest.platform.execution.TaskExecutionResult.Sample`（既有 record：`success()`、`label()`、`statusCode()`、`message()`、`failureMessage()`）
- Produces: `ErrorClustering.ALGORITHM_ID="error-cluster"`、`ErrorClustering.VERSION="1"`、`analyze(List<TaskExecutionResult.Sample> samples, List<String> evidenceRefs) -> AnalysisFact`
- `data` 键：`totalFailures`(int)、`clusters`(List of `{label,statusCode,messagePattern,count,sharePct}`)、`labelContribution`(List of `{label,count,sharePct}`)
- 规则：只看 `success()==false`；messagePattern = failureMessage（空则用 message）中所有数字串替换为 `#`，截断 120 字符；排序 count 降序、并列按聚类键字典序

- [x] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorClusteringTest {
    private static TaskExecutionResult.Sample sample(int id, String label, String statusCode, boolean success, String failureMessage) {
        return new TaskExecutionResult.Sample(
                id,
                "2026-07-29T01:00:00Z",
                statusCode,
                success,
                label,
                100,
                null,
                "thread-1",
                null,
                null,
                null,
                null,
                null,
                failureMessage
        );
    }

    private static List<TaskExecutionResult.Sample> goldenSamples() {
        return List.of(
                sample(1, "login", "500", false, "timeout after 3012 ms"),
                sample(2, "login", "500", false, "timeout after 998 ms"),
                sample(3, "search", "502", false, "bad gateway"),
                sample(4, "login", "200", true, null)
        );
    }

    @Test
    void goldenDatasetClustersByLabelCodeAndNormalizedMessage() {
        AnalysisFact first = new ErrorClustering().analyze(goldenSamples(), List.of("failure-samples#1-3"));
        AnalysisFact second = new ErrorClustering().analyze(goldenSamples(), List.of("failure-samples#1-3"));

        assertThat(first).isEqualTo(second);
        assertThat(first.algorithmId()).isEqualTo("error-cluster");
        assertThat(first.algorithmVersion()).isEqualTo("1");
        assertThat(first.data()).containsEntry("totalFailures", 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clusters = (List<Map<String, Object>>) first.data().get("clusters");
        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0))
                .containsEntry("label", "login")
                .containsEntry("statusCode", "500")
                .containsEntry("messagePattern", "timeout after # ms")
                .containsEntry("count", 2)
                .containsEntry("sharePct", 66.6667);
        assertThat(clusters.get(1))
                .containsEntry("label", "search")
                .containsEntry("statusCode", "502")
                .containsEntry("messagePattern", "bad gateway")
                .containsEntry("count", 1)
                .containsEntry("sharePct", 33.3333);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contribution = (List<Map<String, Object>>) first.data().get("labelContribution");
        assertThat(contribution).hasSize(2);
        assertThat(contribution.get(0))
                .containsEntry("label", "login")
                .containsEntry("count", 2)
                .containsEntry("sharePct", 66.6667);
        assertThat(contribution.get(1))
                .containsEntry("label", "search")
                .containsEntry("count", 1)
                .containsEntry("sharePct", 33.3333);
    }

    @Test
    void fallsBackToMessageWhenFailureMessageIsBlank() {
        TaskExecutionResult.Sample sample = new TaskExecutionResult.Sample(
                1, "t", "500", false, "login", 100, "socket 8080 reset", "thread-1",
                null, null, null, null, null, " ");

        AnalysisFact fact = new ErrorClustering().analyze(List.of(sample), List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clusters = (List<Map<String, Object>>) fact.data().get("clusters");
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0)).containsEntry("messagePattern", "socket # reset");
    }

    @Test
    void emptyInputProducesEmptyClusters() {
        AnalysisFact fact = new ErrorClustering().analyze(List.of(), List.of());

        assertThat(fact.data()).containsEntry("totalFailures", 0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clusters = (List<Map<String, Object>>) fact.data().get("clusters");
        assertThat(clusters).isEmpty();
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.analysis.ErrorClusteringTest"`
Expected: 编译失败（`ErrorClustering` 不存在）

- [x] **Step 3: 实现**

```java
package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.TaskExecutionResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ErrorClustering {
    public static final String ALGORITHM_ID = "error-cluster";
    public static final String VERSION = "1";
    private static final int PATTERN_MAX_LENGTH = 120;

    public AnalysisFact analyze(List<TaskExecutionResult.Sample> samples, List<String> evidenceRefs) {
        List<TaskExecutionResult.Sample> failures = (samples == null ? List.<TaskExecutionResult.Sample>of() : samples)
                .stream()
                .filter(sample -> sample != null && !sample.success())
                .toList();
        Map<String, Integer> clusterCounts = new HashMap<>();
        Map<String, Integer> labelCounts = new HashMap<>();
        for (TaskExecutionResult.Sample sample : failures) {
            String label = sample.label() == null ? "" : sample.label();
            String statusCode = sample.statusCode() == null ? "" : sample.statusCode();
            String pattern = patternOf(sample);
            clusterCounts.merge(label + "\n" + statusCode + "\n" + pattern, 1, Integer::sum);
            labelCounts.merge(label, 1, Integer::sum);
        }
        int total = failures.size();

        List<Map<String, Object>> clusters = clusterCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> {
                    String[] parts = entry.getKey().split("\n", -1);
                    Map<String, Object> cluster = new LinkedHashMap<>();
                    cluster.put("label", parts[0]);
                    cluster.put("statusCode", parts[1]);
                    cluster.put("messagePattern", parts[2]);
                    cluster.put("count", entry.getValue());
                    cluster.put("sharePct", AnalysisMath.round4(entry.getValue() * 100.0 / total));
                    return cluster;
                })
                .toList();
        List<Map<String, Object>> labelContribution = labelCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", entry.getKey());
                    row.put("count", entry.getValue());
                    row.put("sharePct", AnalysisMath.round4(entry.getValue() * 100.0 / total));
                    return row;
                })
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalFailures", total);
        data.put("clusters", clusters);
        data.put("labelContribution", labelContribution);
        return new AnalysisFact(
                ALGORITHM_ID,
                VERSION,
                "error-cluster",
                "failure clusters by label / status code / normalized message with contribution",
                data,
                evidenceRefs
        );
    }

    private String patternOf(TaskExecutionResult.Sample sample) {
        String message = sample.failureMessage() != null && !sample.failureMessage().isBlank()
                ? sample.failureMessage()
                : sample.message();
        if (message == null) {
            return "";
        }
        String normalized = message.replaceAll("\\d+", "#");
        return normalized.length() > PATTERN_MAX_LENGTH
                ? normalized.substring(0, PATTERN_MAX_LENGTH)
                : normalized;
    }
}
```

- [x] **Step 4: 跑测试确认通过**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.analysis.ErrorClusteringTest"`
Expected: PASS（3 个用例）

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/analysis/ErrorClustering.java backend/src/test/java/com/yr/perftest/platform/analysis/ErrorClusteringTest.java
git commit -m "feat：新增错误聚类与贡献度分析算法（T7）"
```

---

### Task 4: 资源饱和 + 压测与资源指标时间相关性（resource-saturation v1）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/analysis/ResourceSaturation.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/analysis/ResourceSaturationTest.java`

**Interfaces:**
- Consumes: `com.yr.perftest.platform.facade.data.PrometheusMetricPoint`（既有 record：`displayName()`、`labels()`、`timestamp()`(epoch 秒)、`value()`）、`MetricTick`
- Produces: `ResourceSaturation.ALGORITHM_ID="resource-saturation"`、`ResourceSaturation.VERSION="1"`、`analyze(List<PrometheusMetricPoint> points, double saturationThreshold, int minSustainedPoints, List<MetricTick> loadTicks, List<String> evidenceRefs) -> AnalysisFact`
- `data` 键：`threshold`(double)、`windows`(List of `{series,fromEpochSec,toEpochSec,points,maxValue}`)、`correlation`(`{alignedPairs,pearson}`，对齐点对 <3 时 pearson 为 null)
- 规则：按 `displayName` 分序列、按 timestamp 排序，连续 `value >= threshold` 且点数 `>= minSustainedPoints` 记为一个饱和窗口；相关性 = 资源值（同秒多序列取均值）与吞吐量（`bucketTimeMs/1000` 对齐）的 Pearson 系数

- [x] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.facade.data.PrometheusMetricPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceSaturationTest {
    private static List<PrometheusMetricPoint> goldenPoints() {
        double[] values = {0.5, 0.9, 0.95, 0.92, 0.4, 0.3};
        List<PrometheusMetricPoint> points = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            points.add(new PrometheusMetricPoint(
                    "cpu-app-1",
                    Map.of("instance", "app-1"),
                    index + 1L,
                    values[index],
                    0
            ));
        }
        return points;
    }

    private static List<MetricTick> goldenLoadTicks() {
        double[] throughputs = {10, 8, 6, 6, 12, 14};
        List<MetricTick> ticks = new ArrayList<>();
        for (int index = 0; index < throughputs.length; index++) {
            ticks.add(new MetricTick(
                    1_000L * (index + 1),
                    List.of(),
                    new MetricTick.LabelMetric("__total__", 10, 0, throughputs[index], 100, 120)
            ));
        }
        return ticks;
    }

    @Test
    void goldenDatasetFindsSustainedWindowAndNegativeCorrelation() {
        AnalysisFact first = new ResourceSaturation()
                .analyze(goldenPoints(), 0.9, 2, goldenLoadTicks(), List.of("prometheus:SERVER_CPU?step=15"));
        AnalysisFact second = new ResourceSaturation()
                .analyze(goldenPoints(), 0.9, 2, goldenLoadTicks(), List.of("prometheus:SERVER_CPU?step=15"));

        assertThat(first).isEqualTo(second);
        assertThat(first.algorithmId()).isEqualTo("resource-saturation");
        assertThat(first.algorithmVersion()).isEqualTo("1");
        assertThat(first.data()).containsEntry("threshold", 0.9);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> windows = (List<Map<String, Object>>) first.data().get("windows");
        assertThat(windows).hasSize(1);
        assertThat(windows.get(0))
                .containsEntry("series", "cpu-app-1")
                .containsEntry("fromEpochSec", 2L)
                .containsEntry("toEpochSec", 4L)
                .containsEntry("points", 3)
                .containsEntry("maxValue", 0.95);
        @SuppressWarnings("unchecked")
        Map<String, Object> correlation = (Map<String, Object>) first.data().get("correlation");
        assertThat(correlation).containsEntry("alignedPairs", 6);
        assertThat((Double) correlation.get("pearson")).isBetween(-0.97, -0.96);
    }

    @Test
    void singlePointAboveThresholdIsNotSustained() {
        List<PrometheusMetricPoint> points = List.of(
                new PrometheusMetricPoint("cpu-app-1", Map.of(), 1L, 0.99, 0)
        );

        AnalysisFact fact = new ResourceSaturation().analyze(points, 0.9, 2, List.of(), List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> windows = (List<Map<String, Object>>) fact.data().get("windows");
        assertThat(windows).isEmpty();
    }

    @Test
    void fewerThanThreeAlignedPairsYieldNullCorrelation() {
        List<PrometheusMetricPoint> points = List.of(
                new PrometheusMetricPoint("cpu-app-1", Map.of(), 1L, 0.5, 0),
                new PrometheusMetricPoint("cpu-app-1", Map.of(), 2L, 0.6, 0)
        );
        List<MetricTick> ticks = List.of(
                new MetricTick(1_000L, List.of(), new MetricTick.LabelMetric("__total__", 10, 0, 10, 100, 120)),
                new MetricTick(2_000L, List.of(), new MetricTick.LabelMetric("__total__", 10, 0, 12, 100, 120))
        );

        AnalysisFact fact = new ResourceSaturation().analyze(points, 0.9, 2, ticks, List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> correlation = (Map<String, Object>) fact.data().get("correlation");
        assertThat(correlation)
                .containsEntry("alignedPairs", 2)
                .containsEntry("pearson", null);
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.analysis.ResourceSaturationTest"`
Expected: 编译失败（`ResourceSaturation` 不存在）

- [x] **Step 3: 实现**

```java
package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.facade.data.PrometheusMetricPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ResourceSaturation {
    public static final String ALGORITHM_ID = "resource-saturation";
    public static final String VERSION = "1";

    public AnalysisFact analyze(
            List<PrometheusMetricPoint> points,
            double saturationThreshold,
            int minSustainedPoints,
            List<MetricTick> loadTicks,
            List<String> evidenceRefs
    ) {
        List<PrometheusMetricPoint> usable = points == null ? List.of() : points.stream()
                .filter(point -> point != null)
                .toList();
        Map<String, List<PrometheusMetricPoint>> bySeries = new TreeMap<>();
        for (PrometheusMetricPoint point : usable) {
            bySeries.computeIfAbsent(
                    point.displayName() == null ? "" : point.displayName(),
                    key -> new ArrayList<>()
            ).add(point);
        }

        List<Map<String, Object>> windows = new ArrayList<>();
        for (Map.Entry<String, List<PrometheusMetricPoint>> entry : bySeries.entrySet()) {
            List<PrometheusMetricPoint> series = entry.getValue().stream()
                    .sorted(Comparator.comparingLong(PrometheusMetricPoint::timestamp))
                    .toList();
            int index = 0;
            while (index < series.size()) {
                if (series.get(index).value() < saturationThreshold) {
                    index++;
                    continue;
                }
                int start = index;
                double max = series.get(index).value();
                while (index < series.size() && series.get(index).value() >= saturationThreshold) {
                    max = Math.max(max, series.get(index).value());
                    index++;
                }
                if (index - start >= minSustainedPoints) {
                    Map<String, Object> window = new LinkedHashMap<>();
                    window.put("series", entry.getKey());
                    window.put("fromEpochSec", series.get(start).timestamp());
                    window.put("toEpochSec", series.get(index - 1).timestamp());
                    window.put("points", index - start);
                    window.put("maxValue", AnalysisMath.round4(max));
                    windows.add(window);
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("threshold", saturationThreshold);
        data.put("windows", List.copyOf(windows));
        data.put("correlation", correlation(usable, loadTicks));
        return new AnalysisFact(
                ALGORITHM_ID,
                VERSION,
                "resource-saturation",
                "sustained resource saturation windows and load correlation",
                data,
                evidenceRefs
        );
    }

    private Map<String, Object> correlation(List<PrometheusMetricPoint> points, List<MetricTick> loadTicks) {
        Map<Long, double[]> resourceBySecond = new TreeMap<>();
        for (PrometheusMetricPoint point : points) {
            double[] accumulator = resourceBySecond.computeIfAbsent(point.timestamp(), key -> new double[2]);
            accumulator[0] += point.value();
            accumulator[1] += 1;
        }
        Map<Long, Double> throughputBySecond = new TreeMap<>();
        if (loadTicks != null) {
            for (MetricTick tick : loadTicks) {
                if (tick == null || tick.overall() == null) {
                    continue;
                }
                throughputBySecond.put(tick.bucketTimeMs() / 1000, tick.overall().throughput());
            }
        }
        List<Double> resourceValues = new ArrayList<>();
        List<Double> throughputValues = new ArrayList<>();
        for (Map.Entry<Long, double[]> entry : resourceBySecond.entrySet()) {
            Double throughput = throughputBySecond.get(entry.getKey());
            if (throughput == null) {
                continue;
            }
            resourceValues.add(entry.getValue()[0] / entry.getValue()[1]);
            throughputValues.add(throughput);
        }
        Map<String, Object> correlation = new LinkedHashMap<>();
        correlation.put("alignedPairs", resourceValues.size());
        correlation.put("pearson", resourceValues.size() < 3
                ? null
                : AnalysisMath.round4(pearson(resourceValues, throughputValues)));
        return correlation;
    }

    private double pearson(List<Double> xs, List<Double> ys) {
        double meanX = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanY = ys.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double numerator = 0;
        double sumX = 0;
        double sumY = 0;
        for (int index = 0; index < xs.size(); index++) {
            double dx = xs.get(index) - meanX;
            double dy = ys.get(index) - meanY;
            numerator += dx * dy;
            sumX += dx * dx;
            sumY += dy * dy;
        }
        if (sumX == 0 || sumY == 0) {
            return 0;
        }
        return numerator / Math.sqrt(sumX * sumY);
    }
}
```

- [x] **Step 4: 跑测试确认通过**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.analysis.ResourceSaturationTest"`
Expected: PASS（3 个用例）

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/analysis/ResourceSaturation.java backend/src/test/java/com/yr/perftest/platform/analysis/ResourceSaturationTest.java
git commit -m "feat：新增资源饱和与压测相关性分析算法（T7）"
```

---

### Task 5: 执行间可比性与差异（execution-compare v1）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/analysis/ExecutionComparison.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/analysis/ExecutionComparisonTest.java`

**Interfaces:**
- Consumes: `TaskExecutionResult.AggregateRow`（既有 record：`label,average,p95,errorRate,throughput` 等 12 字段）
- Produces:
  - `ExecutionComparison.ALGORITHM_ID="execution-compare"`、`ExecutionComparison.VERSION="1"`
  - `record ExecutionComparison.ExecutionSide(long executionId, long scenarioId, Long durationMs, List<TaskExecutionResult.AggregateRow> rows)`
  - `compare(ExecutionSide baseline, ExecutionSide candidate, List<String> evidenceRefs) -> AnalysisFact`
- `data` 键：`baselineExecutionId`、`candidateExecutionId`、`comparable`(boolean)、`reasons`(List<String>)、`labels`(List of `{label,baselineP95,candidateP95,p95DeltaPct,avgRtDeltaPct,throughputDeltaPct,errorRateDelta,verdict}`)、`overallVerdict`（`REGRESSED`/`IMPROVED`/`STABLE`/`NOT_COMPARABLE`）
- 可比规则：同 scenarioId + 标签集合一致 + 双方 durationMs 均有效（非 null 且 >0）时比例须 ∈ [0.8, 1.25]（任一方时长缺失则跳过时长检查）；单标签 verdict 按 p95DeltaPct（>5 REGRESSED / <-5 IMPROVED / 否则 STABLE）

- [x] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionComparisonTest {
    private static TaskExecutionResult.AggregateRow row(String label, long average, long p95, double errorRate, double throughput) {
        return new TaskExecutionResult.AggregateRow(
                label, "thread", 1000, average, average, p95, p95, p95, 10, p95 + 50, errorRate, throughput
        );
    }

    private static ExecutionComparison.ExecutionSide baseline() {
        return new ExecutionComparison.ExecutionSide(1L, 10L, 60_000L, List.of(
                row("checkout", 150, 200, 0.01, 50),
                row("search", 80, 100, 0, 100)
        ));
    }

    private static ExecutionComparison.ExecutionSide candidate() {
        return new ExecutionComparison.ExecutionSide(2L, 10L, 61_000L, List.of(
                row("checkout", 200, 300, 0.02, 45),
                row("search", 79, 98, 0, 102)
        ));
    }

    @Test
    void goldenPairProducesDeterministicDiff() {
        AnalysisFact first = new ExecutionComparison().compare(baseline(), candidate(), List.of("aggregate#1", "aggregate#2"));
        AnalysisFact second = new ExecutionComparison().compare(baseline(), candidate(), List.of("aggregate#1", "aggregate#2"));

        assertThat(first).isEqualTo(second);
        assertThat(first.algorithmId()).isEqualTo("execution-compare");
        assertThat(first.algorithmVersion()).isEqualTo("1");
        assertThat(first.data())
                .containsEntry("baselineExecutionId", 1L)
                .containsEntry("candidateExecutionId", 2L)
                .containsEntry("comparable", true)
                .containsEntry("overallVerdict", "REGRESSED");
        assertThat(first.data().get("reasons")).isEqualTo(List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> labels = (List<Map<String, Object>>) first.data().get("labels");
        assertThat(labels).hasSize(2);
        assertThat(labels.get(0))
                .containsEntry("label", "checkout")
                .containsEntry("baselineP95", 200L)
                .containsEntry("candidateP95", 300L)
                .containsEntry("p95DeltaPct", 50.0)
                .containsEntry("avgRtDeltaPct", 33.3333)
                .containsEntry("throughputDeltaPct", -10.0)
                .containsEntry("errorRateDelta", 0.01)
                .containsEntry("verdict", "REGRESSED");
        assertThat(labels.get(1))
                .containsEntry("label", "search")
                .containsEntry("p95DeltaPct", -2.0)
                .containsEntry("verdict", "STABLE");
    }

    @Test
    void differentScenarioIsNotComparable() {
        ExecutionComparison.ExecutionSide other = new ExecutionComparison.ExecutionSide(
                3L, 99L, 60_000L, candidate().rows());

        AnalysisFact fact = new ExecutionComparison().compare(baseline(), other, List.of());

        assertThat(fact.data())
                .containsEntry("comparable", false)
                .containsEntry("overallVerdict", "NOT_COMPARABLE");
        assertThat(fact.data().get("reasons")).isEqualTo(List.of("different scenario"));
        assertThat(fact.data().get("labels")).isEqualTo(List.of());
    }

    @Test
    void durationBeyondToleranceIsNotComparable() {
        ExecutionComparison.ExecutionSide shortRun = new ExecutionComparison.ExecutionSide(
                3L, 10L, 30_000L, candidate().rows());

        AnalysisFact fact = new ExecutionComparison().compare(baseline(), shortRun, List.of());

        assertThat(fact.data())
                .containsEntry("comparable", false)
                .containsEntry("overallVerdict", "NOT_COMPARABLE");
        assertThat(fact.data().get("reasons")).isEqualTo(List.of("duration differs by more than 25%"));
    }

    @Test
    void differingLabelSetsAreNotComparable() {
        ExecutionComparison.ExecutionSide missing = new ExecutionComparison.ExecutionSide(
                3L, 10L, 60_000L, List.of(row("checkout", 200, 300, 0.02, 45)));

        AnalysisFact fact = new ExecutionComparison().compare(baseline(), missing, List.of());

        assertThat(fact.data()).containsEntry("comparable", false);
        assertThat(fact.data().get("reasons")).isEqualTo(List.of("label sets differ"));
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.analysis.ExecutionComparisonTest"`
Expected: 编译失败（`ExecutionComparison` 不存在）

- [x] **Step 3: 实现**

```java
package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.TaskExecutionResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ExecutionComparison {
    public static final String ALGORITHM_ID = "execution-compare";
    public static final String VERSION = "1";
    private static final double DURATION_TOLERANCE_LOW = 0.8;
    private static final double DURATION_TOLERANCE_HIGH = 1.25;

    public record ExecutionSide(
            long executionId,
            long scenarioId,
            Long durationMs,
            List<TaskExecutionResult.AggregateRow> rows
    ) {
    }

    public AnalysisFact compare(ExecutionSide baseline, ExecutionSide candidate, List<String> evidenceRefs) {
        List<String> reasons = new ArrayList<>();
        if (baseline.scenarioId() != candidate.scenarioId()) {
            reasons.add("different scenario");
        }
        Map<String, TaskExecutionResult.AggregateRow> baselineRows = byLabel(baseline.rows());
        Map<String, TaskExecutionResult.AggregateRow> candidateRows = byLabel(candidate.rows());
        if (!baselineRows.keySet().equals(candidateRows.keySet())) {
            reasons.add("label sets differ");
        }
        if (baseline.durationMs() != null && candidate.durationMs() != null
                && baseline.durationMs() > 0 && candidate.durationMs() > 0) {
            double ratio = (double) candidate.durationMs() / baseline.durationMs();
            if (ratio < DURATION_TOLERANCE_LOW || ratio > DURATION_TOLERANCE_HIGH) {
                reasons.add("duration differs by more than 25%");
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baselineExecutionId", baseline.executionId());
        data.put("candidateExecutionId", candidate.executionId());
        boolean comparable = reasons.isEmpty();
        data.put("comparable", comparable);
        data.put("reasons", List.copyOf(reasons));
        if (!comparable) {
            data.put("labels", List.of());
            data.put("overallVerdict", "NOT_COMPARABLE");
            return new AnalysisFact(ALGORITHM_ID, VERSION, "execution-compare",
                    "baseline vs candidate per-label diff", data, evidenceRefs);
        }

        List<Map<String, Object>> labels = new ArrayList<>();
        boolean anyRegressed = false;
        boolean anyImproved = false;
        for (Map.Entry<String, TaskExecutionResult.AggregateRow> entry : baselineRows.entrySet()) {
            TaskExecutionResult.AggregateRow base = entry.getValue();
            TaskExecutionResult.AggregateRow cand = candidateRows.get(entry.getKey());
            Double p95DeltaPct = AnalysisMath.deltaPct(base.p95(), cand.p95());
            String verdict = verdict(p95DeltaPct);
            anyRegressed |= "REGRESSED".equals(verdict);
            anyImproved |= "IMPROVED".equals(verdict);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", entry.getKey());
            row.put("baselineP95", base.p95());
            row.put("candidateP95", cand.p95());
            row.put("p95DeltaPct", p95DeltaPct);
            row.put("avgRtDeltaPct", AnalysisMath.deltaPct(base.average(), cand.average()));
            row.put("throughputDeltaPct", AnalysisMath.deltaPct(base.throughput(), cand.throughput()));
            row.put("errorRateDelta", AnalysisMath.round4(cand.errorRate() - base.errorRate()));
            row.put("verdict", verdict);
            labels.add(row);
        }
        data.put("labels", List.copyOf(labels));
        data.put("overallVerdict", anyRegressed ? "REGRESSED" : anyImproved ? "IMPROVED" : "STABLE");
        return new AnalysisFact(ALGORITHM_ID, VERSION, "execution-compare",
                "baseline vs candidate per-label diff", data, evidenceRefs);
    }

    private String verdict(Double p95DeltaPct) {
        if (p95DeltaPct == null) {
            return "REGRESSED";
        }
        if (p95DeltaPct > 5.0) {
            return "REGRESSED";
        }
        if (p95DeltaPct < -5.0) {
            return "IMPROVED";
        }
        return "STABLE";
    }

    private Map<String, TaskExecutionResult.AggregateRow> byLabel(List<TaskExecutionResult.AggregateRow> rows) {
        Map<String, TaskExecutionResult.AggregateRow> byLabel = new TreeMap<>();
        if (rows != null) {
            for (TaskExecutionResult.AggregateRow row : rows) {
                if (row != null && row.label() != null) {
                    byLabel.put(row.label(), row);
                }
            }
        }
        return byLabel;
    }
}
```

- [x] **Step 4: 跑测试确认通过**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.analysis.ExecutionComparisonTest"`
Expected: PASS（4 个用例）

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/analysis/ExecutionComparison.java backend/src/test/java/com/yr/perftest/platform/analysis/ExecutionComparisonTest.java
git commit -m "feat：新增执行间可比性与差异分析算法（T7）"
```

---

### Task 6: 分析装配 + Facade + agent 面入口

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/analysis/SourceCompleteness.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/analysis/AnalysisReport.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/analysis/AnalysisService.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/facade/AnalysisFacade.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/agent/analysis/AgentAnalysisController.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/agent/AgentAnalysisApiTest.java`

**Interfaces:**
- Consumes:
  - 算法：`new TrendAnalysis().analyze(List<MetricTick>, List<String>)`、`new AnomalyDetection().analyze(List<MetricTick>, List<String>)`、`new ErrorClustering().analyze(List<TaskExecutionResult.Sample>, List<String>)`、`new ResourceSaturation().analyze(List<PrometheusMetricPoint>, double, int, List<MetricTick>, List<String>)`、`new ExecutionComparison().compare(ExecutionSide, ExecutionSide, List<String>)`、`ExecutionComparison.ExecutionSide(long executionId, long scenarioId, Long durationMs, List<TaskExecutionResult.AggregateRow> rows)`；常量 `*.ALGORITHM_ID` / `*.VERSION`
  - `DataFacade`（既有）：`getExecutionSummary(long) -> ExecutionSummary{scenarioId(),createdAt(),startedAt(),endedAt(),durationMs()}`、`queryMetricSeries(long, Instant, Instant, String, String, PageBudget) -> BoundedPage<MetricTick>`、`queryFailureSamples(long, String, PageBudget) -> BoundedPage<TaskExecutionResult.Sample>`、`queryAggregateRows(long, String, PageBudget) -> BoundedPage<TaskExecutionResult.AggregateRow>`、`queryPrometheus(long, String, Instant, Instant, int, String, PageBudget) -> BoundedPage<PrometheusMetricPoint>`
  - `BoundedPage{items(), availability()}`、`Availability{present(), truncated(), missingReason(), sourceRef()}`、`PageBudget.defaults()`、`FacadeGuard.requirePrincipal(Supplier<T>)`
- Produces:
  - `record SourceCompleteness(String sourceType, boolean present, boolean truncated, String missingReason)`
  - `record AnalysisReport(String schemaVersion, long executionId, Instant from, Instant to, Map<String,String> algorithmVersions, List<AnalysisFact> facts, List<SourceCompleteness> completeness)`
  - `AnalysisService.analyze(long executionId, Instant from, Instant to, List<String> kinds, String metricSelector) -> AnalysisReport`（from/to 可 null，默认取执行 startedAt/createdAt 与 endedAt/now；kinds 空 = 全部四种；metricSelector 可 null，默认 `SERVER_CPU`，取值限 `MetricKind` 枚举名：SERVER_CPU/SERVER_MEM/SERVER_LOAD/JVM_HEAP_PCT/JVM_GC/JVM_THREADS/JVM_CPU 等，非法值由 `DataFacade` 抛 `IllegalArgumentException` → 400）
  - `AnalysisService.compare(long baselineExecutionId, long candidateExecutionId) -> AnalysisFact`
  - `AnalysisFacade.getExecutionAnalysis(long, Instant, Instant, List<String>, String) -> AnalysisReport`、`AnalysisFacade.compareExecutions(long, long) -> AnalysisFact`
  - REST：`GET /api/agent/executions/{executionId}/analysis?from=&to=&kinds=&metric=`、`GET /api/agent/executions/compare?baselineId=&candidateId=`

- [x] **Step 1: 写失败测试（端到端 MockMvc）**

```java
package com.yr.perftest.platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.aggregate.PersistentExecutionMetricSeriesRecord;
import com.yr.perftest.platform.execution.aggregate.PersistentExecutionMetricSeriesRepository;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-analysis-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentAnalysisApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    @Autowired
    private PersistentExecutionMetricSeriesRepository metricSeriesRepository;

    private String apiKey;
    private long executionId;

    @BeforeEach
    void setUp() throws Exception {
        String adminToken = loginToken();
        MvcResult issued = mockMvc.perform(post("/api/agent-api-keys")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"ops\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        apiKey = objectMapper.readTree(issued.getResponse().getContentAsString()).get("plainKey").asText();

        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0)
        );
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenario.getId(), "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}")
        );
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        executionId = executionRepository.save(execution).getId();

        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(executionId, 1_000L, "checkout", 10L, 0L, 50.0, 100L, 120L));
        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(executionId, 1_000L, "search", 10L, 0L, 100.0, 80L, 100L));
        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(executionId, 2_000L, "checkout", 10L, 1L, 45.0, 200L, 260L));
        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(executionId, 2_000L, "search", 10L, 0L, 95.0, 90L, 110L));
    }

    @Test
    void returnsDeterministicAnalysisEnvelope() throws Exception {
        String path = "/api/agent/executions/" + executionId + "/analysis"
                + "?from=1970-01-01T00:00:00Z&to=1970-01-01T00:00:10Z";
        MvcResult first = mockMvc.perform(get(path).header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion", is("1")))
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.schemaVersion", is("1")))
                .andExpect(jsonPath("$.data.executionId", is((int) executionId)))
                .andExpect(jsonPath("$.data.algorithmVersions.trend", is("1")))
                .andExpect(jsonPath("$.data.algorithmVersions.anomaly", is("1")))
                .andExpect(jsonPath("$.data.algorithmVersions['error-cluster']", is("1")))
                .andExpect(jsonPath("$.data.algorithmVersions['resource-saturation']", is("1")))
                .andExpect(jsonPath("$.data.facts.length()", is(4)))
                .andExpect(jsonPath("$.data.facts[0].kind", is("trend")))
                .andExpect(jsonPath("$.data.facts[0].algorithmVersion", is("1")))
                .andExpect(jsonPath("$.data.facts[0].data.tickCount", is(2)))
                .andExpect(jsonPath("$.data.facts[1].kind", is("anomaly")))
                .andExpect(jsonPath("$.data.facts[2].kind", is("error-cluster")))
                .andExpect(jsonPath("$.data.facts[3].kind", is("resource-saturation")))
                .andExpect(jsonPath("$.data.completeness.length()", is(3)))
                .andReturn();
        MvcResult second = mockMvc.perform(get(path).header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstFacts = objectMapper.readTree(first.getResponse().getContentAsString()).at("/data/facts");
        JsonNode secondFacts = objectMapper.readTree(second.getResponse().getContentAsString()).at("/data/facts");
        org.assertj.core.api.Assertions.assertThat(secondFacts).isEqualTo(firstFacts);
    }

    @Test
    void kindsParameterSelectsAlgorithms() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/analysis")
                        .param("from", "1970-01-01T00:00:00Z")
                        .param("to", "1970-01-01T00:00:10Z")
                        .param("kinds", "trend")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.facts.length()", is(1)))
                .andExpect(jsonPath("$.data.facts[0].kind", is("trend")));
    }

    @Test
    void comparesExecutionWithItselfAsComparable() throws Exception {
        mockMvc.perform(get("/api/agent/executions/compare")
                        .param("baselineId", Long.toString(executionId))
                        .param("candidateId", Long.toString(executionId))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.algorithmId", is("execution-compare")))
                .andExpect(jsonPath("$.data.algorithmVersion", is("1")))
                .andExpect(jsonPath("$.data.data.comparable", is(true)))
                .andExpect(jsonPath("$.data.data.overallVerdict", is("STABLE")));
    }

    @Test
    void returnsNotFoundForMissingExecution() throws Exception {
        mockMvc.perform(get("/api/agent/executions/999999/analysis")
                        .param("from", "1970-01-01T00:00:00Z")
                        .param("to", "1970-01-01T00:00:10Z")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    @Test
    void returnsAuthenticationFailedWithoutIdentity() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/analysis"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("AUTHENTICATION_FAILED")));
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }
}
```

注意：Prometheus 数据源在测试环境无监控绑定，`AnalysisService` 必须把它记为 `present=false`（`NO_DATA` 或 `SOURCE_UNAVAILABLE` 均可），resource-saturation 事实仍以空输入产出，不得让整个请求失败。

- [x] **Step 2: 跑测试确认失败**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.agent.AgentAnalysisApiTest"`
Expected: 404 / 编译失败（`AnalysisService`、`AgentAnalysisController` 不存在）

- [x] **Step 3: 实现**

`SourceCompleteness.java`：

```java
package com.yr.perftest.platform.analysis;

public record SourceCompleteness(
        String sourceType,
        boolean present,
        boolean truncated,
        String missingReason
) {
}
```

`AnalysisReport.java`：

```java
package com.yr.perftest.platform.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalysisReport(
        String schemaVersion,
        long executionId,
        Instant from,
        Instant to,
        Map<String, String> algorithmVersions,
        List<AnalysisFact> facts,
        List<SourceCompleteness> completeness
) {
    public AnalysisReport {
        algorithmVersions = algorithmVersions == null ? Map.of() : Map.copyOf(algorithmVersions);
        facts = facts == null ? List.of() : List.copyOf(facts);
        completeness = completeness == null ? List.of() : List.copyOf(completeness);
    }
}
```

`AnalysisService.java`：

```java
package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.data.ExecutionSummary;
import com.yr.perftest.platform.facade.data.PrometheusMetricPoint;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.PageBudget;
import com.yr.perftest.platform.monitoring.MetricKind;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class AnalysisService {
    public static final String SCHEMA_VERSION = "1";
    private static final List<String> ALL_KINDS = List.of("trend", "anomaly", "error-cluster", "resource-saturation");
    private static final String DEFAULT_METRIC_SELECTOR = "SERVER_CPU";
    private static final int PROMETHEUS_STEP_SECONDS = 15;
    private static final double SATURATION_THRESHOLD = 0.9;
    private static final int MIN_SUSTAINED_POINTS = 2;

    private final DataFacade dataFacade;

    public AnalysisService(DataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    public AnalysisReport analyze(long executionId, Instant from, Instant to, List<String> kinds, String metricSelector) {
        ExecutionSummary summary = dataFacade.getExecutionSummary(executionId);
        Instant effectiveFrom = from != null
                ? from
                : summary.startedAt() != null ? summary.startedAt() : summary.createdAt();
        Instant effectiveTo = to != null
                ? to
                : summary.endedAt() != null ? summary.endedAt() : Instant.now();
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("analysis time range is invalid");
        }
        List<String> selected = kinds == null || kinds.isEmpty() ? ALL_KINDS : kinds;
        String selector = metricSelector == null || metricSelector.isBlank()
                ? DEFAULT_METRIC_SELECTOR
                : metricSelector;
        try {
            MetricKind.valueOf(selector);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("analysis metric selector is invalid", exception);
        }
        PageBudget budget = PageBudget.defaults();

        SourceData<MetricTick> series = load("series", () ->
                dataFacade.queryMetricSeries(executionId, effectiveFrom, effectiveTo, "15s", null, budget));
        SourceData<TaskExecutionResult.Sample> failures = load("failure-sample", () ->
                dataFacade.queryFailureSamples(executionId, null, budget));
        SourceData<PrometheusMetricPoint> resources = load("prometheus", () ->
                dataFacade.queryPrometheus(executionId, selector, effectiveFrom, effectiveTo, PROMETHEUS_STEP_SECONDS, null, budget));

        List<AnalysisFact> facts = new ArrayList<>();
        Map<String, String> versions = new LinkedHashMap<>();
        if (selected.contains("trend")) {
            facts.add(new TrendAnalysis().analyze(series.items(), refs(series)));
            versions.put(TrendAnalysis.ALGORITHM_ID, TrendAnalysis.VERSION);
        }
        if (selected.contains("anomaly")) {
            facts.add(new AnomalyDetection().analyze(series.items(), refs(series)));
            versions.put(AnomalyDetection.ALGORITHM_ID, AnomalyDetection.VERSION);
        }
        if (selected.contains("error-cluster")) {
            facts.add(new ErrorClustering().analyze(failures.items(), refs(failures)));
            versions.put(ErrorClustering.ALGORITHM_ID, ErrorClustering.VERSION);
        }
        if (selected.contains("resource-saturation")) {
            facts.add(new ResourceSaturation().analyze(
                    resources.items(),
                    SATURATION_THRESHOLD,
                    MIN_SUSTAINED_POINTS,
                    series.items(),
                    refs(resources, series)
            ));
            versions.put(ResourceSaturation.ALGORITHM_ID, ResourceSaturation.VERSION);
        }
        return new AnalysisReport(
                SCHEMA_VERSION,
                executionId,
                effectiveFrom,
                effectiveTo,
                versions,
                facts,
                List.of(series.completeness(), failures.completeness(), resources.completeness())
        );
    }

    public AnalysisFact compare(long baselineExecutionId, long candidateExecutionId) {
        ExecutionComparison.ExecutionSide baseline = side(baselineExecutionId);
        ExecutionComparison.ExecutionSide candidate = side(candidateExecutionId);
        return new ExecutionComparison().compare(
                baseline,
                candidate,
                List.of("aggregate#" + baselineExecutionId, "aggregate#" + candidateExecutionId)
        );
    }

    private ExecutionComparison.ExecutionSide side(long executionId) {
        ExecutionSummary summary = dataFacade.getExecutionSummary(executionId);
        BoundedPage<TaskExecutionResult.AggregateRow> page =
                dataFacade.queryAggregateRows(executionId, null, PageBudget.defaults());
        return new ExecutionComparison.ExecutionSide(
                executionId,
                summary.scenarioId(),
                summary.durationMs(),
                page.items()
        );
    }

    private <T> SourceData<T> load(String sourceType, Supplier<BoundedPage<T>> query) {
        try {
            BoundedPage<T> page = query.get();
            Availability availability = page.availability();
            return new SourceData<>(
                    page.items(),
                    new SourceCompleteness(
                            sourceType,
                            availability != null && availability.present(),
                            availability != null && availability.truncated(),
                            availability == null || availability.missingReason() == null
                                    ? null
                                    : availability.missingReason().name()
                    ),
                    availability == null ? null : availability.sourceRef()
            );
        } catch (RuntimeException exception) {
            return new SourceData<>(
                    List.of(),
                    new SourceCompleteness(sourceType, false, false, "SOURCE_UNAVAILABLE"),
                    null
            );
        }
    }

    private List<String> refs(SourceData<?>... sources) {
        List<String> refs = new ArrayList<>();
        for (SourceData<?> source : sources) {
            if (source.sourceRef() != null) {
                refs.add(source.sourceRef());
            }
        }
        return List.copyOf(refs);
    }

    private record SourceData<T>(List<T> items, SourceCompleteness completeness, String sourceRef) {
    }
}
```

`AnalysisFacade.java`：

```java
package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.analysis.AnalysisFact;
import com.yr.perftest.platform.analysis.AnalysisReport;
import com.yr.perftest.platform.analysis.AnalysisService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AnalysisFacade {
    private final FacadeGuard guard;
    private final AnalysisService analysisService;

    public AnalysisFacade(FacadeGuard guard, AnalysisService analysisService) {
        this.guard = guard;
        this.analysisService = analysisService;
    }

    public AnalysisReport getExecutionAnalysis(long executionId, Instant from, Instant to, List<String> kinds, String metricSelector) {
        return guard.requirePrincipal(() -> analysisService.analyze(executionId, from, to, kinds, metricSelector));
    }

    public AnalysisFact compareExecutions(long baselineExecutionId, long candidateExecutionId) {
        return guard.requirePrincipal(() -> analysisService.compare(baselineExecutionId, candidateExecutionId));
    }
}
```

`AgentAnalysisController.java`：

```java
package com.yr.perftest.platform.agent.analysis;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.analysis.AnalysisFact;
import com.yr.perftest.platform.analysis.AnalysisReport;
import com.yr.perftest.platform.facade.AnalysisFacade;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/executions")
public class AgentAnalysisController {
    private final AnalysisFacade analysisFacade;

    public AgentAnalysisController(AnalysisFacade analysisFacade) {
        this.analysisFacade = analysisFacade;
    }

    @GetMapping("/{executionId}/analysis")
    public ApiResponse<AnalysisReport> analysis(
            @PathVariable long executionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) List<String> kinds,
            @RequestParam(required = false) String metric
    ) {
        AnalysisReport report = analysisFacade.getExecutionAnalysis(executionId, from, to, kinds, metric);
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, report);
    }

    @GetMapping("/compare")
    public ApiResponse<AnalysisFact> compare(
            @RequestParam long baselineId,
            @RequestParam long candidateId
    ) {
        AnalysisFact fact = analysisFacade.compareExecutions(baselineId, candidateId);
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, fact);
    }
}
```

- [x] **Step 4: 跑测试确认通过**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.agent.AgentAnalysisApiTest"`
Expected: PASS（5 个用例）

- [x] **Step 5: 回归相关测试**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.agent.*" --tests "com.yr.perftest.platform.analysis.*"`
Expected: 全部 PASS（含 `AgentLayeringConstraintTest` 分层约束）

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/analysis backend/src/main/java/com/yr/perftest/platform/facade/AnalysisFacade.java backend/src/main/java/com/yr/perftest/platform/agent/analysis backend/src/test/java/com/yr/perftest/platform/agent/AgentAnalysisApiTest.java
git commit -m "feat：打通 agent 面确定性分析入口（T7）"
```

---

## T8 压测执行工具化

### Task 7: 写操作幂等键（存储 + 服务 + 冲突异常）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/execution/IdempotencyConflictException.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/execution/RequestHashing.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/execution/PersistentIdempotencyRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/execution/PersistentIdempotencyRepository.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/execution/IdempotencyService.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/execution/IdempotencyServiceTest.java`

**Interfaces:**
- Consumes: Spring Data JPA（既有模式，参考 `identity` 包实体）
- Produces:
  - `IdempotencyConflictException extends RuntimeException`
  - `RequestHashing.sha256(String) -> String`（64 位小写 hex）
  - `IdempotencyService.execute(String idemKey, String requestHash, Supplier<Long> action) -> IdempotencyService.IdempotentExecution`
  - `record IdempotencyService.IdempotentExecution(long executionId, boolean replayed)`
  - 语义：key 空白 → 直接执行不去重；key 已存在且 hash 一致 → 返回原 executionId、`replayed=true`，不执行 action；key 已存在且 hash 不同 → 抛 `IdempotencyConflictException`；否则执行 action 并落库

- [x] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.execution;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:idempotency-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class IdempotencyServiceTest {
    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private PersistentIdempotencyRepository repository;

    @Test
    void sameKeySameRequestReturnsOriginalExecutionWithoutRerunning() {
        AtomicLong idSource = new AtomicLong(100);
        String hash = RequestHashing.sha256("payload");

        IdempotencyService.IdempotentExecution first =
                idempotencyService.execute("key-1", hash, idSource::incrementAndGet);
        IdempotencyService.IdempotentExecution second =
                idempotencyService.execute("key-1", hash, idSource::incrementAndGet);

        assertThat(first.executionId()).isEqualTo(101L);
        assertThat(first.replayed()).isFalse();
        assertThat(second.executionId()).isEqualTo(101L);
        assertThat(second.replayed()).isTrue();
        assertThat(idSource).hasValue(101L);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentRequestConflicts() {
        idempotencyService.execute("key-1", RequestHashing.sha256("payload-a"), () -> 1L);

        assertThatThrownBy(() ->
                idempotencyService.execute("key-1", RequestHashing.sha256("payload-b"), () -> 2L))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("different request");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void blankKeySkipsDeduplication() {
        IdempotencyService.IdempotentExecution first =
                idempotencyService.execute("  ", "hash", () -> 1L);
        IdempotencyService.IdempotentExecution second =
                idempotencyService.execute(null, "hash", () -> 2L);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isFalse();
        assertThat(repository.count()).isZero();
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.execution.IdempotencyServiceTest"`
Expected: 编译失败（`IdempotencyService` 等不存在）

- [x] **Step 3: 实现**

`IdempotencyConflictException.java`：

```java
package com.yr.perftest.platform.execution;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
```

`RequestHashing.java`：

```java
package com.yr.perftest.platform.execution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class RequestHashing {
    private RequestHashing() {
    }

    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
```

`PersistentIdempotencyRecord.java`：

```java
package com.yr.perftest.platform.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class PersistentIdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String idemKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private Long executionId;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentIdempotencyRecord() {
    }

    public PersistentIdempotencyRecord(String idemKey, String requestHash, Long executionId) {
        this.idemKey = idemKey;
        this.requestHash = requestHash;
        this.executionId = executionId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

`PersistentIdempotencyRepository.java`：

```java
package com.yr.perftest.platform.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersistentIdempotencyRepository extends JpaRepository<PersistentIdempotencyRecord, Long> {
    Optional<PersistentIdempotencyRecord> findByIdemKey(String idemKey);
}
```

`IdempotencyService.java`：

```java
package com.yr.perftest.platform.execution;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final PersistentIdempotencyRepository repository;

    public IdempotencyService(PersistentIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public IdempotentExecution execute(String idemKey, String requestHash, Supplier<Long> action) {
        if (idemKey == null || idemKey.isBlank()) {
            return new IdempotentExecution(action.get(), false);
        }
        return repository.findByIdemKey(idemKey)
                .map(existing -> replay(existing, requestHash))
                .orElseGet(() -> create(idemKey, requestHash, action));
    }

    private IdempotentExecution replay(PersistentIdempotencyRecord existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("idempotency key was used with a different request");
        }
        return new IdempotentExecution(existing.getExecutionId(), true);
    }

    private IdempotentExecution create(String idemKey, String requestHash, Supplier<Long> action) {
        long executionId = action.get();
        try {
            repository.saveAndFlush(new PersistentIdempotencyRecord(idemKey, requestHash, executionId));
        } catch (DataIntegrityViolationException exception) {
            throw new IdempotencyConflictException("concurrent request with the same idempotency key");
        }
        return new IdempotentExecution(executionId, false);
    }

    public record IdempotentExecution(long executionId, boolean replayed) {
    }
}
```

- [x] **Step 4: 跑测试确认通过**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.execution.IdempotencyServiceTest"`
Expected: PASS（3 个用例）

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/execution/IdempotencyConflictException.java backend/src/main/java/com/yr/perftest/platform/execution/RequestHashing.java backend/src/main/java/com/yr/perftest/platform/execution/PersistentIdempotencyRecord.java backend/src/main/java/com/yr/perftest/platform/execution/PersistentIdempotencyRepository.java backend/src/main/java/com/yr/perftest/platform/execution/IdempotencyService.java backend/src/test/java/com/yr/perftest/platform/execution/IdempotencyServiceTest.java
git commit -m "feat：新增写操作幂等键支持（T8）"
```

---

### Task 8: 统一启动/停止/取消语义（ExecutionControlService）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/execution/ExecutionConflictException.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/PersistentScenarioExecutionRecord.java`（+`markCancelled()`）
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/distributed/DistributedJmeterExecutionRunner.java`（`markInterrupted` 跳过终态，约 482-491 行）
- Create: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionControlService.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/ExecutionControlServiceTest.java`

**Interfaces:**
- Consumes:
  - `IdempotencyService.execute(String, String, Supplier<Long>) -> IdempotentExecution(long executionId, boolean replayed)`（Task 7）
  - `RequestHashing.sha256(String)`（Task 7）
  - `ScenarioExecutionService`（既有）：`triggerExecution(long scenarioId, String executionName, Long threadGroupConfigId, Integer threadGroupPresetSortOrder) -> ScenarioExecution`、`stopExecution(long)`、`getExecution(long) -> ScenarioExecution`
  - `ScenarioExecutionRuntime`（既有）：`requestStop(long) -> boolean`、`register(long)`、`isStopRequested(long)`
- Produces:
  - `ExecutionConflictException extends RuntimeException`
  - `PersistentScenarioExecutionRecord.markCancelled()`（status=CANCELLED、endTime=now、有 startTime 时补 durationMs）
  - `record ExecutionControlService.StartCommand(long scenarioId, String executionName, Long threadGroupConfigId, Integer threadGroupPresetSortOrder)`
  - `record ExecutionControlService.StartOutcome(long executionId, ExecutionStatus status, boolean replayed)`
  - `start(StartCommand, String idempotencyKey) -> StartOutcome`（同事务落幂等记录与执行；afterCommit 异步提交 runner，返回即稳定 ID + QUEUED）
  - `stop(long)` — 优雅停止：终态 → `ExecutionConflictException`；已 STOPPING → 幂等返回；否则委托 `stopExecution`
  - `cancel(long)` — 立即取消：终态 → `ExecutionConflictException`；否则 `requestStop` + `markCancelled`，runner 不得再改写终态
  - `status(long) -> ScenarioExecution`
  - 终态定义：SUCCESS / FAILED / CANCELLED / INTERRUPTED

- [x] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionConflictException;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.distributed.DistributedJmeterExecutionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:execution-control-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionControlServiceTest {
    private static final String CONFIG_JSON = "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}";

    @Autowired
    private ExecutionControlService controlService;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    @Autowired
    private ScenarioExecutionRuntime executionRuntime;

    @Autowired
    private DistributedJmeterExecutionRunner runner;

    private long scenarioId;

    @BeforeEach
    void setUp() {
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0));
        scenario.updateProfile("scenario-a", 1L, "{}", 1L, null, null, null);
        scenarioId = scenarioRepository.save(scenario).getId();
    }

    @Test
    void sameIdempotencyKeyStartsExecutionOnlyOnce() {
        ExecutionControlService.StartCommand command =
                new ExecutionControlService.StartCommand(scenarioId, "run-1", null, null);

        ExecutionControlService.StartOutcome first = controlService.start(command, "idem-1");
        ExecutionControlService.StartOutcome second = controlService.start(command, "idem-1");

        assertThat(first.status()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(first.replayed()).isFalse();
        assertThat(second.executionId()).isEqualTo(first.executionId());
        assertThat(second.replayed()).isTrue();
        assertThat(executionRepository.findAllByScenarioIdOrderByIdDesc(scenarioId)).hasSize(1);
    }

    @Test
    void cancelQueuedExecutionMarksCancelledAndRunnerDoesNotOverride() throws Exception {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        long executionId = execution.getId();
        executionRuntime.register(executionId);

        controlService.cancel(executionId);
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.CANCELLED);

        runner.submit(executionId);
        Thread.sleep(500);
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.CANCELLED);
    }

    @Test
    void stopRunningExecutionMarksStopping() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("result.jtl", "jmeter.log");
        long executionId = executionRepository.save(execution).getId();
        executionRuntime.register(executionId);

        controlService.stop(executionId);

        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.STOPPING);
        assertThat(executionRuntime.isStopRequested(executionId)).isTrue();
    }

    @Test
    void stopAndCancelOnFinishedExecutionConflict() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        long executionId = executionRepository.save(execution).getId();

        assertThatThrownBy(() -> controlService.stop(executionId))
                .isInstanceOf(ExecutionConflictException.class);
        assertThatThrownBy(() -> controlService.cancel(executionId))
                .isInstanceOf(ExecutionConflictException.class);
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    void cancelOnMissingExecutionThrowsNotFound() {
        assertThatThrownBy(() -> controlService.cancel(999999L))
                .hasMessageContaining("execution does not exist");
    }

    @Test
    void statusReturnsCurrentExecution() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));

        ScenarioExecution view = controlService.status(execution.getId());

        assertThat(view.id()).isEqualTo(execution.getId());
        assertThat(view.status()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(view.scenarioId()).isEqualTo(scenarioId);
    }
}
```

注意：`sameIdempotencyKeyStartsExecutionOnlyOnce` 中 runner 会因测试库无脚本版本而异步失败并把执行置为 FAILED——这是既有行为，不影响断言（断言只依赖同步返回的 QUEUED 与执行条数）；不要断言第二次调用的 status。

- [x] **Step 2: 跑测试确认失败**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.task.ExecutionControlServiceTest"`
Expected: 编译失败（`ExecutionControlService`、`ExecutionConflictException`、`markCancelled` 不存在）

- [x] **Step 3: 实现**

`ExecutionConflictException.java`：

```java
package com.yr.perftest.platform.execution;

public class ExecutionConflictException extends RuntimeException {
    public ExecutionConflictException(String message) {
        super(message);
    }
}
```

`PersistentScenarioExecutionRecord.java` 追加方法（放在 `markInterrupted` 之后）：

```java
    public void markCancelled() {
        this.status = ExecutionStatus.CANCELLED;
        this.endTime = Instant.now();
        if (startTime != null) {
            this.durationMs = Duration.between(startTime, endTime).toMillis();
        }
    }
```

`DistributedJmeterExecutionRunner.java` 的 `markInterrupted` 方法体中，在 `execution.markInterrupted(...)` 之前加终态守卫：

```java
    private void markInterrupted(long executionId, Integer exitCode, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId).orElse(null);
            if (execution == null) {
                return;
            }
            if (execution.getStatus() == ExecutionStatus.SUCCESS
                    || execution.getStatus() == ExecutionStatus.FAILED
                    || execution.getStatus() == ExecutionStatus.CANCELLED
                    || execution.getStatus() == ExecutionStatus.INTERRUPTED) {
                return;
            }
            execution.markInterrupted(exitCode, normalizeMessage(message));
            monitorBindingService.markEnd(executionId, execution.getEndTime());
        });
    }
```

`ExecutionControlService.java`：

```java
package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionConflictException;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.IdempotencyService;
import com.yr.perftest.platform.execution.RequestHashing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionControlService {
    private final ScenarioExecutionService scenarioExecutionService;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final ScenarioExecutionRuntime executionRuntime;
    private final IdempotencyService idempotencyService;

    public ExecutionControlService(
            ScenarioExecutionService scenarioExecutionService,
            PersistentScenarioExecutionRepository executionRepository,
            ScenarioExecutionRuntime executionRuntime,
            IdempotencyService idempotencyService
    ) {
        this.scenarioExecutionService = scenarioExecutionService;
        this.executionRepository = executionRepository;
        this.executionRuntime = executionRuntime;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public StartOutcome start(StartCommand command, String idempotencyKey) {
        String requestHash = RequestHashing.sha256(
                command.scenarioId() + "|" + command.executionName() + "|"
                        + command.threadGroupConfigId() + "|" + command.threadGroupPresetSortOrder());
        IdempotencyService.IdempotentExecution result = idempotencyService.execute(
                idempotencyKey,
                requestHash,
                () -> scenarioExecutionService.triggerExecution(
                        command.scenarioId(),
                        command.executionName(),
                        command.threadGroupConfigId(),
                        command.threadGroupPresetSortOrder()
                ).id()
        );
        ScenarioExecution execution = scenarioExecutionService.getExecution(result.executionId());
        return new StartOutcome(execution.id(), execution.status(), result.replayed());
    }

    @Transactional
    public void stop(long executionId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        if (isFinished(execution.getStatus())) {
            throw new ExecutionConflictException("execution already finished");
        }
        if (execution.getStatus() == ExecutionStatus.STOPPING) {
            return;
        }
        scenarioExecutionService.stopExecution(executionId);
    }

    @Transactional
    public void cancel(long executionId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        if (isFinished(execution.getStatus())) {
            throw new ExecutionConflictException("execution already finished");
        }
        executionRuntime.requestStop(executionId);
        execution.markCancelled();
    }

    @Transactional(readOnly = true)
    public ScenarioExecution status(long executionId) {
        return scenarioExecutionService.getExecution(executionId);
    }

    private PersistentScenarioExecutionRecord requireExecution(long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
    }

    private boolean isFinished(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCESS
                || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELLED
                || status == ExecutionStatus.INTERRUPTED;
    }

    public record StartCommand(
            long scenarioId,
            String executionName,
            Long threadGroupConfigId,
            Integer threadGroupPresetSortOrder
    ) {
    }

    public record StartOutcome(long executionId, ExecutionStatus status, boolean replayed) {
    }
}
```

- [x] **Step 4: 跑测试确认通过**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.task.ExecutionControlServiceTest"`
Expected: PASS（6 个用例）

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/execution/ExecutionConflictException.java backend/src/main/java/com/yr/perftest/platform/task/PersistentScenarioExecutionRecord.java backend/src/main/java/com/yr/perftest/platform/execution/distributed/DistributedJmeterExecutionRunner.java backend/src/main/java/com/yr/perftest/platform/task/ExecutionControlService.java backend/src/test/java/com/yr/perftest/platform/task/ExecutionControlServiceTest.java
git commit -m "feat：统一启动/停止/取消执行控制语义（T8）"
```

---

### Task 9: 执行预检 + 影响评估（ExecutionPrecheckService）

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/PersistentScenarioExecutionRepository.java`（+`countByStatusIn`）
- Create: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionPrecheckService.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/ExecutionPrecheckServiceTest.java`

**Interfaces:**
- Consumes:
  - `ExecutionConfigMerger.merge(plan, scenario, Long, Integer) -> ExecutionConfig`（既有；线程组配置不存在时抛 `ExecutionValidationException`）
  - `ExecutionConfig`（既有 record）：`threads()`、`duration()`、`controllerNodeId()`、`workerNodeIds()`、`monitorTargetIds()`
  - `PersistentExecutionNodeRepository.findById(long) -> Optional<PersistentExecutionNodeRecord>`；record 访问器 `getName()`、`getStatus()`（`ExecutionNodeStatus` UNKNOWN/AVAILABLE/OFFLINE）、`getLastMessage()`
- Produces:
  - `PersistentScenarioExecutionRepository.countByStatusIn(List<ExecutionStatus> statuses) -> long`
  - `record ExecutionPrecheckService.PrecheckNode(long nodeId, String name, String role, String status, String message)`
  - `record ExecutionPrecheckService.PrecheckReport(boolean valid, List<String> errors, List<String> warnings, Integer threads, Integer durationSeconds, Integer workerCount, Integer monitorTargetCount, Long queueAhead, List<PrecheckNode> nodes)`
  - `precheck(long scenarioId, Long threadGroupConfigId, Integer threadGroupPresetSortOrder) -> PrecheckReport`
  - 规则：scenario/plan 不存在 → `ExecutionValidationException("... does not exist")`；merge 失败 → errors 收录并提前返回；controller 缺失/节点不存在 → errors；节点 OFFLINE → warnings；threads<=0 / duration<=0 → warnings；`queueAhead` = QUEUED+RUNNING 执行数（>0 时加 warning）；workerNodeIds 为空时 workerCount 按 controller 计 1 且不重复列节点

- [x] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.distributed.ExecutionNodeRole;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRecord;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:execution-precheck-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionPrecheckServiceTest {
    @Autowired
    private ExecutionPrecheckService precheckService;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentExecutionNodeRepository nodeRepository;

    private long scenarioId;

    @BeforeEach
    void setUp() {
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        scenarioId = scenarioRepository.save(new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0)).getId();
    }

    @Test
    void missingScenarioThrowsNotFound() {
        assertThatThrownBy(() -> precheckService.precheck(999999L, null, null))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("scenario does not exist");
    }

    @Test
    void scenarioWithoutControllerNodeIsInvalid() {
        ExecutionPrecheckService.PrecheckReport report = precheckService.precheck(scenarioId, null, null);

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).contains("controller node is required");
        assertThat(report.warnings()).contains("threads is not configured", "duration is not configured");
        assertThat(report.threads()).isEqualTo(0);
        assertThat(report.workerCount()).isEqualTo(0);
        assertThat(report.queueAhead()).isEqualTo(0L);
    }

    @Test
    void configuredScenarioReportsNodeAndImpact() {
        PersistentExecutionNodeRecord node = nodeRepository.save(new PersistentExecutionNodeRecord(
                "node-1", "10.0.0.1", 22, "ops", "/keys/id", ExecutionNodeRole.CONTROLLER, "/tmp/perf"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId).orElseThrow();
        scenario.updateProfile("scenario-a", 1L, "{}", node.getId(), null, null, null);
        scenarioRepository.save(scenario);

        ExecutionPrecheckService.PrecheckReport report = precheckService.precheck(scenarioId, null, null);

        assertThat(report.valid()).isTrue();
        assertThat(report.errors()).isEmpty();
        assertThat(report.workerCount()).isEqualTo(1);
        assertThat(report.nodes()).hasSize(1);
        assertThat(report.nodes().get(0).nodeId()).isEqualTo(node.getId());
        assertThat(report.nodes().get(0).role()).isEqualTo("CONTROLLER");
        assertThat(report.nodes().get(0).status()).isEqualTo("UNKNOWN");
    }

    @Test
    void missingWorkerNodeIsAnError() {
        PersistentExecutionNodeRecord node = nodeRepository.save(new PersistentExecutionNodeRecord(
                "node-1", "10.0.0.1", 22, "ops", "/keys/id", ExecutionNodeRole.CONTROLLER, "/tmp/perf"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId).orElseThrow();
        scenario.updateProfile("scenario-a", 1L, "{}", node.getId(), "[999]", null, null);
        scenarioRepository.save(scenario);

        ExecutionPrecheckService.PrecheckReport report = precheckService.precheck(scenarioId, null, null);

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).anyMatch(message -> message.contains("worker node 999 does not exist"));
        assertThat(report.nodes()).hasSize(2);
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.task.ExecutionPrecheckServiceTest"`
Expected: 编译失败（`ExecutionPrecheckService`、`countByStatusIn` 不存在）

- [x] **Step 3: 实现**

`PersistentScenarioExecutionRepository.java` 追加：

```java
    long countByStatusIn(java.util.List<com.yr.perftest.platform.execution.ExecutionStatus> statuses);
```

`ExecutionPrecheckService.java`：

```java
package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.distributed.ExecutionNodeStatus;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRecord;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExecutionPrecheckService {
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentExecutionNodeRepository nodeRepository;
    private final ExecutionConfigMerger configMerger;

    public ExecutionPrecheckService(
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentTaskPlanRepository planRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentExecutionNodeRepository nodeRepository,
            ExecutionConfigMerger configMerger
    ) {
        this.scenarioRepository = scenarioRepository;
        this.planRepository = planRepository;
        this.executionRepository = executionRepository;
        this.nodeRepository = nodeRepository;
        this.configMerger = configMerger;
    }

    @Transactional(readOnly = true)
    public PrecheckReport precheck(long scenarioId, Long threadGroupConfigId, Integer threadGroupPresetSortOrder) {
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ExecutionValidationException("scenario does not exist"));
        PersistentTaskPlanRecord plan = planRepository.findById(scenario.getPlanId())
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));

        ExecutionConfig config;
        try {
            config = configMerger.merge(plan, scenario, threadGroupConfigId, threadGroupPresetSortOrder);
        } catch (ExecutionValidationException exception) {
            return new PrecheckReport(
                    false,
                    List.of(exception.getMessage()),
                    List.of(),
                    null, null, null, null, null,
                    List.of()
            );
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (config.threads() <= 0) {
            warnings.add("threads is not configured");
        }
        if (config.duration() <= 0) {
            warnings.add("duration is not configured");
        }

        List<PrecheckNode> nodes = new ArrayList<>();
        if (config.controllerNodeId() == null) {
            errors.add("controller node is required");
        } else {
            addNode(nodes, errors, warnings, config.controllerNodeId(), "CONTROLLER");
        }
        int workerCount;
        if (config.workerNodeIds().isEmpty()) {
            workerCount = config.controllerNodeId() == null ? 0 : 1;
        } else {
            workerCount = config.workerNodeIds().size();
            for (Long workerId : config.workerNodeIds()) {
                addNode(nodes, errors, warnings, workerId, "WORKER");
            }
        }

        long queueAhead = executionRepository.countByStatusIn(List.of(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING));
        if (queueAhead > 0) {
            warnings.add("platform has " + queueAhead + " queued or running executions");
        }
        return new PrecheckReport(
                errors.isEmpty(),
                List.copyOf(errors),
                List.copyOf(warnings),
                config.threads(),
                config.duration(),
                workerCount,
                config.monitorTargetIds().size(),
                queueAhead,
                List.copyOf(nodes)
        );
    }

    private void addNode(List<PrecheckNode> nodes, List<String> errors, List<String> warnings, long nodeId, String role) {
        PersistentExecutionNodeRecord node = nodeRepository.findById(nodeId).orElse(null);
        if (node == null) {
            errors.add(role.toLowerCase() + " node " + nodeId + " does not exist");
            nodes.add(new PrecheckNode(nodeId, null, role, "MISSING", null));
            return;
        }
        if (node.getStatus() == ExecutionNodeStatus.OFFLINE) {
            warnings.add("node " + nodeId + " is OFFLINE");
        }
        nodes.add(new PrecheckNode(nodeId, node.getName(), role, node.getStatus().name(), node.getLastMessage()));
    }

    public record PrecheckNode(long nodeId, String name, String role, String status, String message) {
    }

    public record PrecheckReport(
            boolean valid,
            List<String> errors,
            List<String> warnings,
            Integer threads,
            Integer durationSeconds,
            Integer workerCount,
            Integer monitorTargetCount,
            Long queueAhead,
            List<PrecheckNode> nodes
    ) {
    }
}
```

- [x] **Step 4: 跑测试确认通过**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.task.ExecutionPrecheckServiceTest"`
Expected: PASS（4 个用例）

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task/PersistentScenarioExecutionRepository.java backend/src/main/java/com/yr/perftest/platform/task/ExecutionPrecheckService.java backend/src/test/java/com/yr/perftest/platform/task/ExecutionPrecheckServiceTest.java
git commit -m "feat：新增执行预检与影响评估（T8）"
```

---

### Task 10: ExecutionFacade + agent 面执行工具入口 + 409 错误映射

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/facade/data/ExecutionStartResult.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/facade/data/ExecutionStatusView.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/facade/data/ExecutionPrecheckView.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/facade/ExecutionFacade.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/agent/execution/AgentExecutionControlController.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/agent/AgentExceptionHandler.java`（+`IdempotencyConflictException`、`ExecutionConflictException` → 409）
- Test: `backend/src/test/java/com/yr/perftest/platform/agent/AgentExecutionControlApiTest.java`

**Interfaces:**
- Consumes:
  - `ExecutionControlService`（Task 8）：`StartCommand`、`StartOutcome`、`start/stop/cancel/status`
  - `ExecutionPrecheckService`（Task 9）：`precheck(long, Long, Integer) -> PrecheckReport`、`PrecheckReport`、`PrecheckNode`
  - `ScenarioExecution`（既有 record）：`id()`、`status()`、`createdAt()`、`startedAt()`、`endedAt()`、`durationMs()`、`errorMessage()`
  - `IdempotencyConflictException`、`ExecutionConflictException`（Task 7/8）
- Produces:
  - `record ExecutionStartResult(String schemaVersion, long executionId, String status, boolean replayed)`（`SCHEMA_VERSION="1"`）
  - `record ExecutionStatusView(String schemaVersion, long executionId, String status, Instant createdAt, Instant startedAt, Instant endedAt, Long durationMs, String errorMessage)`（`SCHEMA_VERSION="1"`）
  - `record ExecutionPrecheckView(String schemaVersion, boolean valid, List<String> errors, List<String> warnings, Integer threads, Integer durationSeconds, Integer workerCount, Integer monitorTargetCount, Long queueAhead, List<ExecutionPrecheckView.NodeView> nodes)`；`record NodeView(long nodeId, String name, String role, String status, String message)`（`SCHEMA_VERSION="1"`）
  - `ExecutionFacade.startExecution(long scenarioId, String executionName, Long threadGroupConfigId, Integer threadGroupPresetSortOrder, String idempotencyKey) -> ExecutionStartResult`、`stopExecution(long) -> ExecutionStatusView`、`cancelExecution(long) -> ExecutionStatusView`、`getExecutionStatus(long) -> ExecutionStatusView`、`precheckExecution(long scenarioId, Long threadGroupConfigId, Integer threadGroupPresetSortOrder) -> ExecutionPrecheckView`
  - REST：`POST /api/agent/scenarios/{scenarioId}/executions`（头 `Idempotency-Key`）、`POST /api/agent/scenarios/{scenarioId}/precheck`、`POST /api/agent/executions/{executionId}/stop`、`POST /api/agent/executions/{executionId}/cancel`、`GET /api/agent/executions/{executionId}/status`
  - 错误映射：幂等键不同参数 → 409 `IDEMPOTENCY_CONFLICT`；终态上 stop/cancel → 409 `EXECUTION_CONFLICT`

- [x] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-execution-control-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentExecutionControlApiTest {
    private static final String CONFIG_JSON = "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    private String apiKey;
    private long scenarioId;

    @BeforeEach
    void setUp() throws Exception {
        String adminToken = loginToken();
        MvcResult issued = mockMvc.perform(post("/api/agent-api-keys")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"ops\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        apiKey = objectMapper.readTree(issued.getResponse().getContentAsString()).get("plainKey").asText();

        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0));
        scenario.updateProfile("scenario-a", 1L, "{}", 1L, null, null, null);
        scenarioId = scenarioRepository.save(scenario).getId();
    }

    @Test
    void startWithIdempotencyKeyStartsOnlyOnce() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "start-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"run-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error", nullValue()))
                .andExpect(jsonPath("$.data.schemaVersion", is("1")))
                .andExpect(jsonPath("$.data.status", is("QUEUED")))
                .andExpect(jsonPath("$.data.replayed", is(false)))
                .andReturn();
        long executionId = objectMapper.readTree(first.getResponse().getContentAsString())
                .at("/data/executionId").asLong();

        mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "start-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"run-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionId", is((int) executionId)))
                .andExpect(jsonPath("$.data.replayed", is(true)));

        org.assertj.core.api.Assertions.assertThat(
                executionRepository.findAllByScenarioIdOrderByIdDesc(scenarioId)).hasSize(1);
    }

    @Test
    void sameKeyWithDifferentBodyConflicts() throws Exception {
        mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "start-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"run-1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "start-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"run-2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("IDEMPOTENCY_CONFLICT")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void cancelQueuedExecutionThenStatusIsCancelled() throws Exception {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        long executionId = execution.getId();

        mockMvc.perform(post("/api/agent/executions/" + executionId + "/cancel")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        mockMvc.perform(get("/api/agent/executions/" + executionId + "/status")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionId", is((int) executionId)))
                .andExpect(jsonPath("$.data.status", is("CANCELLED")))
                .andExpect(jsonPath("$.data.endedAt", notNullValue()));
    }

    @Test
    void stopAndCancelOnFinishedExecutionReturnConflict() throws Exception {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        long executionId = executionRepository.save(execution).getId();

        mockMvc.perform(post("/api/agent/executions/" + executionId + "/stop")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("EXECUTION_CONFLICT")));
        mockMvc.perform(post("/api/agent/executions/" + executionId + "/cancel")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("EXECUTION_CONFLICT")));
    }

    @Test
    void precheckReportsMissingNodeRecords() throws Exception {
        mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/precheck")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemaVersion", is("1")))
                .andExpect(jsonPath("$.data.valid", is(false)))
                .andExpect(jsonPath("$.data.errors[0]", is("controller node 1 does not exist")))
                .andExpect(jsonPath("$.data.nodes[0].status", is("MISSING")))
                .andExpect(jsonPath("$.data.queueAhead", is(0)));
    }

    @Test
    void returnsAuthenticationFailedWithoutIdentity() throws Exception {
        mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("AUTHENTICATION_FAILED")));
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.agent.AgentExecutionControlApiTest"`
Expected: 404 / 编译失败

- [x] **Step 3: 实现**

`facade/data/ExecutionStartResult.java`：

```java
package com.yr.perftest.platform.facade.data;

public record ExecutionStartResult(
        String schemaVersion,
        long executionId,
        String status,
        boolean replayed
) {
    public static final String SCHEMA_VERSION = "1";
}
```

`facade/data/ExecutionStatusView.java`：

```java
package com.yr.perftest.platform.facade.data;

import java.time.Instant;

public record ExecutionStatusView(
        String schemaVersion,
        long executionId,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        String errorMessage
) {
    public static final String SCHEMA_VERSION = "1";
}
```

`facade/data/ExecutionPrecheckView.java`：

```java
package com.yr.perftest.platform.facade.data;

import java.util.List;

public record ExecutionPrecheckView(
        String schemaVersion,
        boolean valid,
        List<String> errors,
        List<String> warnings,
        Integer threads,
        Integer durationSeconds,
        Integer workerCount,
        Integer monitorTargetCount,
        Long queueAhead,
        List<NodeView> nodes
) {
    public static final String SCHEMA_VERSION = "1";

    public record NodeView(long nodeId, String name, String role, String status, String message) {
    }
}
```

`facade/ExecutionFacade.java`：

```java
package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.facade.data.ExecutionPrecheckView;
import com.yr.perftest.platform.facade.data.ExecutionStartResult;
import com.yr.perftest.platform.facade.data.ExecutionStatusView;
import com.yr.perftest.platform.task.ExecutionControlService;
import com.yr.perftest.platform.task.ExecutionPrecheckService;
import com.yr.perftest.platform.task.ScenarioExecution;
import org.springframework.stereotype.Service;

@Service
public class ExecutionFacade {
    private final FacadeGuard guard;
    private final ExecutionControlService controlService;
    private final ExecutionPrecheckService precheckService;

    public ExecutionFacade(
            FacadeGuard guard,
            ExecutionControlService controlService,
            ExecutionPrecheckService precheckService
    ) {
        this.guard = guard;
        this.controlService = controlService;
        this.precheckService = precheckService;
    }

    public ExecutionStartResult startExecution(
            long scenarioId,
            String executionName,
            Long threadGroupConfigId,
            Integer threadGroupPresetSortOrder,
            String idempotencyKey
    ) {
        return guard.requirePrincipal(() -> {
            ExecutionControlService.StartOutcome outcome = controlService.start(
                    new ExecutionControlService.StartCommand(
                            scenarioId, executionName, threadGroupConfigId, threadGroupPresetSortOrder),
                    idempotencyKey
            );
            return new ExecutionStartResult(
                    ExecutionStartResult.SCHEMA_VERSION,
                    outcome.executionId(),
                    outcome.status().name(),
                    outcome.replayed()
            );
        });
    }

    public ExecutionStatusView stopExecution(long executionId) {
        return guard.requirePrincipal(() -> {
            controlService.stop(executionId);
            return statusView(controlService.status(executionId));
        });
    }

    public ExecutionStatusView cancelExecution(long executionId) {
        return guard.requirePrincipal(() -> {
            controlService.cancel(executionId);
            return statusView(controlService.status(executionId));
        });
    }

    public ExecutionStatusView getExecutionStatus(long executionId) {
        return guard.requirePrincipal(() -> statusView(controlService.status(executionId)));
    }

    public ExecutionPrecheckView precheckExecution(
            long scenarioId,
            Long threadGroupConfigId,
            Integer threadGroupPresetSortOrder
    ) {
        return guard.requirePrincipal(() -> {
            ExecutionPrecheckService.PrecheckReport report =
                    precheckService.precheck(scenarioId, threadGroupConfigId, threadGroupPresetSortOrder);
            return new ExecutionPrecheckView(
                    ExecutionPrecheckView.SCHEMA_VERSION,
                    report.valid(),
                    report.errors(),
                    report.warnings(),
                    report.threads(),
                    report.durationSeconds(),
                    report.workerCount(),
                    report.monitorTargetCount(),
                    report.queueAhead(),
                    report.nodes().stream()
                            .map(node -> new ExecutionPrecheckView.NodeView(
                                    node.nodeId(), node.name(), node.role(), node.status(), node.message()))
                            .toList()
            );
        });
    }

    private ExecutionStatusView statusView(ScenarioExecution execution) {
        return new ExecutionStatusView(
                ExecutionStatusView.SCHEMA_VERSION,
                execution.id(),
                execution.status().name(),
                execution.createdAt(),
                execution.startedAt(),
                execution.endedAt(),
                execution.durationMs(),
                execution.errorMessage()
        );
    }
}
```

`agent/execution/AgentExecutionControlController.java`：

```java
package com.yr.perftest.platform.agent.execution;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.facade.ExecutionFacade;
import com.yr.perftest.platform.facade.data.ExecutionPrecheckView;
import com.yr.perftest.platform.facade.data.ExecutionStartResult;
import com.yr.perftest.platform.facade.data.ExecutionStatusView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
public class AgentExecutionControlController {
    private final ExecutionFacade executionFacade;

    public AgentExecutionControlController(ExecutionFacade executionFacade) {
        this.executionFacade = executionFacade;
    }

    @PostMapping("/scenarios/{scenarioId}/executions")
    public ApiResponse<ExecutionStartResult> start(
            @PathVariable long scenarioId,
            @RequestBody(required = false) StartExecutionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        ExecutionStartResult result = executionFacade.startExecution(
                scenarioId,
                request == null ? null : request.executionName(),
                request == null ? null : request.threadGroupConfigId(),
                request == null ? null : request.threadGroupPresetSortOrder(),
                idempotencyKey
        );
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, result);
    }

    @PostMapping("/scenarios/{scenarioId}/precheck")
    public ApiResponse<ExecutionPrecheckView> precheck(
            @PathVariable long scenarioId,
            @RequestBody(required = false) PrecheckRequest request
    ) {
        ExecutionPrecheckView view = executionFacade.precheckExecution(
                scenarioId,
                request == null ? null : request.threadGroupConfigId(),
                request == null ? null : request.threadGroupPresetSortOrder()
        );
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, view);
    }

    @PostMapping("/executions/{executionId}/stop")
    public ApiResponse<ExecutionStatusView> stop(@PathVariable long executionId) {
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                executionFacade.stopExecution(executionId)
        );
    }

    @PostMapping("/executions/{executionId}/cancel")
    public ApiResponse<ExecutionStatusView> cancel(@PathVariable long executionId) {
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                executionFacade.cancelExecution(executionId)
        );
    }

    @GetMapping("/executions/{executionId}/status")
    public ApiResponse<ExecutionStatusView> status(@PathVariable long executionId) {
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                executionFacade.getExecutionStatus(executionId)
        );
    }

    public record StartExecutionRequest(
            String executionName,
            Long threadGroupConfigId,
            Integer threadGroupPresetSortOrder
    ) {
    }

    public record PrecheckRequest(Long threadGroupConfigId, Integer threadGroupPresetSortOrder) {
    }
}
```

`AgentExceptionHandler.java` 追加两个 handler（imports 加 `com.yr.perftest.platform.execution.ExecutionConflictException` 与 `com.yr.perftest.platform.execution.IdempotencyConflictException`）：

```java
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(IdempotencyConflictException exception) {
        return envelope(HttpStatus.CONFLICT, AgentErrorCode.IDEMPOTENCY_CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ExecutionConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleExecutionConflict(ExecutionConflictException exception) {
        return envelope(HttpStatus.CONFLICT, AgentErrorCode.EXECUTION_CONFLICT, exception.getMessage());
    }
```

- [x] **Step 4: 跑测试确认通过**

Run: `./gradlew :backend:test --tests "com.yr.perftest.platform.agent.AgentExecutionControlApiTest"`
Expected: PASS（6 个用例）

- [x] **Step 5: 全量回归**

Run: `./gradlew :backend:test`
Expected: 全部 PASS（含 `AgentLayeringConstraintTest`、`AgentOpenApiTest` 等既有用例）

- [x] **Step 6: 更新任务清单勾选**

修改 `docs/agent-platform-buildout-tasks.md`：T7、T8 的清单项 `[ ]` 改为 `[x]`，任务索引表中 T7/T8 状态改为「✅ 完成」。

- [x] **Step 7: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/facade backend/src/main/java/com/yr/perftest/platform/agent backend/src/test/java/com/yr/perftest/platform/agent/AgentExecutionControlApiTest.java docs/agent-platform-buildout-tasks.md
git commit -m "feat：打通 agent 面压测执行工具化入口（T8）"
```
