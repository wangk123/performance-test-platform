package com.yr.perftest.platform.project;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.ProjectAccessResolver.PlanActorRole;
import org.springframework.stereotype.Service;

/** 计划动作的角色解析（设计 §13.1）：ADMIN > 项目 OWNER > 计划负责人 > 成员 > 无。 */
@Service
public class ProjectAccessResolver {

    public enum PlanActorRole { SYSTEM_ADMIN, PROJECT_OWNER, PLAN_OWNER, MEMBER, NONE }

    private final PersistentProjectRepository projectRepository;
    private final PersistentProjectMemberRepository memberRepository;

    public ProjectAccessResolver(PersistentProjectRepository projectRepository,
                                 PersistentProjectMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    public PlanActorRole resolve(long projectId, HumanPrincipal principal, String planCreatedBy) {
        if (principal == null || principal.username() == null) {
            return PlanActorRole.NONE;
        }
        if (principal.roles().contains(SystemRole.ADMIN)) {
            return PlanActorRole.SYSTEM_ADMIN;
        }
        String username = principal.username();
        if (projectRepository.findById(projectId)
                .map(project -> username.equals(project.getOwnerUsername()))
                .orElse(false)) {
            return PlanActorRole.PROJECT_OWNER;
        }
        boolean isMember = memberRepository.findByProjectIdAndUsername(projectId, username).isPresent();
        if (!isMember) {
            return PlanActorRole.NONE; // 负责人以成员身份为前提（设计 §13.1 第 4 条）
        }
        if (username.equals(planCreatedBy)) {
            return PlanActorRole.PLAN_OWNER;
        }
        return PlanActorRole.MEMBER;
    }
}
