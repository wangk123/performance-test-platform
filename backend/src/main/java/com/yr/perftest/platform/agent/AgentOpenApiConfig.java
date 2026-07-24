package com.yr.perftest.platform.agent;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentOpenApiConfig {
    @Bean
    public OpenAPI agentPlatformOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Performance Test Platform Agent API")
                .version("1")
                .description("Agent-facing API surface"));
    }

    @Bean
    public GroupedOpenApi agentGroupedOpenApi() {
        return GroupedOpenApi.builder()
                .group("agent")
                .pathsToMatch("/api/agent/**")
                .build();
    }
}
