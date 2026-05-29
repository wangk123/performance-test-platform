package com.yr.perftest.platform.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class IdentityPersistenceTest {
    @Autowired
    private PersistentUserAccountRepository userAccountRepository;

    @Test
    void persistsUserAccountForAuthentication() {
        userAccountRepository.save(new PersistentUserAccountRecord(
                "admin",
                "admin123",
                "平台管理员",
                true,
                SystemRole.ADMIN
        ));

        Optional<PersistentUserAccountRecord> loadedAccount = userAccountRepository.findByUsername("admin");

        assertThat(loadedAccount).isPresent();
        assertThat(loadedAccount.get().passwordMatches("admin123")).isTrue();
        assertThat(loadedAccount.get().toAuthenticatedUser().getRoles()).contains(SystemRole.ADMIN);
    }
}
