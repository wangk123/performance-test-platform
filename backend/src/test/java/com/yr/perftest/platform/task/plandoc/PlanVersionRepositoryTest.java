package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-version-repo-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
class PlanVersionRepositoryTest {

    @Autowired
    private PersistentPlanVersionRepository repository;

    @Test
    void saveAndQueryOrderByCreatedAtDesc() {
        repository.save(new PersistentPlanVersionRecord(1L, "V1.0", "首版", "owner", "owner",
                "正文一", "DRAFT", 1, Instant.parse("2026-09-10T01:00:00Z")));
        repository.save(new PersistentPlanVersionRecord(1L, "V1.1", "补充指标", "owner", "owner",
                "正文二", "DRAFT", 2, Instant.parse("2026-09-10T02:00:00Z")));

        List<PersistentPlanVersionRecord> versions = repository.findByPlanIdOrderByCreatedAtDescIdDesc(1L);
        assertThat(versions).extracting(PersistentPlanVersionRecord::getVersionNo)
                .containsExactly("V1.1", "V1.0");
        assertThat(repository.existsByPlanIdAndVersionNo(1L, "V1.0")).isTrue();
        assertThat(repository.existsByPlanIdAndVersionNo(1L, "V9.9")).isFalse();
    }

    @Test
    void duplicateVersionNoWithinPlanRejected() {
        repository.save(new PersistentPlanVersionRecord(2L, "V1.0", "首版", "owner", "owner",
                "正文", "DRAFT", 1, Instant.now()));
        assertThatThrownBy(() -> repository.saveAndFlush(new PersistentPlanVersionRecord(2L, "V1.0", "重号",
                "owner", "owner", "正文二", "DRAFT", 2, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
