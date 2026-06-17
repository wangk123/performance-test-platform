<template>
  <a-layout-header class="topbar">
    <div>
      <div class="eyebrow">Phase 2 Prototype</div>
      <div class="page-title">{{ pageTitle }}</div>
    </div>
    <div class="topbar-actions">
      <a-segmented v-model:value="themeMode" class="theme-switch" :options="themeModeOptions" />
      <a-tag color="success">Backend API</a-tag>
      <a-tag v-if="currentProject" color="success">{{ currentProject.code }}</a-tag>
      <a-tag v-if="currentUser" color="success">{{ currentUser.displayName }}</a-tag>
      <a-button v-if="currentUser" @click="fullLogout">退出</a-button>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { useAuth } from '../../composables/useAuth';
import { useNavigation } from '../../composables/useNavigation';
import { useTheme } from '../../composables/useTheme';
import { useWorkspace } from '../../composables/useWorkspace';

const { currentUser } = useAuth();
const { currentProject, fullLogout } = useWorkspace();
const { pageTitle } = useNavigation();
const { themeMode, themeModeOptions } = useTheme();
</script>
