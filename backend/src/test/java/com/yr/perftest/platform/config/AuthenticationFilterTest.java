package com.yr.perftest.platform.config;

import com.yr.perftest.platform.identity.AgentApiKeyService;
import com.yr.perftest.platform.identity.AuthTokenService;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.MachinePrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationFilterTest {
    private final AuthTokenService authTokenService = mock(AuthTokenService.class);
    private final AgentApiKeyService agentApiKeyService = mock(AgentApiKeyService.class);
    private final AuthenticationFilter filter = new AuthenticationFilter(authTokenService, agentApiKeyService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bearerTokenResolvesHumanPrincipal() throws Exception {
        HumanPrincipal human = new HumanPrincipal("admin", EnumSet.of(SystemRole.ADMIN));
        when(authTokenService.resolve("tok-1")).thenReturn(Optional.of(human));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tok-1");
        AtomicReference<Authentication> captured = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capturingChain(captured));

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getPrincipal()).isEqualTo(human);
    }

    @Test
    void apiKeyHeaderResolvesMachinePrincipal() throws Exception {
        MachinePrincipal machine = new MachinePrincipal(7L, "ops");
        when(agentApiKeyService.resolve("pak_abc")).thenReturn(Optional.of(machine));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "pak_abc");
        AtomicReference<Authentication> captured = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capturingChain(captured));

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getPrincipal()).isEqualTo(machine);
    }

    @Test
    void missingCredentialsLeavesSecurityContextEmpty() throws Exception {
        AtomicReference<Authentication> captured = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), capturingChain(captured));

        assertThat(captured.get()).isNull();
    }

    private static FilterChain capturingChain(AtomicReference<Authentication> captured) {
        return (req, res) -> captured.set(SecurityContextHolder.getContext().getAuthentication());
    }
}
