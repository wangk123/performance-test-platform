import { createRouter, createWebHistory } from 'vue-router';
import { useAuth } from '../composables/useAuth';
import AuthScreen from '../views/AuthScreen.vue';
import MainLayout from '../views/MainLayout.vue';
import HomeView from '../components/views/HomeView.vue';
import ProjectListView from '../components/views/ProjectListView.vue';
import ProjectDetail from '../components/views/ProjectDetail.vue';
import SettingsView from '../components/views/SettingsView.vue';
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
        { path: 'settings/llm/providers', name: 'settings-llm-providers', component: SettingsView },
        { path: 'settings/llm/models', name: 'settings-llm-models', component: SettingsView },
        { path: 'settings/llm/call-records', name: 'settings-llm-call-records', component: SettingsView },
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
