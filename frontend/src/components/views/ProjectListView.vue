<template>
  <section class="hero-band">
    <div>
      <span class="eyebrow">Module 02</span>
      <h1>项目是所有压测资产的入口</h1>
      <p>脚本、任务计划、监控配置、报告都归属于项目。先选择或创建项目，进入后再维护项目内资产。</p>
    </div>
    <div class="hero-actions">
      <a-button @click="resetWorkspace">重置本地视图</a-button>
      <a-button type="primary" size="large" @click="$emit('create')">新建项目</a-button>
    </div>
  </section>

  <section class="metrics-grid">
    <div class="metric">
      <span>活跃项目</span>
      <strong>{{ activeProjectCount }}</strong>
    </div>
    <div class="metric">
      <span>归档项目</span>
      <strong>{{ archivedProjectCount }}</strong>
    </div>
    <div class="metric">
      <span>资产加载</span>
      <strong>按项目</strong>
    </div>
    <div class="metric">
      <span>成员加载</span>
      <strong>按弹窗</strong>
    </div>
  </section>

  <section class="project-home-grid">
    <div class="panel">
      <div class="panel-header">
        <div>
          <h2>项目列表</h2>
          <p>项目只做归档，不做物理删除；已归档项目保留历史资产查看。</p>
        </div>
      </div>

      <div class="filters">
        <a-input v-model:value="projectKeyword" allow-clear placeholder="搜索项目名称、编码、负责人" />
        <a-segmented v-model:value="projectStatusFilter" :options="projectStatusOptions" />
      </div>

      <a-table
        :columns="projectColumns"
        :data-source="filteredProjects"
        :pagination="false"
        :row-key="(record: Project) => record.id"
        :custom-row="projectRowEvents"
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

    <aside class="panel detail-panel">
      <template v-if="selectedProject">
        <div class="detail-heading">
          <div>
            <span class="eyebrow">{{ selectedProject.code }}</span>
            <h2>{{ selectedProject.name }}</h2>
          </div>
          <a-tag :color="selectedProject.status === 'ACTIVE' ? 'success' : 'default'">
            {{ projectStatusText(selectedProject.status) }}
          </a-tag>
        </div>
        <p class="detail-description">{{ selectedProject.description || '暂未填写项目说明。' }}</p>
        <div class="info-list">
          <div>
            <span>负责人</span>
            <strong>{{ selectedProject.ownerUsername }}</strong>
          </div>
          <div>
            <span>成员数</span>
            <strong>按需加载</strong>
          </div>
          <div>
            <span>脚本资产</span>
            <strong>按需加载</strong>
          </div>
          <div>
            <span>创建时间</span>
            <strong>{{ formatDate(selectedProject.createdAt) }}</strong>
          </div>
        </div>
        <div class="detail-actions">
          <a-button type="primary" @click="enterProject(selectedProject)">进入项目工作区</a-button>
          <a-button @click="$emit('members', selectedProject)">维护成员</a-button>
        </div>
      </template>
    </aside>
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
  selectedProject,
  activeProjectCount,
  archivedProjectCount,
  selectProject,
  archiveProject,
  restoreProject,
  resetWorkspace,
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

function projectRowEvents(record: Project) {
  return {
    onClick: () => selectProject(record),
  };
}

onMounted(() => {
  void loadProjects();
});
</script>
