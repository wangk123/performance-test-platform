<template>
  <main class="share-page">
    <div class="share-card">
      <h1>{{ shared?.name ?? '压测计划' }}</h1>
      <p class="share-meta">发布时间：{{ shared?.publishedAt ? new Date(shared.publishedAt).toLocaleString() : '—' }}</p>
      <MdPreview class="plan-md" :model-value="shared?.body ?? '加载中…'" :theme="mdTheme" language="zh-CN" />
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { useTheme } from '../composables/useTheme';
import { getSharedPlanApi } from '../api/plan-doc';

const route = useRoute();
const { themeMode } = useTheme();
const mdTheme = computed(() => (themeMode.value === 'dark' ? 'dark' : 'light'));
const shared = ref<{ name: string; body: string | null; publishedAt: string | null } | null>(null);

onMounted(async () => {
  try {
    shared.value = await getSharedPlanApi(String(route.params.token));
  } catch {
    shared.value = { name: '分享链接不存在或已失效', body: '', publishedAt: null };
  }
});
</script>

<style scoped>
.share-page {
  max-width: 960px;
  margin: 24px auto;
  padding: 0 16px;
}

.share-card {
  padding: 24px 28px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
}

.share-card h1 {
  margin: 0;
  color: var(--ink);
  font-size: 20px;
  font-weight: 700;
}

.share-meta {
  margin: 6px 0 16px;
  color: var(--muted);
  font-size: 12px;
}
</style>
