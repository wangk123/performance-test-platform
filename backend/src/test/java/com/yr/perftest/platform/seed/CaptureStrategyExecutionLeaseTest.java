package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(DatasourceCaptureLeaseService.class)
class CaptureStrategyExecutionLeaseTest {
    @Autowired
    private PersistentSeedDatasourceRepository datasourceRepository;

    @Autowired
    private PersistentSeedCaptureStrategyRepository strategyRepository;

    @Autowired
    private PersistentSeedCaptureSampleRepository sampleRepository;

    @Autowired
    private DatasourceCaptureLeaseService leaseService;

    @Test
    void rejectsSecondExecutionBeforeCreatingAnotherSample() {
        PersistentSeedDatasourceRecord datasource = datasourceRepository.saveAndFlush(
                new PersistentSeedDatasourceRecord(1L, "fake", "fake", 3306, "shop", "u", "p")
        );
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.saveAndFlush(
                new PersistentSeedCaptureStrategyRecord(
                        1L,
                        "capture",
                        datasource.getId(),
                        "[\"shop.orders\"]",
                        "[]",
                        1,
                        100
                )
        );
        SeedCaptureStrategyService service = new SeedCaptureStrategyService(
                datasourceRepository,
                strategyRepository,
                sampleRepository,
                leaseService,
                null
        );

        MapView first = new MapView(service.execute(1L, strategy.getId()));

        assertThat(first.value("status")).isEqualTo("QUEUED");
        assertThatThrownBy(() -> service.execute(1L, strategy.getId()))
                .isInstanceOf(DatasourceCaptureLeaseService.ActiveCaptureException.class)
                .hasMessageContaining(String.valueOf(first.value("id")));
        assertThat(sampleRepository.findByStrategyIdOrderBySampleSeqAsc(strategy.getId()))
                .hasSize(1);
        assertThat(leaseService.findActiveSampleId(datasource.getId()))
                .contains((Long) first.value("id"));
    }

    private record MapView(java.util.Map<String, Object> values) {
        private Object value(String key) {
            return values.get(key);
        }
    }
}
