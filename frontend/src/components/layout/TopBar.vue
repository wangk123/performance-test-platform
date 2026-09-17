<template>
  <header class="topbar">
    <nav class="topbar-breadcrumb">
      <template v-for="(segment, index) in breadcrumbs" :key="index">
        <span v-if="index > 0" class="crumb-sep">/</span>
        <a
          v-if="segment.to"
          class="crumb-seg"
          @click="navigateTo(segment.to)"
        ><HomeOutlined v-if="index === 0" class="crumb-icon" />{{ segment.label }}</a>
        <span v-else class="crumb-seg crumb-current"><HomeOutlined v-if="index === 0" class="crumb-icon" />{{ segment.label }}</span>
      </template>
    </nav>
    <div class="topbar-actions">
      <UserMenu />
    </div>
  </header>
</template>

<script setup lang="ts">
import { HomeOutlined } from '@ant-design/icons-vue';
import { useRouter } from 'vue-router';
import { useBreadcrumb } from '../../composables/useBreadcrumb';
import { useWorkspace } from '../../composables/useWorkspace';
import UserMenu from './UserMenu.vue';

const { exitProjectWorkspace } = useWorkspace();
const { breadcrumbs } = useBreadcrumb();
const router = useRouter();

function navigateTo(to: string) {
  if (!/^\/projects\/\d+/.test(to)) {
    exitProjectWorkspace();
  }
  void router.push(to);
}
</script>
