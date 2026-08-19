package com.yr.perftest.platform.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.identity.Principal;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

/**
 * Streamable HTTP MCP Server 装配（T12）：机器身份经现有安全链（T1 API Key）接入，
 * 工具复用 Facade，响应复用 T4 稳定错误码。
 */
@Configuration
public class McpServerConfiguration {
    private static final String MCP_ENDPOINT = "/mcp";

    @Bean
    public HttpServletStreamableServerTransportProvider mcpStreamableTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint(MCP_ENDPOINT)
                .contextExtractor(this::principalContext)
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServletRegistration(
            HttpServletStreamableServerTransportProvider transportProvider
    ) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transportProvider, MCP_ENDPOINT);
        registration.setName("mcp");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    public McpSyncServer mcpSyncServer(
            HttpServletStreamableServerTransportProvider transportProvider,
            McpToolRegistry registry,
            ObjectMapper objectMapper
    ) {
        McpServer.StreamableSyncSpecification specification = McpServer.sync(transportProvider);
        specification.serverInfo("performance-test-platform", "0.1.0");
        specification.instructions("Agent-ready performance test platform: navigate projects, design and "
                + "execute load tests, observe executions, diagnose with deterministic analysis and "
                + "evidence drill-down, capture supplementary evidence and verify optimizations.");
        specification.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build());

        List<McpServerFeatures.SyncToolSpecification> tools = registry.all().stream()
                .map(tool -> toolSpecification(tool, registry, objectMapper))
                .toList();
        specification.tools(tools);
        specification.jsonMapper(McpJsonDefaults.getMapper());
        return specification.build();
    }

    private McpServerFeatures.SyncToolSpecification toolSpecification(
            McpTool tool,
            McpToolRegistry registry,
            ObjectMapper objectMapper
    ) {
        McpSchema.Tool schema = McpSchema.Tool.builder()
                .name(tool.name())
                .title(tool.title())
                .description(tool.description() + " [stage: " + tool.stage() + "]")
                .inputSchema(McpJsonDefaults.getMapper(), toJson(objectMapper, tool.inputSchema()))
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(schema)
                .callHandler((exchange, request) -> handleCall(tool, registry, objectMapper, exchange, request))
                .build();
    }

    private McpSchema.CallToolResult handleCall(
            McpTool tool,
            McpToolRegistry registry,
            ObjectMapper objectMapper,
            McpSyncServerExchange exchange,
            McpSchema.CallToolRequest request
    ) {
        Principal principal = registry.principalFrom(exchange.transportContext());
        if (!(principal instanceof com.yr.perftest.platform.identity.MachinePrincipal)) {
            return McpToolSupport.error(objectMapper, "AUTHENTICATION_FAILED", "machine identity required");
        }
        if (!registry.visible(tool, principal)) {
            return McpToolSupport.error(objectMapper, "ACCESS_DENIED", "tool requires write scope");
        }
        // SDK 可能在 Reactor 线程执行工具处理器，需把请求级身份恢复到当前线程，
        // 使 FacadeGuard 的 SecurityContextHolder 校验在工具线程同样生效。
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                AuthorityUtils.NO_AUTHORITIES
        ));
        SecurityContextHolder.setContext(context);
        try {
            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            return McpToolSupport.ok(objectMapper, tool.call(args, principal));
        } catch (RuntimeException exception) {
            return McpToolSupport.failure(objectMapper, exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private McpTransportContext principalContext(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Principal principal) {
            return McpTransportContext.create(Map.of(McpToolRegistry.CONTEXT_KEY_PRINCIPAL, principal));
        }
        return McpTransportContext.create(Map.of());
    }

    private String toJson(ObjectMapper objectMapper, Map<String, Object> schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize tool input schema", exception);
        }
    }
}
