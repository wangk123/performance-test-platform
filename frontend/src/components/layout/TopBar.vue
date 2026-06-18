<template>
  <a-layout-header class="topbar">
    <div>
      <div class="eyebrow">Phase 2 Prototype</div>
      <div class="page-title">{{ pageTitle }}</div>
    </div>
    <div class="topbar-actions">
      <a-tag color="success">Backend API</a-tag>
      <a-tag v-if="currentProject" color="success">{{ currentProject.code }}</a-tag>
      <a-dropdown v-if="currentUser" trigger="click">
        <button class="user-menu-trigger" type="button">
          <a-avatar class="user-avatar">{{ userInitial }}</a-avatar>
          <span>{{ currentUser.displayName }}</span>
        </button>
        <template #overlay>
          <a-menu>
            <a-menu-item key="profile" disabled>
              <strong>{{ currentUser.displayName }}</strong>
              <small>{{ currentUser.username }} · 平台管理员</small>
            </a-menu-item>
            <a-menu-divider />
            <a-menu-item key="theme">
              <div class="user-menu-section" @click.stop>
                <span>主题</span>
                <a-segmented v-model:value="themeMode" :options="themeModeOptions" />
              </div>
            </a-menu-item>
            <a-menu-item key="settings" disabled>用户设置（Mock）</a-menu-item>
            <a-menu-item key="notifications" disabled>消息中心（Mock）</a-menu-item>
            <a-menu-divider />
            <a-menu-item key="logout" danger @click="fullLogout">退出登录</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAuth } from '../../composables/useAuth';
import { useNavigation } from '../../composables/useNavigation';
import { useTheme } from '../../composables/useTheme';
import { useWorkspace } from '../../composables/useWorkspace';

const { currentUser } = useAuth();
const { currentProject, fullLogout } = useWorkspace();
const { pageTitle } = useNavigation();
const { themeMode, themeModeOptions } = useTheme();

const userInitial = computed(() => currentUser.value?.displayName?.slice(0, 1).toUpperCase() ?? 'U');
</script>
