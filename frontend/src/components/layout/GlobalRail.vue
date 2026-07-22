<template>
  <aside class="global-rail" aria-label="全局导航">
    <div class="global-rail-brand" title="性能测试平台">PT</div>

    <button
      class="global-rail-btn"
      :class="{ active: activeMainNav === 'home' }"
      type="button"
      title="首页"
      @click="selectMainNav('home')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1z"/></svg>
    </button>
    <button
      class="global-rail-btn"
      :class="{ active: activeMainNav === 'projects' }"
      type="button"
      title="项目管理"
      @click="selectMainNav('projects')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M9 20V9"/></svg>
    </button>
    <button
      class="global-rail-btn"
      :class="{ active: activeMainNav === 'executionNodes' }"
      type="button"
      title="执行器配置"
      @click="selectMainNav('executionNodes')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="6" rx="1"/><rect x="3" y="14" width="18" height="6" rx="1"/></svg>
    </button>
    <button
      class="global-rail-btn"
      :class="{ active: activeMainNav === 'settings' }"
      type="button"
      title="系统配置"
      @click="selectMainNav('settings')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M12 3v2M12 19v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M3 12h2M19 12h2"/></svg>
    </button>
    <button
      class="global-rail-btn"
      :class="{ active: activeMainNav === 'llmConfig' }"
      type="button"
      title="模型配置"
      @click="selectMainNav('llmConfig')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 4 7.5v9L12 21l8-4.5v-9L12 3z"/><path d="M12 12 4 7.5M12 12l8-4.5M12 12v9"/></svg>
    </button>
  </aside>
</template>

<script setup lang="ts">
import { useNavigation } from '../../composables/useNavigation';

const { activeMainNav, selectMainNav } = useNavigation();
</script>
