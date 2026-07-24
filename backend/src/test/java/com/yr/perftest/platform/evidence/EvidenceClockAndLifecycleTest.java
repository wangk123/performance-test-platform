package com.yr.perftest.platform.evidence;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.PageBudget;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceClockAndLifecycleTest {
    @Test
    void warnsAboutPrometheusClockSkewWithoutChangingTimestamps() {
        CorrelationKey key = key(42L);
        Availability prometheusWindow = new Availability(
                true,
                key.from().plusSeconds(31),
                key.to().plusSeconds(31),
                "15s",
                false,
                "prometheus:SERVER_CPU?step=15",
                null
        );
        EvidenceSummary sourceSummary = new EvidenceSummary(
                key,
                "prometheus",
                prometheusWindow,
                Map.of("pointCount", 1),
                prometheusWindow.sourceRef(),
                "prometheus"
        );
        EvidenceService service = new EvidenceService(List.of(source(sourceSummary)));

        EvidenceSummary result = service.collect(key, PageBudget.defaults()).get(0);

        assertThat(result.summary())
                .containsEntry("warnings", List.of("clock:skew-suspected"));
        assertThat(result.key().from()).isEqualTo(key.from());
        assertThat(result.key().to()).isEqualTo(key.to());
        assertThat(result.availability().from()).isEqualTo(prometheusWindow.from());
        assertThat(result.availability().to()).isEqualTo(prometheusWindow.to());
        assertThat(result.sourceClock()).isEqualTo("prometheus");
    }

    @Test
    void keepsDeclaredSourceClockForEverySummary() {
        CorrelationKey key = key(42L);
        EvidenceService service = new EvidenceService(List.of(
                source(summary(key, "aggregate", "load")),
                source(summary(key, "target-metric", "target")),
                source(summary(key, "prometheus", "prometheus"))
        ));

        assertThat(service.collect(key, PageBudget.defaults()))
                .extracting(EvidenceSummary::sourceClock)
                .containsExactly("load", "target", "prometheus");
    }

    @Test
    void marksMissingExecutionEvidenceAsDeleted() {
        DataFacade facade = mock(DataFacade.class);
        when(facade.getExecutionSummary(99L))
                .thenThrow(new ExecutionValidationException("execution does not exist"));

        EvidenceSummary result = new ExecutionEvidenceSource(facade)
                .summarize(key(99L), PageBudget.defaults());

        assertThat(result.availability().present()).isFalse();
        assertThat(result.availability().missingReason()).isEqualTo(Availability.MissingReason.DELETED);
        assertThat(result.summary()).isEmpty();
    }

    private static CorrelationKey key(long executionId) {
        return new CorrelationKey(
                executionId,
                Instant.parse("2026-07-24T01:00:00Z"),
                Instant.parse("2026-07-24T01:05:00Z"),
                List.of(),
                null,
                null
        );
    }

    private static EvidenceSummary summary(CorrelationKey key, String sourceType, String sourceClock) {
        Availability availability = new Availability(
                true,
                key.from(),
                key.to(),
                "1s",
                false,
                sourceType,
                null
        );
        return new EvidenceSummary(
                key,
                sourceType,
                availability,
                Map.of("count", 1),
                sourceType,
                sourceClock
        );
    }

    private static EvidenceSource source(EvidenceSummary summary) {
        return new EvidenceSource() {
            @Override
            public boolean supports(CorrelationKey key) {
                return true;
            }

            @Override
            public EvidenceSummary summarize(CorrelationKey key, PageBudget budget) {
                return summary;
            }
        };
    }
}
