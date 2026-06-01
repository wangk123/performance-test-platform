package com.yr.perftest.platform.project;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PersistentProjectService implements ProjectOperations {
    private final PersistentProjectRepository projectRepository;
    private final PersistentProjectMemberRepository memberRepository;

    public PersistentProjectService(
            PersistentProjectRepository projectRepository,
            PersistentProjectMemberRepository memberRepository
    ) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional
    public Project createProject(String code, String name, String description, String ownerUsername) {
        if (isBlank(code)) {
            throw new ProjectValidationException("project code is required");
        }
        if (isBlank(name)) {
            throw new ProjectValidationException("project name is required");
        }
        if (projectRepository.existsByCode(code)) {
            throw new ProjectValidationException("project code already exists");
        }

        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord(
                code,
                name,
                description,
                ownerUsername
        ));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), ownerUsername, ProjectRole.OWNER));
        return project.toProject();
    }

    @Override
    @Transactional
    public void archiveProject(long projectId, String operatorUsername) {
        PersistentProjectRecord project = requireProject(projectId);
        requireProjectOwner(projectId, operatorUsername);
        project.archive();
    }

    @Override
    @Transactional
    public void restoreProject(long projectId, String operatorUsername) {
        PersistentProjectRecord project = requireProject(projectId);
        requireProjectOwner(projectId, operatorUsername);
        project.restore();
    }

    @Override
    @Transactional
    public void addMember(long projectId, String username, ProjectRole role, String operatorUsername) {
        requireProject(projectId);
        requireProjectOwner(projectId, operatorUsername);
        if (!memberRepository.existsByProjectIdAndUsername(projectId, username)) {
            memberRepository.save(new PersistentProjectMemberRecord(projectId, username, role));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAccessProject(long projectId, String username) {
        return memberRepository.existsByProjectIdAndUsername(projectId, username);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberInfo> listMembers(long projectId) {
        requireProject(projectId);
        return memberRepository.findAllByProjectIdOrderByIdAsc(projectId).stream()
                .map(PersistentProjectMemberRecord::toProjectMemberInfo)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> listProjectsAvailableForTaskSelection() {
        return listProjects(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> listProjects(boolean includeArchived) {
        List<PersistentProjectRecord> records = includeArchived
                ? projectRepository.findAllByOrderByIdAsc()
                : projectRepository.findAllByStatusOrderByIdAsc(ProjectStatus.ACTIVE);
        return records.stream()
                .map(PersistentProjectRecord::toProject)
                .toList();
    }

    private PersistentProjectRecord requireProject(long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectValidationException("project does not exist"));
    }

    private void requireProjectOwner(long projectId, String username) {
        if (!memberRepository.existsByProjectIdAndUsernameAndRole(projectId, username, ProjectRole.OWNER)) {
            throw new ProjectValidationException("project owner permission is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
