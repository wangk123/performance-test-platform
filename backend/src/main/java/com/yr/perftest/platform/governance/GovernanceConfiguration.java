package com.yr.perftest.platform.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 治理装配（T10）：脱敏、限流、审计与 agent 面治理过滤器。
 * 过滤器只挂在 Spring Security 链上，禁用 Servlet 容器自动注册避免双跑。
 */
@Configuration
@EnableConfigurationProperties(GovernanceProperties.class)
public class GovernanceConfiguration {
    @Bean
    public RedactionService redactionService(ObjectMapper objectMapper) {
        return new RedactionService(objectMapper);
    }

    @Bean
    public SlidingWindowRateLimiter slidingWindowRateLimiter(GovernanceProperties properties) {
        return new SlidingWindowRateLimiter(properties.getRateLimit().getWindowMillis());
    }

    @Bean
    public InFlightLimiter inFlightLimiter() {
        return new InFlightLimiter();
    }

    @Bean
    public AgentGovernanceFilter agentGovernanceFilter(
            GovernanceProperties properties,
            SlidingWindowRateLimiter rateLimiter,
            InFlightLimiter inFlightLimiter,
            RedactionService redactionService,
            RequestAuditService requestAuditService,
            ObjectMapper objectMapper
    ) {
        return new AgentGovernanceFilter(
                properties,
                rateLimiter,
                inFlightLimiter,
                redactionService,
                requestAuditService,
                objectMapper
        );
    }

    @Bean
    public FilterRegistrationBean<AgentGovernanceFilter> agentGovernanceFilterRegistration(
            AgentGovernanceFilter filter
    ) {
        FilterRegistrationBean<AgentGovernanceFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
