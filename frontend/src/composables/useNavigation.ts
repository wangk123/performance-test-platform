import { computed, ref } from 'vue';
import type { ConfigTab, MainNav, ProjectTab, ScriptAsset } from '../types';
import { configLabel, tabLabel } from '../utils/format';
import { useWorkspace } from './useWorkspace';

const activeMainNav = ref<MainNav>('home');
const activeProjectTab = ref<ProjectTab>('overview');
const activeConfigTab = ref<ConfigTab>('users');

function selectMainNav(nav: MainNav) {
  const { exitProjectWorkspace } = useWorkspace();
  activeMainNav.value = nav;
  exitProjectWorkspace();
  activeProjectTab.value = 'overview';
}

function backToProjects() {
  const { exitProjectWorkspace } = useWorkspace();
  activeMainNav.value = 'projects';
  exitProjectWorkspace();
  activeProjectTab.value = 'overview';
}

function enterProjectTab(tab: ProjectTab) {
  activeProjectTab.value = tab;
}

function openScript(script: ScriptAsset | undefined | null) {
  const { projects, workspaceProjectId, selectedProjectId, selectedScriptId } = useWorkspace();
  if (!script) {
    return;
  }
  const project = projects.value.find((item) => item.id === script.projectId);
  if (project) {
    activeMainNav.value = 'projects';
    workspaceProjectId.value = project.id;
    selectedProjectId.value = project.id;
  }
  activeProjectTab.value = 'scripts';
  selectedScriptId.value = script.id;
}

function buildPageTitle() {
  const { currentProject } = useWorkspace();
  return computed(() => {
    if (currentProject.value) {
      return `${currentProject.value.name} · ${tabLabel(activeProjectTab.value)}`;
    }
    if (activeMainNav.value === 'settings') {
      return `系统配置 · ${configLabel(activeConfigTab.value)}`;
    }
    return activeMainNav.value === 'projects' ? '项目管理' : '首页';
  });
}

export function useNavigation() {
  return {
    activeMainNav,
    activeProjectTab,
    activeConfigTab,
    selectMainNav,
    backToProjects,
    enterProjectTab,
    openScript,
    pageTitle: buildPageTitle(),
  };
}
