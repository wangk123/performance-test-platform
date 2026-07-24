package com.yr.perftest.platform.api;

import com.yr.perftest.platform.identity.AgentApiKeyService;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/agent-api-keys")
public class AgentApiKeyController {
    private final AgentApiKeyService agentApiKeyService;

    public AgentApiKeyController(AgentApiKeyService agentApiKeyService) {
        this.agentApiKeyService = agentApiKeyService;
    }

    @GetMapping
    public List<AgentApiKeyService.AgentApiKeyView> list() {
        requireAdmin();
        return agentApiKeyService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentApiKeyService.IssuedApiKey issue(@RequestBody(required = false) IssueRequest request) {
        requireAdmin();
        IssueRequest body = request == null ? new IssueRequest(null, null) : request;
        return agentApiKeyService.issue(body.scope(), body.expiresAt());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable long id) {
        requireAdmin();
        agentApiKeyService.revoke(id);
    }

    private void requireAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof HumanPrincipal human)
                || !human.roles().contains(SystemRole.ADMIN)) {
            throw new AccessDeniedException("admin role required");
        }
    }

    public record IssueRequest(String scope, Instant expiresAt) {
    }
}
