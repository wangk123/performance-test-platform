package com.yr.perftest.platform.task.method;

import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-evidence-image-repo-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
class PlanEvidenceImageRepositoryTest {

    @Autowired
    private PlanEvidenceImageRepository repository;

    @Test
    void savesAndQueriesByScenarioOrdering() {
        PersistentPlanEvidenceImageRecord a = image(1L, 1L, "甲", 1);
        PersistentPlanEvidenceImageRecord b = image(1L, 1L, "乙", 0);
        repository.saveAll(List.of(a, b));

        List<PersistentPlanEvidenceImageRecord> list = repository.findByScenarioIdOrderBySortOrderAscIdAsc(1L);
        assertThat(list).extracting(PersistentPlanEvidenceImageRecord::getCaption).containsExactly("乙", "甲");
        assertThat(list).allSatisfy(record -> {
            assertThat(record.getId()).isNotNull();
            assertThat(record.getPlanId()).isEqualTo(1L);
            assertThat(record.getScenarioId()).isEqualTo(1L);
            assertThat(record.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void executionRecordExposesMethodHiddenFlag() {
        PersistentScenarioExecutionRecord execution = new PersistentScenarioExecutionRecord(1L, "{}");
        assertThat(execution.isMethodHidden()).isFalse();
        execution.setMethodHidden(true);
        assertThat(execution.isMethodHidden()).isTrue();
    }

    @Test
    void deletesByExecutionIds() {
        PersistentPlanEvidenceImageRecord attached = image(2L, 2L, "a", 0);
        attached.setExecutionId(9L);
        PersistentPlanEvidenceImageRecord detached = image(2L, 2L, "b", 1);
        repository.saveAll(List.of(attached, detached));

        assertThat(repository.findByExecutionIdIn(List.of(9L)))
                .extracting(PersistentPlanEvidenceImageRecord::getCaption)
                .containsExactly("a");

        repository.deleteByExecutionIdIn(List.of(9L));

        assertThat(repository.findByExecutionIdIn(List.of(9L))).isEmpty();
        assertThat(repository.findByScenarioIdOrderBySortOrderAscIdAsc(2L))
                .extracting(PersistentPlanEvidenceImageRecord::getCaption)
                .containsExactly("b");
    }

    private PersistentPlanEvidenceImageRecord image(long planId, long scenarioId, String caption, int sortOrder) {
        return new PersistentPlanEvidenceImageRecord(planId, scenarioId, caption, sortOrder,
                "storage/evidence/" + caption + ".png", "image/png", 1024, "tester");
    }
}
