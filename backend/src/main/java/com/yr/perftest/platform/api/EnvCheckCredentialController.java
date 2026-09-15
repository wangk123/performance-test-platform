package com.yr.perftest.platform.api;

import com.yr.perftest.platform.envcheck.EnvCheckCredentialService;
import com.yr.perftest.platform.identity.AuthenticationException;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.ProjectAccessResolver;
import com.yr.perftest.platform.task.plandoc.PlanAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 凭据池 API：密码只写不回显，任何响应不含密码/密文字段。 */
@RestController
@RequestMapping("/api/projects/{projectId}/env-check/credentials")
public class EnvCheckCredentialController {
    private final EnvCheckCredentialService credentialService;
    private final ProjectAccessResolver accessResolver;

    public EnvCheckCredentialController(EnvCheckCredentialService credentialService,
                                        ProjectAccessResolver accessResolver) {
        this.credentialService = credentialService;
        this.accessResolver = accessResolver;
    }

    @GetMapping
    public List<EnvCheckCredentialService.CredentialView> list(@PathVariable long projectId) {
        requireProjectMember(projectId);
        return credentialService.list(projectId);
    }

    @PutMapping
    public EnvCheckCredentialService.CredentialView save(@PathVariable long projectId,
                                                         @RequestBody EnvCheckCredentialService.CredentialInput request) {
        requireProjectMember(projectId);
        return credentialService.save(projectId, requireHuman().username(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long projectId, @PathVariable long id) {
        requireProjectMember(projectId);
        credentialService.delete(projectId, id);
    }

    @PostMapping("/{id}/test")
    public EnvCheckCredentialService.ConnectionTest test(@PathVariable long projectId, @PathVariable long id) {
        requireProjectMember(projectId);
        return credentialService.test(projectId, id);
    }

    /** 项目域门禁：未登录 401；登录但非项目成员 403（与计划域同口径）。 */
    private void requireProjectMember(long projectId) {
        HumanPrincipal principal = requireHuman();
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(projectId, principal, null);
        if (role == ProjectAccessResolver.PlanActorRole.NONE) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
        }
    }

    private HumanPrincipal requireHuman() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof HumanPrincipal human) {
            return human;
        }
        throw new AuthenticationException("login required");
    }
}
