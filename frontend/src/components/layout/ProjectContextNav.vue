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
        <span v-if="!collapsed">{{ shortLabel(option.label) }}</span>
        <span v-else>{{ shortLabel(option.label).slice(0, 1) }}</span>
      </button>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { projectTabOptions } from '../../constants';
import { useNavigation } from '../../composables/useNavigation';
import { useWorkspace } from '../../composables/useWorkspace';

const { activeProjectTab, enterProjectTab, backToProjects } = useNavigation();
const { currentProject } = useWorkspace();
const collapsed = ref(false);

function shortLabel(label: string) {
  return label.replace(/管理|配置|权限/g, '').trim() || label;
}
</script>
