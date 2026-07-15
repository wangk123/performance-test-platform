package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(DatasourceCaptureLeaseService.class)
class DatasourceCaptureLeaseServiceTest {
    @Autowired
    private DatasourceCaptureLeaseService leaseService;

    @Autowired
    private PersistentSeedCaptureSampleRepository sampleRepository;

    @Test
    void acquiresOnlyOneLeasePerDatasourceAndReportsTheActiveSample() {
        DatasourceCaptureLease lease = leaseService.acquire(11L, 101L);

        assertThat(lease.sampleId()).isEqualTo(101L);
        assertThat(leaseService.findActiveSampleId(11L)).contains(101L);
        assertThatThrownBy(() -> leaseService.acquire(11L, 202L))
                .isInstanceOf(DatasourceCaptureLeaseService.ActiveCaptureException.class)
                .hasMessageContaining("101");
    }

    @Test
    void releasesLeaseSoAnotherSampleCanAcquireIt() {
        leaseService.acquire(11L, 101L);

        leaseService.release(11L, 101L);

        assertThat(leaseService.findActiveSampleId(11L)).isEmpty();
        assertThat(leaseService.acquire(11L, 202L).sampleId()).isEqualTo(202L);
    }

    @Test
    void discoversAnActiveSampleEvenBeforeItsLeaseIsCreated() {
        PersistentSeedCaptureSampleRecord sample = sampleRepository.saveAndFlush(
                new PersistentSeedCaptureSampleRecord(
                        1L,
                        2L,
                        11L,
                        1,
                        "CAPTURING",
                        Instant.now(),
                        null,
                        "{}",
                        1
                )
        );

        assertThat(leaseService.findActiveSample(11L)).contains(sample);
    }
}
