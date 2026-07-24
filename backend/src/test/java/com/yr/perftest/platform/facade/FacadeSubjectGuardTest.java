package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.identity.AuthenticationException;
import com.yr.perftest.platform.identity.MachinePrincipal;
import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.task.ScenarioExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FacadeSubjectGuardTest {
    @Mock
    private ScenarioExecutionService scenarioExecutionService;

    @Test
    void missingPrincipalBlocksBusinessCall() {
        SecurityContextHolder.clearContext();
        FacadeGuard guard = new FacadeGuard();
        DataFacade facade = new DataFacade(guard, scenarioExecutionService);
        AtomicBoolean reached = new AtomicBoolean(false);

        assertThatThrownBy(() -> guard.requirePrincipal(() -> {
            reached.set(true);
            return null;
        }))
                .isInstanceOf(AuthenticationException.class);
        assertThat(reached).isFalse();

        assertThatThrownBy(() -> facade.getExecutionSummary(1L))
                .isInstanceOf(AuthenticationException.class);
        verifyNoInteractions(scenarioExecutionService);
    }

    @Test
    void validMachinePrincipalAllowsCall() {
        MachinePrincipal principal = new MachinePrincipal(7L, "ops");
        setPrincipal(principal);
        try {
            FacadeGuard guard = new FacadeGuard();
            AtomicBoolean reached = new AtomicBoolean(false);

            Principal resolved = guard.requirePrincipal(() -> {
                reached.set(true);
                return (Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            });

            assertThat(reached).isTrue();
            assertThat(resolved).isEqualTo(principal);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void setPrincipal(Principal principal) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                AuthorityUtils.NO_AUTHORITIES
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
