<template>
  <a-dropdown v-if="currentUser" v-model:open="open" trigger="click" placement="bottomRight">
    <button class="user-menu-trigger" :class="{ 'is-open': open }" type="button">
      <a-avatar class="user-avatar" :size="24">{{ userInitial }}</a-avatar>
      <span class="user-menu-name">{{ currentUser.displayName }}</span>
      <DownOutlined class="user-menu-caret" />
    </button>
    <template #overlay>
      <div class="user-menu-panel" @click.stop>
        <div class="user-menu-head">
          <a-avatar class="user-avatar" :size="36">{{ userInitial }}</a-avatar>
          <div class="user-menu-id">
            <strong>{{ currentUser.displayName }}</strong>
            <span>{{ currentUser.username }} · {{ roleLabel }}</span>
          </div>
        </div>
        <div class="user-menu-sep" />
        <div class="user-menu-theme">
          <span>主题</span>
          <a-segmented v-model:value="themeMode" :options="themeModeOptions" size="small" />
        </div>
        <div class="user-menu-sep" />
        <button class="user-menu-logout" type="button" @click="handleLogout">
          <LogoutOutlined />
          <span>退出登录</span>
        </button>
      </div>
    </template>
  </a-dropdown>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { DownOutlined, LogoutOutlined } from '@ant-design/icons-vue';
import { useAuth } from '../../composables/useAuth';
import { useTheme } from '../../composables/useTheme';
import { useWorkspace } from '../../composables/useWorkspace';

const { currentUser } = useAuth();
const { fullLogout } = useWorkspace();
const { themeMode, themeModeOptions } = useTheme();

const open = ref(false);

const userInitial = computed(() => currentUser.value?.displayName?.slice(0, 1).toUpperCase() ?? 'U');
const ROLE_LABELS: Record<string, string> = { ADMIN: '平台管理员' };
const roleLabel = computed(() => {
  const labels = (currentUser.value?.roles ?? []).filter(Boolean).map((role) => ROLE_LABELS[role] ?? role);
  return labels.length > 0 ? labels.join(' / ') : '平台管理员';
});

function handleLogout() {
  open.value = false;
  fullLogout();
}
</script>
