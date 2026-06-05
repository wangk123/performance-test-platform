<template>
  <ProjectOverview
    v-if="activeProjectTab === 'overview'"
    @edit="(p) => $emit('edit', p)"
    @members="(p) => $emit('members', p)"
  />

  <ScriptWorkspace v-else-if="activeProjectTab === 'scripts'" />

  <TaskScheduleView v-else-if="activeProjectTab === 'tasks'" />

  <section v-else-if="activeProjectTab === 'monitoring'" class="placeholder-grid">
    <div class="panel">
      <h2>监控配置</h2>
      <p class="detail-description">监控目标从脚本解析结果和项目环境中汇总，执行任务可选择绑定。</p>
      <div class="monitor-grid">
        <div v-for="monitor in currentProjectMonitors" :key="monitor.target">
          <strong>{{ monitor.target }}</strong>
          <span>{{ monitor.metrics.join(' / ') }}</span>
        </div>
      </div>
    </div>
  </section>

  <section v-else-if="activeProjectTab === 'reports'" class="placeholder-grid">
    <div class="panel">
      <h2>报告管理</h2>
      <p class="detail-description">报告属于当前项目，后续展示执行结论、趋势和瓶颈定位。</p>
      <div class="report-list">
        <div v-for="report in reportMocks" :key="report.name">
          <strong>{{ report.name }}</strong>
          <span>{{ report.time }} · {{ report.result }}</span>
        </div>
      </div>
    </div>
  </section>

  <section v-else class="placeholder-grid">
    <div class="panel">
      <h2>成员权限</h2>
      <p class="detail-description">成员关系仍是项目级权限前置校验，脚本、执行、报告不重复维护成员。</p>
      <el-table v-if="currentProject" :data="membersByProject(currentProject.id)" border stripe>
        <el-table-column prop="username" label="账号" />
        <el-table-column prop="displayName" label="姓名" />
        <el-table-column prop="role" label="项目角色">
          <template #default="{ row }">{{ projectRoleText(row.role) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { projectRoleText } from '../../utils/format';
import { useNavigation } from '../../composables/useNavigation';
import { useWorkspace } from '../../composables/useWorkspace';
import type { Project } from '../../types';
import ProjectOverview from './ProjectOverview.vue';
import ScriptWorkspace from '../scripts/ScriptWorkspace.vue';
import TaskScheduleView from '../tasks/TaskScheduleView.vue';

defineEmits<{
  (e: 'edit', project: Project): void;
  (e: 'members', project: Project): void;
}>();

const { activeProjectTab } = useNavigation();
const { currentProject, currentProjectMonitors, reportMocks, membersByProject } = useWorkspace();
</script>
