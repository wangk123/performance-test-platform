package com.yr.perftest.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.AgentErrorCode;
import com.yr.perftest.platform.agent.contract.ApiErrorBody;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.identity.AgentApiKeyService;
import com.yr.perftest.platform.identity.AuthTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.UUID;

@Configuration
public class SecurityConfiguration {
    @Bean
    public AuthenticationFilter authenticationFilter(
            AuthTokenService authTokenService,
            AgentApiKeyService agentApiKeyService
    ) {
        return new AuthenticationFilter(authTokenService, agentApiKeyService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationFilter authenticationFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/api/auth/login",
                                "/actuator/health",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            if (request.getRequestURI() != null && request.getRequestURI().startsWith("/api/agent")) {
                                objectMapper.writeValue(
                                        response.getWriter(),
                                        ApiResponse.error(
                                                UUID.randomUUID().toString(),
                                                AgentExceptionHandler.SCHEMA_VERSION,
                                                new ApiErrorBody(AgentErrorCode.AUTHENTICATION_FAILED, "unauthorized")
                                        )
                                );
                            } else {
                                response.getWriter().write("{\"message\":\"unauthorized\"}");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            if (request.getRequestURI() != null && request.getRequestURI().startsWith("/api/agent")) {
                                objectMapper.writeValue(
                                        response.getWriter(),
                                        ApiResponse.error(
                                                UUID.randomUUID().toString(),
                                                AgentExceptionHandler.SCHEMA_VERSION,
                                                new ApiErrorBody(AgentErrorCode.ACCESS_DENIED, "forbidden")
                                        )
                                );
                            } else {
                                response.getWriter().write("{\"message\":\"forbidden\"}");
                            }
                        }))
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .build();
    }
}
