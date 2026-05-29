package com.yr.perftest.platform.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProjectPersistenceTest {
    @Autowired
    private PersistentProjectRepository projectRepository;

    @Autowired
    private PersistentProjectMemberRepository memberRepository;

    @Test
    void persistsProjectAndOwnerMember() {
        PersistentProjectRecord project = new PersistentProjectRecord(
                "loan-core",
                "信贷核心压测",
                "授信和放款链路",
                "admin"
        );
        PersistentProjectRecord savedProject = projectRepository.save(project);
        memberRepository.save(new PersistentProjectMemberRecord(savedProject.getId(), "admin", ProjectRole.OWNER));

        Optional<PersistentProjectRecord> loadedProject = projectRepository.findByCode("loan-core");

        assertThat(loadedProject).isPresent();
        assertThat(loadedProject.get().getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(memberRepository.existsByProjectIdAndUsername(savedProject.getId(), "admin")).isTrue();
    }
}
