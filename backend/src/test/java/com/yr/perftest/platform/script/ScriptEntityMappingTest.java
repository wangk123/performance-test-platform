package com.yr.perftest.platform.script;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:script-entity-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "platform.storage.root=./build/test-storage/script-entity"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ScriptEntityMappingTest {
    @Autowired
    private PersistentScriptRepository scriptRepository;
    @Autowired
    private PersistentScriptVersionRepository versionRepository;

    @Test
    void persistsScriptAndDraftVersion() {
        PersistentScriptRecord script = scriptRepository.save(
                PersistentScriptRecord.persistentOf(1L, "登录链路压测", 0, "admin", Instant.now()));
        script.bumpLatestVersionNo(1);
        scriptRepository.saveAndFlush(script);

        PersistentScriptVersionRecord version = versionRepository.save(new PersistentScriptVersionRecord(
                script.getPersistentId(), script.getProjectId(), 0, "登录链路压测.jmx",
                "./build/test-storage/script-entity/draft.jmx", "admin", Instant.now(),
                ScriptVersionStatus.DRAFT, null));
        version.markPublished(1, "首发", "admin", Instant.now());
        versionRepository.saveAndFlush(version);

        assertThat(scriptRepository.findByIdAndProjectId(script.getPersistentId(), 1L)).isPresent();
        assertThat(scriptRepository.findByIdAndProjectId(script.getPersistentId(), 1L).orElseThrow()
                .getLatestVersionNo()).isEqualTo(1);
        assertThat(versionRepository.findById(version.getId()).orElseThrow().getStatus())
                .isEqualTo(ScriptVersionStatus.PUBLISHED);
        assertThat(versionRepository.findById(version.getId()).orElseThrow().getRemark()).isEqualTo("首发");
    }

    @Test
    void bumpWatermarkNeverDecreases() {
        PersistentScriptRecord script = scriptRepository.save(
                PersistentScriptRecord.persistentOf(1L, "下单链路", 3, "admin", Instant.now()));
        script.bumpLatestVersionNo(2);
        assertThat(script.getLatestVersionNo()).isEqualTo(3);
        script.bumpLatestVersionNo(5);
        assertThat(script.getLatestVersionNo()).isEqualTo(5);
    }
}
