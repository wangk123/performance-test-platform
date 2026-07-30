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
