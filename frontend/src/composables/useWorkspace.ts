import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { Project, ProjectMember, ProjectRole, ScriptAsset, StatusFilter } from '../types';
import { createSeedData, normalizeScriptAsset } from '../utils/seed';
import { useAuth } from './useAuth';
import { addMemberApi, archiveProjectApi, createProjectApi, getProjectApi, listMembersApi, listProjectsApi, removeMemberApi, restoreProjectApi, updateProjectApi } from '../api/projects';
import { deleteScriptApi, listScriptDefinitionsApi, mapScriptDefinition } from '../api/scripts';

const projects = ref<Project[]>([]), members = ref<ProjectMember[]>([]), scriptAssets = ref<ScriptAsset[]>([]);
const selectedProjectId = ref<number | null>(null), workspaceProjectId = ref<number | null>(null), selectedScriptId = ref<number | null>(null);
const projectKeyword = ref(''), scriptKeyword = ref('');
const projectStatusFilter = ref<StatusFilter>('ACTIVE');

let projectsRequest: Promise<Project[]> | null = null;
const projectRequests = new Map<number, Promise<Project>>();
const memberRequests = new Map<number, Promise<ProjectMember[]>>();
const scriptRequests = new Map<number, Promise<ScriptAsset[]>>();

function upsertProject(project: Project) {
  const index = projects.value.findIndex((item) => item.id === project.id);
  if (index >= 0) {
    projects.value.splice(index, 1, project);
  } else {
    projects.value.unshift(project);
  }
}

function replaceProjectMembers(projectId: number, items: ProjectMember[]) {
  members.value = [...members.value.filter((member) => member.projectId !== projectId), ...items];
}

function replaceProjectScripts(projectId: number, items: ScriptAsset[]) {
  scriptAssets.value = [...scriptAssets.value.filter((script) => script.projectId !== projectId), ...items];
}

async function loadProjects(force = false) {
  if (!force && projects.value.length > 0) {
    return projects.value;
  }
  if (!projectsRequest || force) {
    projectsRequest = listProjectsApi(true)
      .then((items) => {
        projects.value = items;
        if (selectedProjectId.value === null) {
          selectedProjectId.value = items[0]?.id ?? null;
        }
        return items;
      })
      .catch(() => {
        projects.value = createSeedData().projects;
        return projects.value;
      })
      .finally(() => {
        projectsRequest = null;
      });
  }
  return projectsRequest;
}

async function loadProject(projectId: number, force = false) {
  const cached = projects.value.find((project) => project.id === projectId);
  if (cached && !force) {
    return cached;
  }
  if (!projectRequests.has(projectId) || force) {
    projectRequests.set(
      projectId,
      getProjectApi(projectId)
        .then((project) => {
          upsertProject(project);
          return project;
        })
        .catch(() => createSeedData().projects.find((project) => project.id === projectId) ?? cached!)
        .finally(() => projectRequests.delete(projectId)),
    );
  }
  return projectRequests.get(projectId)!;
}

async function loadMembers(projectId: number, force = false) {
  const cached = members.value.filter((member) => member.projectId === projectId);
  if (cached.length > 0 && !force) {
    return cached;
  }
  if (!memberRequests.has(projectId) || force) {
    memberRequests.set(
      projectId,
      listMembersApi(projectId)
        .then((items) => {
          replaceProjectMembers(projectId, items);
          return items;
        })
        .catch(() => {
          const items = createSeedData().members.filter((member) => member.projectId === projectId);
          replaceProjectMembers(projectId, items);
          return items;
        })
        .finally(() => memberRequests.delete(projectId)),
    );
  }
  return memberRequests.get(projectId)!;
}

async function loadProjectScripts(projectId: number, force = false) {
  const cached = scriptAssets.value.filter((script) => script.projectId === projectId);
  if (cached.length > 0 && !force) {
    return cached;
  }
  if (!scriptRequests.has(projectId) || force) {
    scriptRequests.set(
      projectId,
      listScriptDefinitionsApi(projectId)
        .then((items) => items.map(mapScriptDefinition).map(normalizeScriptAsset))
        .then((items) => {
          replaceProjectScripts(projectId, items);
          return items;
        })
        .catch(() => {
          const items = createSeedData().scriptAssets.filter((script) => script.projectId === projectId);
          replaceProjectScripts(projectId, items);
          return items;
        })
        .finally(() => scriptRequests.delete(projectId)),
    );
  }
  return scriptRequests.get(projectId)!;
}

async function loadProjectContext(projectId: number) {
  workspaceProjectId.value = projectId;
  selectedProjectId.value = projectId;
  await Promise.all([loadProject(projectId), loadProjectScripts(projectId)]);
}

const activeProjects = computed(() => projects.value.filter((project) => project.status === 'ACTIVE'));
const activeProjectCount = computed(() => activeProjects.value.length);
const archivedProjectCount = computed(() => projects.value.filter((project) => project.status === 'ARCHIVED').length);
const memberTotal = computed(() => members.value.length);
const scriptAssetTotal = computed(() => scriptAssets.value.length);
const selectedProject = computed(() => projects.value.find((project) => project.id === selectedProjectId.value) ?? null);
const currentProject = computed(() => projects.value.find((project) => project.id === workspaceProjectId.value) ?? null);
const currentProjectScripts = computed(() =>
  currentProject.value ? scriptAssets.value.filter((script) => script.projectId === currentProject.value?.id) : [],
);
const currentProjectMonitors = computed(() => {
  const unique = new Map(currentProjectScripts.value.flatMap((script) => script.monitors).map((monitor) => [monitor.target, monitor]));
  return [...unique.values()];
});
const currentProjectMonitorCount = computed(() => currentProjectMonitors.value.length);
const filteredProjects = computed(() => {
  const keyword = projectKeyword.value.trim().toLowerCase();
  return projects.value.filter((project) => {
    const matchesStatus = projectStatusFilter.value === 'ALL' || project.status === projectStatusFilter.value;
    const source = `${project.code} ${project.name} ${project.ownerUsername} ${project.description}`.toLowerCase();
    return matchesStatus && (!keyword || source.includes(keyword));
  });
});
const filteredScriptAssets = computed(() => {
  const keyword = scriptKeyword.value.trim().toLowerCase();
  return currentProjectScripts.value.filter((script) => {
    const source = `${script.name} ${script.sourceFile} ${script.apis.map((api) => api.path).join(' ')}`.toLowerCase();
    return !keyword || source.includes(keyword);
  });
});
const selectedScriptAsset = computed(
  () => currentProjectScripts.value.find((script) => script.id === selectedScriptId.value) ?? currentProjectScripts.value[0] ?? null,
);
const reportMocks = computed(() => [
  { name: `${currentProject.value?.name ?? '项目'} 容量基线报告`, time: '05/30 18:20', result: '通过' },
  { name: `${currentProject.value?.name ?? '项目'} 瓶颈复测报告`, time: '05/28 21:05', result: '待复核' },
]);

function selectProject(project: Project) {
  selectedProjectId.value = project.id;
}

async function enterProject(project: Project) {
  await loadProjectContext(project.id);
}

function exitProjectWorkspace() {
  workspaceProjectId.value = null;
}

function membersByProject(projectId: number) {
  return members.value.filter((member) => member.projectId === projectId);
}

function scriptsByProject(projectId: number) {
  return scriptAssets.value.filter((script) => script.projectId === projectId);
}

function projectName(projectId: number) {
  return projects.value.find((project) => project.id === projectId)?.name ?? '未知项目';
}

async function saveProject(payload: { code: string; name: string; ownerUsername: string; description: string }, editing: Project | null) {
  if (!payload.code || !payload.name || !payload.ownerUsername) {
    return { ok: false as const, message: '项目编码、项目名称和负责人不能为空' };
  }
  try {
    const project = editing
      ? await updateProjectApi(editing.id, payload, payload.ownerUsername)
      : await createProjectApi(payload, payload.ownerUsername);
    upsertProject(project);
    selectedProjectId.value = project.id;
    return { ok: true as const, message: editing ? '项目已更新' : '项目已创建' };
  } catch (error) {
    return { ok: false as const, message: error instanceof Error ? error.message : '项目保存失败' };
  }
}

async function archiveProject(project: Project) {
  const { currentUser } = useAuth();
  try {
    await ElMessageBox.confirm(`归档后，${project.name} 不会出现在新建任务入口，但历史资产仍可查看。`, '确认归档项目');
    upsertProject(await archiveProjectApi(project.id, currentUser.value?.username ?? project.ownerUsername));
    if (workspaceProjectId.value === project.id) {
      workspaceProjectId.value = null;
    }
    ElMessage.success('项目已归档');
    return true;
  } catch {
    return false;
  }
}

async function restoreProject(project: Project) {
  const { currentUser } = useAuth();
  try {
    upsertProject(await restoreProjectApi(project.id, currentUser.value?.username ?? project.ownerUsername));
    ElMessage.success('项目已恢复');
    return true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '项目恢复失败');
    return false;
  }
}

async function addMember(projectId: number, payload: { username: string; displayName: string; role: ProjectRole }) {
  const { currentUser } = useAuth();
  if (!payload.username) {
    return { ok: false as const, message: '请输入成员账号' };
  }
  try {
    await addMemberApi(projectId, { username: payload.username, role: payload.role }, currentUser.value?.username ?? 'admin');
    await loadMembers(projectId, true);
    return { ok: true as const, message: '成员已添加' };
  } catch (error) {
    return { ok: false as const, message: error instanceof Error ? error.message : '成员添加失败' };
  }
}

async function removeMember(projectId: number, username: string) {
  const { currentUser } = useAuth();
  await removeMemberApi(projectId, username, currentUser.value?.username ?? 'admin');
  await loadMembers(projectId, true);
  ElMessage.success('成员已移除');
}

async function deleteScriptAsset(script: ScriptAsset) {
  try {
    await ElMessageBox.confirm(`确认删除脚本「${script.name}」？`, '删除脚本');
    await deleteScriptApi(script.projectId, script.id);
    scriptAssets.value = scriptAssets.value.filter((item) => item.id !== script.id);
    ElMessage.success('脚本已删除');
    return true;
  } catch {
    return false;
  }
}

function resetWorkspace() {
  projects.value = []; members.value = []; scriptAssets.value = [];
  selectedProjectId.value = null;
  workspaceProjectId.value = null;
  selectedScriptId.value = null;
  void loadProjects(true);
}

const { logout: authLogout } = useAuth();
function fullLogout() {
  authLogout();
  exitProjectWorkspace();
}

export function useWorkspace() {
  return {
    projects, members, scriptAssets, selectedProjectId, workspaceProjectId, selectedScriptId,
    projectKeyword, scriptKeyword, projectStatusFilter,
    activeProjects, activeProjectCount, archivedProjectCount, memberTotal, scriptAssetTotal,
    currentProject, selectedProject, currentProjectScripts, filteredProjects, filteredScriptAssets,
    selectedScriptAsset, currentProjectMonitors, currentProjectMonitorCount, reportMocks,
    loadProjects, loadProject, loadMembers, loadProjectScripts, loadProjectContext,
    selectProject, enterProject, exitProjectWorkspace, membersByProject, scriptsByProject, projectName,
    saveProject, archiveProject, restoreProject, addMember, removeMember, deleteScriptAsset,
    resetWorkspace, fullLogout,
  };
}
