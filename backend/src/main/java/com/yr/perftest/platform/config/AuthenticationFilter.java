package com.yr.perftest.platform.config;

import com.yr.perftest.platform.identity.AgentApiKeyService;
import com.yr.perftest.platform.identity.AuthTokenService;
import com.yr.perftest.platform.identity.Principal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class AuthenticationFilter extends OncePerRequestFilter {
    public static final String API_KEY_HEADER = "X-API-Key";

    private final AuthTokenService authTokenService;
    private final AgentApiKeyService agentApiKeyService;

    public AuthenticationFilter(AuthTokenService authTokenService, AgentApiKeyService agentApiKeyService) {
        this.authTokenService = authTokenService;
        this.agentApiKeyService = agentApiKeyService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<Principal> principal = resolvePrincipal(request);
        principal.ifPresent(value -> {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    value,
                    null,
                    AuthorityUtils.NO_AUTHORITIES
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<Principal> resolvePrincipal(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            return authTokenService.resolve(token).map(Principal.class::cast);
        }
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return agentApiKeyService.resolve(apiKey.trim()).map(Principal.class::cast);
        }
        return Optional.empty();
    }
}
