import type { Project, ProjectMember, ProjectRole } from '../types';
import { request } from './http';

export function listProjectsApi(includeArchived = true) {
  return request<Project[]>(`/api/projects?includeArchived=${includeArchived}`);
}

export function getProjectApi(projectId: number) {
  return request<Project>(`/api/projects/${projectId}`);
}

export function createProjectApi(project: Pick<Project, 'code' | 'name' | 'description'>, username: string) {
  return request<Project>('/api/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify(project),
  });
}

export function updateProjectApi(
  projectId: number,
  project: Pick<Project, 'name' | 'description' | 'ownerUsername'>,
  username: string,
) {
  return request<Project>(`/api/projects/${projectId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify(project),
  });
}

export function archiveProjectApi(projectId: number, username: string) {
  return request<Project>(`/api/projects/${projectId}/archive`, {
    method: 'PATCH',
    headers: { 'X-User': username },
  });
}

export function restoreProjectApi(projectId: number, username: string) {
  return request<Project>(`/api/projects/${projectId}/restore`, {
    method: 'PATCH',
    headers: { 'X-User': username },
  });
}

export function listMembersApi(projectId: number) {
  return request<BackendProjectMember[]>(`/api/projects/${projectId}/members`).then((members) =>
    members.map((member, index) => mapProjectMember(member, index)),
  );
}

export function addMemberApi(
  projectId: number,
  payload: { username: string; role: ProjectRole },
  username: string,
) {
  return request<BackendProjectMember>(`/api/projects/${projectId}/members`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify(payload),
  }).then((member) => mapProjectMember(member, 0));
}

export function removeMemberApi(projectId: number, memberUsername: string, username: string) {
  return request<void>(`/api/projects/${projectId}/members/${memberUsername}`, {
    method: 'DELETE',
    headers: { 'X-User': username },
  });
}

type BackendProjectMember = {
  projectId: number;
  username: string;
  role: ProjectRole;
};

function mapProjectMember(member: BackendProjectMember, index: number): ProjectMember {
  return {
    id: index + 1,
    projectId: member.projectId,
    username: member.username,
    displayName: member.username,
    role: member.role,
  };
}
