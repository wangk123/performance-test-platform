import { computed } from 'vue';
import { useRoute } from 'vue-router';
import type { ProjectTab } from '../types';
import { tabLabel } from '../utils/format';
import { useWorkspace } from './useWorkspace';
import { useTaskPlans } from './useTaskPlans';

export interface BreadcrumbSegment {
  label: string;
  to?: string;
}

function resolveProjectTab(routeName: string): ProjectTab {
  if (routeName === 'project-scripts' || routeName === 'script-editor') {
    return 'scripts';
  }
  if (
    routeName === 'project-task-plans' ||
    routeName === 'project-task-plan-detail' ||
    routeName === 'project-scenario-detail' ||
    routeName === 'project-execution-detail'
  ) {
    return 'task-plans';
  }
  if (routeName === 'project-monitoring') return 'monitoring';
  if (routeName === 'project-reports') return 'reports';
  if (routeName === 'project-data') return 'data';
  if (routeName === 'project-functions') return 'functions';
  if (routeName === 'project-members') return 'members';
  return 'overview';
}

export function useBreadcrumb() {
  const route = useRoute();
  const { currentProject } = useWorkspace();
  const { activePlan, activePlanId, executionDetail, plans } = useTaskPlans();

  const breadcrumbs = computed<BreadcrumbSegment[]>(() => {
    const segments: BreadcrumbSegment[] = [];
    const routeName = String(route.name ?? '');

    // 首页：单段，不可点击
    if (routeName === 'home') {
      segments.push({ label: '🏠 首页' });
      return segments;
    }

    // 首页图标段始终可点击
    segments.push({ label: '🏠 首页', to: '/' });

    // 系统配置
    if (route.path.startsWith('/settings')) {
      segments.push({ label: '系统配置' });
      return segments;
    }

    // 执行器配置
    if (route.path.startsWith('/execution-nodes')) {
      segments.push({ label: '执行器配置' });
      return segments;
    }

    // 项目列表
    if (routeName === 'projects') {
      segments.push({ label: '项目列表' });
      return segments;
    }

    // ---- 项目内页面 ----
    const projectId = currentProject.value?.id ?? Number(route.params.projectId);
    if (!projectId) {
      return segments;
    }

    const projectName = currentProject.value?.name ?? `项目 #${projectId}`;
    const projectOverviewRoute = `/projects/${projectId}/overview`;

    segments.push({ label: '项目列表', to: '/projects' });

    const tab = resolveProjectTab(routeName);
    const isTaskPlanArea = tab === 'task-plans';

    // 项目概览
    if (routeName === 'project-overview') {
      segments.push({ label: projectName, to: projectOverviewRoute });
      segments.push({ label: '项目概览' });
      return segments;
    }

    // 非任务计划的 tab：项目名 + tab 名
    if (!isTaskPlanArea) {
      segments.push({ label: projectName, to: projectOverviewRoute });
      segments.push({ label: tabLabel(tab) });
      return segments;
    }

    // ---- 任务计划体系 ----
    const executionId = Number(route.params.executionId);
    const planId = activePlanId.value || Number(route.params.planId);

    // 执行详情页（最深层级）
    if (executionId) {
      segments.push({ label: projectName, to: projectOverviewRoute });
      segments.push({ label: '任务计划', to: `/projects/${projectId}/task-plans` });

      const detailPlanId = executionDetail.value?.planId;
      const detailPlanName =
        plans.value.find((p) => p.id === detailPlanId)?.name ?? (detailPlanId ? `计划 #${detailPlanId}` : '未知计划');
      segments.push({
        label: detailPlanName,
        to: detailPlanId ? `/projects/${projectId}/task-plans/${detailPlanId}` : undefined,
      });

      const execName =
        executionDetail.value?.executionName ||
        executionDetail.value?.scenarioName ||
        `执行 #${executionId}`;
      segments.push({ label: execName });
      return segments;
    }

    // 计划详情页
    if (planId) {
      segments.push({ label: projectName, to: projectOverviewRoute });
      segments.push({ label: '任务计划', to: `/projects/${projectId}/task-plans` });
      const planName = activePlan.value?.name ?? `计划 #${planId}`;
      segments.push({ label: planName });
      return segments;
    }

    // 任务计划列表页
    segments.push({ label: projectName, to: projectOverviewRoute });
    segments.push({ label: '任务计划' });
    return segments;
  });

  return { breadcrumbs };
}
