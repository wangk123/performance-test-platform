<template>
  <aside class="project-ctx" :class="{ collapsed: collapsed }" aria-label="项目导航">
    <div class="project-ctx-head">
      <button class="project-ctx-back" type="button" @click="backToProjects">← 项目</button>
      <strong v-if="!collapsed" class="project-ctx-name" :title="currentProject?.name">
        {{ currentProject?.name }}
      </strong>
      <button
        class="project-ctx-toggle"
        type="button"
        :title="collapsed ? '展开导航' : '折叠导航'"
        @click="collapsed = !collapsed"
      >
        {{ collapsed ? '»' : '«' }}
      </button>
    </div>

    <nav class="project-ctx-nav">
      <button
        v-for="option in projectTabOptions"
        :key="option.value"
        class="project-ctx-link"
        :class="{ active: activeProjectTab === option.value }"
        type="button"
        :title="option.label"
        @click="enterProjectTab(option.value)"
      >
        <component :is="tabIcons[option.value]" class="project-ctx-icon" />
        <span v-if="!collapsed">{{ shortLabel(option.label) }}</span>
      </button>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { ref, type Component } from 'vue';
import {
  AppstoreOutlined,
  BarChartOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  FunctionOutlined,
  LineChartOutlined,
  ProfileOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue';
import { projectTabOptions } from '../../constants';
import type { ProjectTab } from '../../types';
import { useNavigation } from '../../composables/useNavigation';
import { useWorkspace } from '../../composables/useWorkspace';

const { activeProjectTab, enterProjectTab, backToProjects } = useNavigation();
const { currentProject } = useWorkspace();
const collapsed = ref(false);

/** 项目导航图标（折叠态仅显示图标，替代原首字占位）。 */
const tabIcons: Record<ProjectTab, Component> = {
  overview: AppstoreOutlined,
  scripts: FileTextOutlined,
  'task-plans': ProfileOutlined,
  monitoring: LineChartOutlined,
  reports: BarChartOutlined,
  data: DatabaseOutlined,
  functions: FunctionOutlined,
  members: TeamOutlined,
};

function shortLabel(label: string) {
  return label.replace(/管理|配置|权限/g, '').trim() || label;
}
</script>
