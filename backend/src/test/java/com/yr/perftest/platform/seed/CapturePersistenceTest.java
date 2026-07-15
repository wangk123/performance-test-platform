package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CapturePersistenceTest {
    private static final Set<String> ACTIVE_SAMPLE_STATUSES = Set.of(
            "QUEUED",
            "PREPARING",
            "CAPTURING",
            "CANCEL_REQUESTED"
    );

    private static final Set<String> TERMINAL_SAMPLE_STATUSES = Set.of(
            "SUCCEEDED",
            "FAILED",
            "CANCELED",
            "INTERRUPTED",
            "DELETING"
    );

    @Autowired
    private PersistentSeedCaptureStrategyRepository strategyRepository;

    @Autowired
    private PersistentSeedCaptureSampleRepository sampleRepository;

    @Autowired
    private PersistentSeedCaptureAnalysisRepository analysisRepository;

    @Autowired
    private PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository;

    @Test
    void incrementsStrategyConfigVersionWhenConfigurationChanges() {
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.saveAndFlush(
                new PersistentSeedCaptureStrategyRecord(
                        1L,
                        "baseline",
                        11L,
                        "[\"orders\"]",
                        "[]",
                        2,
                        500
                )
        );

        assertThat(strategy.getConfigVersion()).isEqualTo(1);

        strategy.updateConfiguration(
                "baseline-v2",
                11L,
                "[\"orders\", \"users\"]",
                "[\"audit\"]",
                4,
                1000
        );
        strategyRepository.saveAndFlush(strategy);

        assertThat(strategyRepository.findById(strategy.getId()).orElseThrow().getConfigVersion())
                .isEqualTo(2);
    }

    @Test
    void returnsSampleSequencesInMonotonicOrderPerStrategy() {
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.saveAndFlush(
                new PersistentSeedCaptureStrategyRecord(
                        1L,
                        "baseline",
                        11L,
                        "[\"orders\"]",
                        "[]",
                        2,
                        500
                )
        );

        sampleRepository.saveAndFlush(sample(strategy, 2, "SUCCEEDED", 2));
        sampleRepository.saveAndFlush(sample(strategy, 1, "SUCCEEDED", 1));

        List<PersistentSeedCaptureSampleRecord> samples =
                sampleRepository.findByStrategyIdOrderBySampleSeqAsc(strategy.getId());

        assertThat(samples).extracting(PersistentSeedCaptureSampleRecord::getSampleSeq)
                .containsExactly(1, 2);
        assertThat(sampleRepository.findNextSampleSeq(strategy.getId())).isEqualTo(3);
    }

    @Test
    void findsOnlyTheActiveCaptureForADatasource() {
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.saveAndFlush(
                new PersistentSeedCaptureStrategyRecord(
                        1L,
                        "baseline",
                        11L,
                        "[\"orders\"]",
                        "[]",
                        2,
                        500
                )
        );

        PersistentSeedCaptureSampleRecord active = sample(strategy, 1, "CAPTURING", 1);
        PersistentSeedCaptureSampleRecord finished = sample(strategy, 2, "SUCCEEDED", 2);
        sampleRepository.saveAndFlush(active);
        sampleRepository.saveAndFlush(finished);

        assertThat(sampleRepository.findByDatasourceIdAndStatusInOrderByCaptureStartedAtDesc(
                11L,
                ACTIVE_SAMPLE_STATUSES
        )).containsExactly(active);
    }

    @Test
    void locksSamplesReferencedByAnAnalysis() {
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.saveAndFlush(
                new PersistentSeedCaptureStrategyRecord(
                        1L,
                        "baseline",
                        11L,
                        "[\"orders\"]",
                        "[]",
                        2,
                        500
                )
        );
        PersistentSeedCaptureSampleRecord sample = sampleRepository.saveAndFlush(
                sample(strategy, 1, "SUCCEEDED", 1)
        );
        PersistentSeedCaptureAnalysisRecord analysis = analysisRepository.saveAndFlush(
                new PersistentSeedCaptureAnalysisRecord(1L, strategy.getId(), "[%d]".formatted(sample.getId()))
        );

        inputLockRepository.saveAndFlush(
                new PersistentSeedCaptureAnalysisInputLockRecord(analysis.getId(), sample.getId())
        );

        assertThat(inputLockRepository.findBySampleId(sample.getId())).isPresent();
        assertThat(inputLockRepository.existsBySampleId(sample.getId())).isTrue();
    }

    @Test
    void filtersSamplesByTerminalState() {
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.saveAndFlush(
                new PersistentSeedCaptureStrategyRecord(
                        1L,
                        "baseline",
                        11L,
                        "[\"orders\"]",
                        "[]",
                        2,
                        500
                )
        );

        sampleRepository.saveAndFlush(sample(strategy, 1, "SUCCEEDED", 1));
        sampleRepository.saveAndFlush(sample(strategy, 2, "FAILED", 2));
        sampleRepository.saveAndFlush(sample(strategy, 3, "CANCELED", 3));
        sampleRepository.saveAndFlush(sample(strategy, 4, "INTERRUPTED", 4));
        sampleRepository.saveAndFlush(sample(strategy, 5, "DELETING", 5));
        sampleRepository.saveAndFlush(sample(strategy, 6, "CAPTURING", 6));

        List<PersistentSeedCaptureSampleRecord> terminalSamples =
                sampleRepository.findByStrategyIdAndStatusInOrderByCaptureStartedAtAscSampleSeqAsc(
                        strategy.getId(),
                        TERMINAL_SAMPLE_STATUSES
                );

        assertThat(terminalSamples).hasSize(5);
        assertThat(terminalSamples).extracting(PersistentSeedCaptureSampleRecord::getStatus)
                .containsExactly("SUCCEEDED", "FAILED", "CANCELED", "INTERRUPTED", "DELETING");
    }

    private static PersistentSeedCaptureSampleRecord sample(
            PersistentSeedCaptureStrategyRecord strategy,
            int sampleSeq,
            String status,
            int minute
    ) {
        Instant startedAt = Instant.parse("2026-07-15T06:0%d:00Z".formatted(minute));
        return new PersistentSeedCaptureSampleRecord(
                1L,
                strategy.getId(),
                11L,
                sampleSeq,
                status,
                startedAt,
                startedAt.plusSeconds(30),
                "{}",
                strategy.getConfigVersion()
        );
    }
}
