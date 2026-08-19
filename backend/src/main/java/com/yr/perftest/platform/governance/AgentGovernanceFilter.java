package com.yr.perftest.platform.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.AgentErrorCode;
import com.yr.perftest.platform.agent.contract.ApiErrorBody;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.MachinePrincipal;
import com.yr.perftest.platform.identity.Principal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * agent 面治理过滤器（T10）：限流（速率 + 并发）→ 请求审计 → 输出边界脱敏。
 * 挂在鉴权过滤器之后，仅作用于 `/api/agent/**`。
 */
public class AgentGovernanceFilter extends OncePerRequestFilter {
    static final String AGENT_PATH_PREFIX = "/api/agent";

    private final GovernanceProperties properties;
    private final SlidingWindowRateLimiter rateLimiter;
    private final InFlightLimiter inFlightLimiter;
    private final RedactionService redactionService;
    private final RequestAuditService requestAuditService;
    private final ObjectMapper objectMapper;

    public AgentGovernanceFilter(
            GovernanceProperties properties,
            SlidingWindowRateLimiter rateLimiter,
            InFlightLimiter inFlightLimiter,
            RedactionService redactionService,
            RequestAuditService requestAuditService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.inFlightLimiter = inFlightLimiter;
        this.redactionService = redactionService;
        this.requestAuditService = requestAuditService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !request.getRequestURI().startsWith(AGENT_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        String principalKey = principalKey(request);
        PrincipalDetails details = principalDetails(request);

        if (!rateLimiter.tryAcquire(principalKey, startedAt, capacityFor(details.principalType()))) {
            audit(request, response, details, requestId, startedAt);
            writeRateLimited(response);
            return;
        }
        if (!inFlightLimiter.tryAcquire(principalKey, maxInFlightFor(details.principalType()))) {
            audit(request, response, details, requestId, startedAt);
            writeRateLimited(response);
            return;
        }
        try {
            RedactingResponseWrapper wrapper = new RedactingResponseWrapper(response);
            filterChain.doFilter(request, wrapper);
            byte[] body = wrapper.getContentAsByteArray();
            String contentType = wrapper.getContentType();
            if (contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
                body = redactionService.redactJsonBytes(body);
            }
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            response.getOutputStream().flush();
        } finally {
            inFlightLimiter.release(principalKey);
        }
        audit(request, response, details, requestId, startedAt);
    }

    private void writeRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.error(
                        UUID.randomUUID().toString(),
                        AgentExceptionHandler.SCHEMA_VERSION,
                        new ApiErrorBody(AgentErrorCode.RATE_LIMITED, "rate limited")
                )
        );
        response.getWriter().flush();
    }

    private void audit(
            HttpServletRequest request,
            HttpServletResponse response,
            PrincipalDetails details,
            String requestId,
            long startedAt
    ) {
        String query = request.getQueryString();
        requestAuditService.record(
                requestId,
                details.principalType(),
                details.principalName(),
                request.getMethod(),
                request.getRequestURI(),
                query == null ? null : redactionService.redactText(query),
                response.getStatus(),
                System.currentTimeMillis() - startedAt
        );
    }

    private int capacityFor(String principalType) {
        GovernanceProperties.RateLimit limit = properties.getRateLimit();
        return switch (principalType) {
            case "HUMAN" -> limit.getHumanCapacity();
            case "MACHINE" -> limit.getMachineCapacity();
            default -> limit.getAnonymousCapacity();
        };
    }

    private int maxInFlightFor(String principalType) {
        GovernanceProperties.RateLimit limit = properties.getRateLimit();
        return switch (principalType) {
            case "HUMAN" -> limit.getMaxInFlightHuman();
            case "MACHINE" -> limit.getMaxInFlightMachine();
            default -> limit.getMaxInFlightAnonymous();
        };
    }

    private String principalKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Principal principal) {
            if (principal instanceof HumanPrincipal human) {
                return "HUMAN:" + human.username();
            }
            if (principal instanceof MachinePrincipal machine) {
                return "MACHINE:" + machine.apiKeyId();
            }
            return "PRINCIPAL:other";
        }
        return "ANONYMOUS:" + request.getRemoteAddr();
    }

    private PrincipalDetails principalDetails(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Principal principal) {
            if (principal instanceof HumanPrincipal human) {
                return new PrincipalDetails("HUMAN", human.username());
            }
            if (principal instanceof MachinePrincipal machine) {
                return new PrincipalDetails("MACHINE", Long.toString(machine.apiKeyId()));
            }
            return new PrincipalDetails("PRINCIPAL", "other");
        }
        return new PrincipalDetails("ANONYMOUS", request.getRemoteAddr());
    }

    private record PrincipalDetails(String principalType, String principalName) {
    }
}
