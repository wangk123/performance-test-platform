import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type {
  Project,
  ProjectMember,
  ProjectRole,
  ScriptAsset,
  StatusFilter,
} from '../types';
import { STORAGE_KEY } from '../constants';
import { nextId } from '../utils/format';
import { createSeedData, normalizeScriptAsset } from '../utils/seed';
import { useAuth } from './useAuth';

const projects = ref<Project[]>([]);
const members = ref<ProjectMember[]>([]);
const scriptAssets = ref<ScriptAsset[]>([]);

const selectedProjectId = ref<number | null>(null);
const workspaceProjectId = ref<number | null>(null);
const selectedScriptId = ref<number | null>(null);

const projectKeyword = ref('');
const scriptKeyword = ref('');
const projectStatusFilter = ref<StatusFilter>('ACTIVE');

let initialized = false;

function loadWorkspace() {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored) {
    try {
      const parsed = JSON.parse(stored) as {
        projects: Project[];
        members: ProjectMember[];
        scriptAssets: ScriptAsset[];
      };
      projects.value = parsed.projects;
      members.value = parsed.members;
      scriptAssets.value = parsed.scriptAssets.map(normalizeScriptAsset);
      return;
    } catch {
      localStorage.removeItem(STORAGE_KEY);
    }
  }
  const seed = createSeedData();
  projects.value = seed.projects;
  members.value = seed.members;
  scriptAssets.value = seed.scriptAssets;
}

function persistWorkspace() {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      projects: projects.value,
      members: members.value,
      scriptAssets: scriptAssets.value,
    }),
  );
}

function ensureInit() {
  if (initialized) {
    return;
  }
  initialized = true;
  loadWorkspace();
  watch([projects, members, scriptAssets], persistWorkspace, { deep: true });
  if (selectedProjectId.value === null) {
    selectedProjectId.value = projects.value[0]?.id ?? null;
  }
}

const activeProjects = computed(() => projects.value.filter((project) => project.status === 'ACTIVE'));
const activeProjectCount = computed(() => activeProjects.value.length);
const archivedProjectCount = computed(
  () => projects.value.filter((project) => project.status === 'ARCHIVED').length,
);
const memberTotal = computed(() => members.value.length);
const scriptAssetTotal = computed(() => scriptAssets.value.length);

const filteredProjects = computed(() => {
  const keyword = projectKeyword.value.trim().toLowerCase();
  return projects.value.filter((project) => {
    const matchesStatus = projectStatusFilter.value === 'ALL' || project.status === projectStatusFilter.value;
    const source = `${project.code} ${project.name} ${project.ownerUsername} ${project.description}`.toLowerCase();
    return matchesStatus && (!keyword || source.includes(keyword));
  });
});

const selectedProject = computed(
  () => projects.value.find((project) => project.id === selectedProjectId.value) ?? null,
);
const currentProject = computed(
  () => projects.value.find((project) => project.id === workspaceProjectId.value) ?? null,
);
const recentProjects = computed(
  () => [...projects.value].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 4),
);

const activeProjectScripts = computed(() =>
  scriptAssets.value.filter((script) =>
    projects.value.some((project) => project.id === script.projectId && project.status === 'ACTIVE'),
  ),
);

const currentProjectScripts = computed(() => {
  if (!currentProject.value) {
    return [];
  }
  return scriptAssets.value.filter((script) => script.projectId === currentProject.value?.id);
});

const filteredScriptAssets = computed(() => {
  const keyword = scriptKeyword.value.trim().toLowerCase();
  return currentProjectScripts.value.filter((script) => {
    const source = `${script.name} ${script.sourceFile} ${script.apis
      .map((api) => api.path)
      .join(' ')} ${script.variables.map((item) => item.key).join(' ')}`.toLowerCase();
    return !keyword || source.includes(keyword);
  });
});

const selectedScriptAsset = computed(
  () =>
    currentProjectScripts.value.find((script) => script.id === selectedScriptId.value) ??
    currentProjectScripts.value[0] ??
    null,
);

const currentProjectMonitors = computed(() => {
  const monitors = currentProjectScripts.value.flatMap((script) => script.monitors);
  const unique = new Map(monitors.map((monitor) => [monitor.target, monitor]));
  return [...unique.values()];
});
const currentProjectMonitorCount = computed(() => currentProjectMonitors.value.length);

const pendingTaskCount = computed(
  () => activeProjectCount.value + currentProjectScripts.value.length + 2,
);
const monitorTargetTotal = computed(
  () =>
    new Set(
      scriptAssets.value.flatMap((script) =>
        script.monitors.map((monitor) => `${script.projectId}:${monitor.target}`),
      ),
    ).size,
);

const reportMocks = computed(() => [
  { name: `${currentProject.value?.name ?? '项目'} 容量基线报告`, time: '05/30 18:20', result: '通过' },
  { name: `${currentProject.value?.name ?? '项目'} 瓶颈复测报告`, time: '05/28 21:05', result: '待复核' },
]);

watch(currentProjectScripts, (scripts) => {
  if (!scripts.some((script) => script.id === selectedScriptId.value)) {
    selectedScriptId.value = scripts[0]?.id ?? null;
  }
});

function selectProject(project: Project) {
  selectedProjectId.value = project.id;
}

function enterProject(project: Project) {
  selectedProjectId.value = project.id;
  workspaceProjectId.value = project.id;
  selectedScriptId.value = scriptsByProject(project.id)[0]?.id ?? null;
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

function ensureOwnerMember(project: Project) {
  members.value
    .filter((member) => member.projectId === project.id && member.role === 'OWNER')
    .forEach((member) => {
      member.role = 'MEMBER';
    });
  const owner = members.value.find(
    (member) => member.projectId === project.id && member.username === project.ownerUsername,
  );
  if (owner) {
    owner.role = 'OWNER';
  } else {
    members.value.push({
      id: nextId(members.value),
      projectId: project.id,
      username: project.ownerUsername,
      displayName: project.ownerUsername,
      role: 'OWNER',
    });
  }
}

type ProjectFormPayload = {
  code: string;
  name: string;
  ownerUsername: string;
  description: string;
};

function saveProject(payload: ProjectFormPayload, editing: Project | null) {
  const duplicate = projects.value.some(
    (project) => project.code === payload.code && project.id !== editing?.id,
  );
  if (duplicate) {
    return { ok: false as const, message: '项目编码已存在，不能重复保存' };
  }
  const now = new Date().toISOString();
  if (editing) {
    const target = projects.value.find((project) => project.id === editing.id);
    if (target) {
      target.name = payload.name;
      target.ownerUsername = payload.ownerUsername;
      target.description = payload.description;
      target.updatedAt = now;
      ensureOwnerMember(target);
      selectedProjectId.value = target.id;
    }
    return { ok: true as const, message: '项目已更新' };
  }
  const project: Project = {
    id: nextId(projects.value),
    code: payload.code,
    name: payload.name,
    description: payload.description,
    ownerUsername: payload.ownerUsername,
    status: 'ACTIVE',
    createdAt: now,
    updatedAt: now,
  };
  projects.value.unshift(project);
  ensureOwnerMember(project);
  selectedProjectId.value = project.id;
  return { ok: true as const, message: '项目已创建' };
}

async function archiveProject(project: Project) {
  try {
    await ElMessageBox.confirm(`归档后，${project.name} 不会出现在新建任务入口，但历史资产仍可查看。`, '确认归档项目', {
      confirmButtonText: '归档',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return false;
  }
  project.status = 'ARCHIVED';
  project.updatedAt = new Date().toISOString();
  if (workspaceProjectId.value === project.id) {
    workspaceProjectId.value = null;
  }
  ElMessage.success('项目已归档');
  return true;
}

function restoreProject(project: Project) {
  project.status = 'ACTIVE';
  project.updatedAt = new Date().toISOString();
  ElMessage.success('项目已恢复');
}

function addMember(
  projectId: number,
  payload: { username: string; displayName: string; role: ProjectRole },
) {
  const project = projects.value.find((item) => item.id === projectId);
  if (!project) {
    return { ok: false as const, message: '项目不存在' };
  }
  if (!payload.username) {
    return { ok: false as const, message: '请输入成员账号' };
  }
  const exists = members.value.some(
    (member) => member.projectId === projectId && member.username === payload.username,
  );
  if (exists) {
    return { ok: false as const, message: '该成员已在项目中' };
  }
  if (payload.role === 'OWNER') {
    members.value
      .filter((member) => member.projectId === projectId && member.role === 'OWNER')
      .forEach((member) => {
        member.role = 'MEMBER';
      });
    project.ownerUsername = payload.username;
  }
  members.value.push({
    id: nextId(members.value),
    projectId,
    username: payload.username,
    displayName: payload.displayName || payload.username,
    role: payload.role,
  });
  return { ok: true as const, message: '成员已添加' };
}

function removeMember(projectId: number, username: string) {
  members.value = members.value.filter(
    (member) => !(member.projectId === projectId && member.username === username),
  );
  ElMessage.success('成员已移除');
}

async function deleteScriptAsset(script: ScriptAsset) {
  try {
    await ElMessageBox.confirm(
      `确认删除脚本「${script.name}」？删除后会移除本地 Mock 资产和步骤编排。`,
      '删除脚本',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    );
  } catch {
    return false;
  }
  scriptAssets.value = scriptAssets.value.filter((item) => item.id !== script.id);
  if (selectedScriptId.value === script.id) {
    selectedScriptId.value = currentProjectScripts.value[0]?.id ?? null;
  }
  ElMessage.success('脚本已删除');
  return true;
}

function resetWorkspace() {
  const seed = createSeedData();
  projects.value = seed.projects;
  members.value = seed.members;
  scriptAssets.value = seed.scriptAssets;
  selectedProjectId.value = projects.value[0]?.id ?? null;
  workspaceProjectId.value = null;
  selectedScriptId.value = null;
  ElMessage.success('Mock 数据已重置');
}

const { logout: authLogout } = useAuth();
function fullLogout() {
  authLogout();
  workspaceProjectId.value = null;
}

export function useWorkspace() {
  ensureInit();
  return {
    projects,
    members,
    scriptAssets,
    selectedProjectId,
    workspaceProjectId,
    selectedScriptId,
    projectKeyword,
    scriptKeyword,
    projectStatusFilter,
    activeProjects,
    activeProjectCount,
    archivedProjectCount,
    memberTotal,
    scriptAssetTotal,
    pendingTaskCount,
    monitorTargetTotal,
    recentProjects,
    activeProjectScripts,
    currentProject,
    selectedProject,
    currentProjectScripts,
    filteredProjects,
    filteredScriptAssets,
    selectedScriptAsset,
    currentProjectMonitors,
    currentProjectMonitorCount,
    reportMocks,
    selectProject,
    enterProject,
    exitProjectWorkspace,
    membersByProject,
    scriptsByProject,
    projectName,
    saveProject,
    archiveProject,
    restoreProject,
    addMember,
    removeMember,
    deleteScriptAsset,
    resetWorkspace,
    fullLogout,
  };
}
