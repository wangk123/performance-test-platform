<template>
  <section class="project-home-grid">
    <div class="page-head">
      <div>
        <h1>项目管理</h1>
        <p>项目只做归档，不做物理删除；已归档项目保留历史资产查看。</p>
      </div>
      <a-button type="primary" @click="$emit('create')">新建项目</a-button>
    </div>
    <div class="panel">
      <div class="filters">
        <a-input v-model:value="projectKeyword" allow-clear placeholder="搜索项目名称、编码、负责人" />
        <a-segmented v-model:value="projectStatusFilter" :options="projectStatusOptions" />
      </div>

      <a-table
        :columns="projectColumns"
        :data-source="filteredProjects"
        :pagination="false"
        :row-key="(record: Project) => record.id"
        :locale="{ emptyText: '没有匹配项目，调整筛选条件或新建项目。' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'asset'">进入后查看</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">{{ projectStatusText(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">{{ formatDate(record.updatedAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-button type="link" @click.stop="enterProject(record)">进入项目</a-button>
            <a-button type="link" @click.stop="$emit('edit', record)">编辑</a-button>
            <a-button type="link" @click.stop="$emit('members', record)">成员</a-button>
            <a-button
              v-if="record.status === 'ACTIVE'"
              type="link"
              danger
              @click.stop="archiveProject(record)"
            >归档</a-button>
            <a-button v-else type="link" @click.stop="restoreProject(record)">恢复</a-button>
          </template>
        </template>
      </a-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import { projectStatusOptions } from '../../constants';
import { formatDate, projectStatusText } from '../../utils/format';
import { useWorkspace } from '../../composables/useWorkspace';
import { useNavigation } from '../../composables/useNavigation';
import type { Project } from '../../types';

defineEmits<{
  (e: 'create'): void;
  (e: 'edit', project: Project): void;
  (e: 'members', project: Project): void;
}>();

const {
  projectKeyword,
  projectStatusFilter,
  filteredProjects,
  archiveProject,
  restoreProject,
  loadProjects,
} = useWorkspace();
const { enterProject } = useNavigation();

const projectColumns: TableColumnsType<Project> = [
  { title: '项目编码', dataIndex: 'code', key: 'code', width: 150 },
  { title: '项目名称', dataIndex: 'name', key: 'name', width: 220 },
  { title: '负责人', dataIndex: 'ownerUsername', key: 'ownerUsername', width: 120 },
  { title: '资产', key: 'asset', width: 150 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 168 },
  { title: '操作', key: 'actions', width: 280, fixed: 'right' },
];

onMounted(() => {
  void loadProjects();
});
</script>
