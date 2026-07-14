import { createRouter, createWebHistory } from 'vue-router';
import { useAuth } from '../composables/useAuth';
import AuthScreen from '../views/AuthScreen.vue';
import MainLayout from '../views/MainLayout.vue';
import HomeView from '../components/views/HomeView.vue';
import ProjectListView from '../components/views/ProjectListView.vue';
import ProjectDetail from '../components/views/ProjectDetail.vue';
import SettingsView from '../components/views/SettingsView.vue';
import LlmConfigView from '../components/views/LlmConfigView.vue';
import ExecutionNodeView from '../components/views/ExecutionNodeView.vue';
import ScriptEditorPage from '../views/ScriptEditorPage.vue';
import ReportPreviewPage from '../views/ReportPreviewPage.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: AuthScreen },
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', name: 'home', component: HomeView },
        { path: 'projects', name: 'projects', component: ProjectListView },
        { path: 'execution-nodes', name: 'execution-nodes', component: ExecutionNodeView },
        { path: 'settings', name: 'settings', component: SettingsView },
        { path: 'llm-config', redirect: '/llm-config/providers' },
        { path: 'llm-config/providers', name: 'llm-config-providers', component: LlmConfigView },
        { path: 'llm-config/models', name: 'llm-config-models', component: LlmConfigView },
        { path: 'llm-config/call-records', name: 'llm-config-call-records', component: LlmConfigView },
        { path: 'settings/llm/providers', redirect: '/llm-config/providers' },
        { path: 'settings/llm/models', redirect: '/llm-config/models' },
        { path: 'settings/llm/call-records', redirect: '/llm-config/call-records' },
        { path: 'projects/:projectId', redirect: (to) => `/projects/${to.params.projectId}/overview` },
        { path: 'projects/:projectId/overview', name: 'project-overview', component: ProjectDetail },
        { path: 'projects/:projectId/scripts', name: 'project-scripts', component: ProjectDetail },
        { path: 'projects/:projectId/task-plans', name: 'project-task-plans', component: ProjectDetail },
        { path: 'projects/:projectId/task-plans/:planId', name: 'project-task-plan-detail', component: ProjectDetail },
        { path: 'projects/:projectId/scenarios/:scenarioId', redirect: (to) => `/projects/${to.params.projectId}/task-plans` },
        { path: 'projects/:projectId/executions/:executionId', name: 'project-execution-detail', component: ProjectDetail },
        { path: 'projects/:projectId/monitoring', name: 'project-monitoring', component: ProjectDetail },
        { path: 'projects/:projectId/reports', name: 'project-reports', component: ProjectDetail },
        { path: 'projects/:projectId/data', name: 'project-data', component: ProjectDetail },
        { path: 'projects/:projectId/functions', name: 'project-functions', component: ProjectDetail },
        { path: 'projects/:projectId/members', name: 'project-members', component: ProjectDetail },
      ],
    },
    {
      path: '/projects/:projectId/scripts/:scriptId/edit',
      name: 'script-editor',
      component: ScriptEditorPage,
    },
    {
      path: '/projects/:projectId/reports/plans/:planId',
      name: 'report-preview',
      component: ReportPreviewPage,
    },
  ],
});

router.beforeEach((to) => {
  const { currentUser } = useAuth();
  if (!currentUser.value && to.name !== 'login') {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (currentUser.value && to.name === 'login') {
    return typeof to.query.redirect === 'string' ? to.query.redirect : '/';
  }
  return true;
});
