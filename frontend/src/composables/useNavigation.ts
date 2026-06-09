import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { ConfigTab, MainNav, Project, ProjectTab, ScriptAsset } from '../types';
import { configLabel, tabLabel } from '../utils/format';
import { useWorkspace } from './useWorkspace';

const activeConfigTab = computed<ConfigTab>(() => 'users');

function routeTab(name: string | symbol | null | undefined): ProjectTab {
  if (name === 'project-scripts' || name === 'script-editor') {
    return 'scripts';
  }
  if (name === 'project-tasks' || name === 'project-task-detail') {
    return 'tasks';
  }
  if (name === 'project-monitoring') {
    return 'monitoring';
  }
  if (name === 'project-reports') {
    return 'reports';
  }
  if (name === 'project-data') {
    return 'data';
  }
  if (name === 'project-functions') {
    return 'functions';
  }
  if (name === 'project-members') {
    return 'members';
  }
  return 'overview';
}

export function useNavigation() {
  const route = useRoute();
  const router = useRouter();
  const workspace = useWorkspace();

  const activeMainNav = computed<MainNav>(() => {
    if (route.path.startsWith('/settings')) {
      return 'settings';
    }
    if (route.path.startsWith('/projects')) {
      return 'projects';
    }
    return 'home';
  });
  const activeProjectTab = computed<ProjectTab>(() => routeTab(route.name));

  function selectMainNav(nav: MainNav) {
    workspace.exitProjectWorkspace();
    void router.push(nav === 'home' ? '/' : `/${nav}`);
  }

  function backToProjects() {
    workspace.exitProjectWorkspace();
    void router.push('/projects');
  }

  function enterProjectTab(tab: ProjectTab) {
    const projectId = workspace.currentProject.value?.id ?? Number(route.params.projectId);
    if (projectId) {
      void router.push(`/projects/${projectId}/${tab}`);
    }
  }

  function enterProject(project: Project) {
    void workspace.loadProjectContext(project.id);
    void router.push(`/projects/${project.id}/overview`);
  }

  function openScript(script: ScriptAsset | undefined | null) {
    if (!script) {
      return;
    }
    workspace.selectedScriptId.value = script.id;
    void router.push(`/projects/${script.projectId}/scripts`);
  }

  const pageTitle = computed(() => {
    if (workspace.currentProject.value) {
      return `${workspace.currentProject.value.name} · ${tabLabel(activeProjectTab.value)}`;
    }
    if (activeMainNav.value === 'settings') {
      return `系统配置 · ${configLabel(activeConfigTab.value)}`;
    }
    return activeMainNav.value === 'projects' ? '项目管理' : '首页';
  });

  return {
    activeMainNav,
    activeProjectTab,
    activeConfigTab,
    selectMainNav,
    backToProjects,
    enterProject,
    enterProjectTab,
    openScript,
    pageTitle,
  };
}
