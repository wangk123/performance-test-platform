<template>
  <header class="topbar">
    <nav class="topbar-breadcrumb">
      <template v-for="(segment, index) in breadcrumbs" :key="index">
        <span v-if="index > 0" class="crumb-sep">/</span>
        <a
          v-if="segment.to"
          class="crumb-seg"
          @click="navigateTo(segment.to)"
        >{{ segment.label }}</a>
        <span v-else class="crumb-seg crumb-current">{{ segment.label }}</span>
      </template>
    </nav>
    <div class="topbar-actions">
      <a-dropdown v-if="currentUser" trigger="click">
        <button class="user-menu-trigger" type="button">
          <a-avatar class="user-avatar" :size="26">{{ userInitial }}</a-avatar>
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
            <a-menu-divider />
            <a-menu-item key="logout" danger @click="fullLogout">退出登录</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAuth } from '../../composables/useAuth';
import { useRouter } from 'vue-router';
import { useBreadcrumb } from '../../composables/useBreadcrumb';
import { useTheme } from '../../composables/useTheme';
import { useWorkspace } from '../../composables/useWorkspace';

const { currentUser } = useAuth();
const { exitProjectWorkspace, fullLogout } = useWorkspace();
const { breadcrumbs } = useBreadcrumb();
const { themeMode, themeModeOptions } = useTheme();
const router = useRouter();

function navigateTo(to: string) {
  if (!/^\/projects\/\d+/.test(to)) {
    exitProjectWorkspace();
  }
  void router.push(to);
}

const userInitial = computed(() => currentUser.value?.displayName?.slice(0, 1).toUpperCase() ?? 'U');
</script>
