<template>
  <section class="hero-band">
    <div>
      <span class="eyebrow">Module 02</span>
      <h1>项目是所有压测资产的入口</h1>
      <p>脚本、任务计划、监控配置、报告都归属于项目。先选择或创建项目，进入后再维护项目内资产。</p>
    </div>
    <div class="hero-actions">
      <el-button @click="resetWorkspace">重置本地视图</el-button>
      <el-button type="primary" size="large" @click="$emit('create')">新建项目</el-button>
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
      <span>脚本资产</span>
      <strong>{{ scriptAssetTotal }}</strong>
    </div>
    <div class="metric">
      <span>项目成员</span>
      <strong>{{ memberTotal }}</strong>
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
        <el-input v-model="projectKeyword" clearable placeholder="搜索项目名称、编码、负责人" />
        <el-segmented v-model="projectStatusFilter" :options="projectStatusOptions" />
      </div>

      <el-table
        :data="filteredProjects"
        border
        stripe
        highlight-current-row
        @row-click="selectProject"
      >
        <el-table-column prop="code" label="项目编码" min-width="150" />
        <el-table-column prop="name" label="项目名称" min-width="220" />
        <el-table-column prop="ownerUsername" label="负责人" width="120" />
        <el-table-column label="资产" width="150">
          <template #default="{ row }">{{ scriptsByProject(row.id).length }} 个脚本</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ projectStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="168">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="enterProject(row)">进入项目</el-button>
            <el-button link type="primary" @click.stop="$emit('edit', row)">编辑</el-button>
            <el-button link type="primary" @click.stop="$emit('members', row)">成员</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              link
              type="warning"
              @click.stop="archiveProject(row)"
            >归档</el-button>
            <el-button v-else link type="success" @click.stop="restoreProject(row)">恢复</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-state">没有匹配项目，调整筛选条件或新建项目。</div>
        </template>
      </el-table>
    </div>

    <aside class="panel detail-panel">
      <template v-if="selectedProject">
        <div class="detail-heading">
          <div>
            <span class="eyebrow">{{ selectedProject.code }}</span>
            <h2>{{ selectedProject.name }}</h2>
          </div>
          <el-tag :type="selectedProject.status === 'ACTIVE' ? 'success' : 'info'">
            {{ projectStatusText(selectedProject.status) }}
          </el-tag>
        </div>
        <p class="detail-description">{{ selectedProject.description || '暂未填写项目说明。' }}</p>
        <div class="info-list">
          <div>
            <span>负责人</span>
            <strong>{{ selectedProject.ownerUsername }}</strong>
          </div>
          <div>
            <span>成员数</span>
            <strong>{{ membersByProject(selectedProject.id).length }}</strong>
          </div>
          <div>
            <span>脚本资产</span>
            <strong>{{ scriptsByProject(selectedProject.id).length }}</strong>
          </div>
          <div>
            <span>创建时间</span>
            <strong>{{ formatDate(selectedProject.createdAt) }}</strong>
          </div>
        </div>
        <div class="detail-actions">
          <el-button type="primary" @click="enterProject(selectedProject)">进入项目工作区</el-button>
          <el-button @click="$emit('members', selectedProject)">维护成员</el-button>
        </div>
      </template>
    </aside>
  </section>
</template>

<script setup lang="ts">
import { projectStatusOptions } from '../../constants';
import { formatDate, projectStatusText } from '../../utils/format';
import { useWorkspace } from '../../composables/useWorkspace';
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
  scriptAssetTotal,
  memberTotal,
  selectProject,
  enterProject,
  archiveProject,
  restoreProject,
  resetWorkspace,
  membersByProject,
  scriptsByProject,
} = useWorkspace();
</script>
