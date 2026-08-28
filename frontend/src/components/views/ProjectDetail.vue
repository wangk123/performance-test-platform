<template>
  <ProjectOverview
    v-if="activeProjectTab === 'overview'"
    @edit="(p) => $emit('edit', p)"
    @members="(p) => $emit('members', p)"
  />

  <ScriptWorkspace v-else-if="activeProjectTab === 'scripts'" />

  <TaskPlanList v-else-if="activeProjectTab === 'task-plans'" />

  <ProjectMonitoringView v-else-if="activeProjectTab === 'monitoring'" />

  <section v-else-if="activeProjectTab === 'reports'" class="placeholder-grid">
    <div class="page-head">
      <div>
        <h1>报告管理</h1>
        <p>按测试计划生成性能测试报告，支持 HTML 预览与 Word/PDF 导出。</p>
      </div>
    </div>
    <div class="panel">
      <a-empty v-if="!projectPlans.length" description="暂无测试计划，请先创建计划并完成执行" />
      <a-list v-else :data-source="projectPlans" :pagination="false">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta>
              <template #title>{{ item.name }}</template>
              <template #description>{{ item.remark || '无备注' }}</template>
            </a-list-item-meta>
            <template #actions>
              <a-button type="link" @click="openPlanReport(item.id)">查看报告</a-button>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </div>
  </section>

  <section v-else-if="activeProjectTab === 'data'">
    <SeedFactoryView />
  </section>

  <FunctionLibraryView v-else-if="activeProjectTab === 'functions'" />

  <section v-else class="members-page">
    <div class="page-head">
      <div>
        <h1>成员权限</h1>
        <p>成员关系仍是项目级权限前置校验，脚本、执行、报告不重复维护成员。</p>
      </div>
    </div>
    <div class="panel">
      <a-table
        v-if="currentProject"
        :columns="memberColumns"
        :data-source="membersByProject(currentProject.id)"
        :pagination="false"
        :row-key="(record: ProjectMember) => record.username"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'role'">{{ projectRoleText(record.role) }}</template>
        </template>
      </a-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { TableColumnsType } from 'ant-design-vue';
import { projectRoleText } from '../../utils/format';
import { useNavigation } from '../../composables/useNavigation';
import { useWorkspace } from '../../composables/useWorkspace';
import { useTaskPlans } from '../../composables/useTaskPlans';
import type { Project, ProjectMember } from '../../types';
import ProjectOverview from './ProjectOverview.vue';
import ProjectMonitoringView from './ProjectMonitoringView.vue';
import ScriptWorkspace from '../scripts/ScriptWorkspace.vue';
import TaskPlanList from '../task-plans/TaskPlanList.vue';
import FunctionLibraryView from './FunctionLibraryView.vue';
import SeedFactoryView from './SeedFactoryView.vue';

defineEmits<{
  (e: 'edit', project: Project): void;
  (e: 'members', project: Project): void;
}>();

const { activeProjectTab } = useNavigation();
const {
  currentProject,
  membersByProject,
  loadProject,
  loadProjectScripts,
  loadMembers,
  workspaceProjectId,
  selectedProjectId,
} =
  useWorkspace();
const { projectPlans, loadPlans } = useTaskPlans();
const route = useRoute();
const router = useRouter();

function openPlanReport(planId: number) {
  if (!currentProject.value) return;
  router.push(`/projects/${currentProject.value.id}/reports/plans/${planId}`);
}

const memberColumns: TableColumnsType<ProjectMember> = [
  { title: '账号', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
  { title: '项目角色', dataIndex: 'role', key: 'role' },
];

watch(
  () => [route.params.projectId, route.name] as const,
  ([projectId]) => {
    const id = Number(projectId);
    if (!id) {
      return;
    }
    workspaceProjectId.value = id;
    selectedProjectId.value = id;
    void loadProject(id);
    if (['project-overview', 'project-scripts', 'project-task-plans', 'project-task-plan-detail', 'project-scenario-detail', 'project-execution-detail'].includes(String(route.name))) {
      void loadProjectScripts(id);
    }
    if (route.name === 'project-members') {
      void loadMembers(id);
    }
    if (route.name === 'project-reports') {
      void loadPlans();
    }
  },
  { immediate: true },
);
</script>
