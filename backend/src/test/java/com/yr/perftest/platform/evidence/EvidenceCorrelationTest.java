package com.yr.perftest.platform.evidence;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.data.PrometheusMetricPoint;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.PageBudget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceCorrelationTest {
    @Test
    void collectsOnlyBoundSourcesWithTheSameCorrelationKey() {
        CorrelationKey key = new CorrelationKey(
                42L,
                Instant.parse("2026-07-24T01:00:00Z"),
                Instant.parse("2026-07-24T01:05:00Z"),
                List.of("app-1"),
                "checkout",
                "trace-1"
        );
        AtomicInteger unsupportedCalls = new AtomicInteger();
        EvidenceSource unsupported = new StubSource("unbound") {
            @Override
            public boolean supports(CorrelationKey ignored) {
                return false;
            }

            @Override
            public EvidenceSummary summarize(CorrelationKey ignored, PageBudget budget) {
                unsupportedCalls.incrementAndGet();
                return super.summarize(ignored, budget);
            }
        };
        EvidenceService service = new EvidenceService(List.of(
                new StubSource("aggregate"),
                new StubSource("series"),
                new StubSource("prometheus"),
                unsupported
        ));

        List<EvidenceSummary> summaries = service.collect(key, PageBudget.defaults());

        assertThat(summaries)
                .extracting(EvidenceSummary::sourceType)
                .containsExactly("aggregate", "series", "prometheus");
        assertThat(summaries).allSatisfy(summary -> {
            assertThat(summary.key()).isEqualTo(key);
            assertThat(summary.key().targetInstances()).containsExactly("app-1");
            assertThat(summary.summary()).containsOnlyKeys("count");
            assertThat(summary.sourceRef()).startsWith("stub:");
        });
        assertThat(unsupportedCalls).hasValue(0);
    }

    @Test
    void agentControllersDoNotExposeEvidenceSummary() throws IOException {
        Path root = Path.of("src/main/java/com/yr/perftest/platform/agent");

        try (Stream<Path> paths = Files.walk(root)) {
            assertThat(paths.filter(path -> path.toString().endsWith("Controller.java")))
                    .allSatisfy(path -> {
                        try {
                            assertThat(Files.readString(path)).doesNotContain("EvidenceSummary");
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        }
    }

    @Test
    void facadeAdaptersKeepOnlyRequestedLabelAndTargetInstance() {
        DataFacade facade = mock(DataFacade.class);
        PageBudget budget = PageBudget.defaults();
        CorrelationKey key = new CorrelationKey(
                42L,
                Instant.parse("2026-07-24T01:00:00Z"),
                Instant.parse("2026-07-24T01:05:00Z"),
                List.of("app-1"),
                "checkout",
                null
        );
        CorrelationKey aggregateKey = new CorrelationKey(
                key.executionId(),
                key.from(),
                key.to(),
                List.of(),
                key.requestLabel(),
                key.traceId()
        );
        Availability availability = new Availability(
                true,
                key.from(),
                key.to(),
                "15s",
                false,
                "source",
                null
        );
        TaskExecutionResult.AggregateRow checkout = aggregateRow("checkout", 10);
        TaskExecutionResult.AggregateRow search = aggregateRow("search", 20);
        when(facade.queryAggregateRows(42L, null, budget)).thenReturn(
                new BoundedPage<>(List.of(checkout, search), false, null, List.of(), availability)
        );
        PrometheusMetricPoint appOne = new PrometheusMetricPoint(
                "cpu",
                Map.of("instance", "app-1"),
                key.from().getEpochSecond(),
                0.5,
                0
        );
        PrometheusMetricPoint appTwo = new PrometheusMetricPoint(
                "cpu",
                Map.of("instance", "app-2"),
                key.from().getEpochSecond(),
                0.6,
                0
        );
        when(facade.queryPrometheus(42L, "SERVER_CPU", key.from(), key.to(), 15, null, budget))
                .thenReturn(new BoundedPage<>(List.of(appOne, appTwo), false, null, List.of(), availability));

        AggregateEvidenceSource aggregateSource = new AggregateEvidenceSource(facade);
        EvidenceSummary aggregate = aggregateSource.summarize(aggregateKey, budget);
        EvidenceSummary prometheus = new PrometheusEvidenceSource(facade, "SERVER_CPU", 15)
                .summarize(key, budget);

        assertThat(aggregateSource.supports(key)).isFalse();
        assertThat(aggregate.summary()).containsEntry("rowCount", 1);
        assertThat(aggregate.summary().toString()).doesNotContain("search");
        assertThat(aggregate.sourceClock()).isEqualTo("load");
        assertThat(prometheus.summary()).containsEntry("pointCount", 1);
        assertThat(prometheus.summary().toString()).doesNotContain("app-2");
        assertThat(prometheus.sourceClock()).isEqualTo("prometheus");

        CorrelationKey missingLabel = new CorrelationKey(
                key.executionId(),
                key.from(),
                key.to(),
                List.of(),
                "missing",
                null
        );
        CorrelationKey missingTarget = new CorrelationKey(
                key.executionId(),
                key.from(),
                key.to(),
                List.of("app-3"),
                null,
                null
        );
        assertThat(aggregateSource.summarize(missingLabel, budget).availability().present()).isFalse();
        assertThat(new PrometheusEvidenceSource(facade, "SERVER_CPU", 15)
                .summarize(missingTarget, budget)
                .availability()
                .present()).isFalse();
    }

    private static TaskExecutionResult.AggregateRow aggregateRow(String label, int samples) {
        return new TaskExecutionResult.AggregateRow(
                label,
                "thread",
                samples,
                10,
                10,
                10,
                10,
                10,
                10,
                10,
                0,
                1
        );
    }

    private static class StubSource implements EvidenceSource {
        private final String sourceType;

        private StubSource(String sourceType) {
            this.sourceType = sourceType;
        }

        @Override
        public boolean supports(CorrelationKey key) {
            return key.executionId() > 0 && key.from() != null && key.to() != null;
        }

        @Override
        public EvidenceSummary summarize(CorrelationKey key, PageBudget budget) {
            Availability availability = new Availability(
                    true,
                    key.from(),
                    key.to(),
                    "1s",
                    false,
                    "stub:" + sourceType,
                    null
            );
            return new EvidenceSummary(
                    key,
                    sourceType,
                    availability,
                    Map.of("count", 1),
                    availability.sourceRef(),
                    sourceType.equals("prometheus") ? "prometheus" : "load"
            );
        }
    }
}
