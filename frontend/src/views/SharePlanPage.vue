<template>
  <main class="share-page">
    <h1>{{ shared?.name ?? '压测计划' }}</h1>
    <p class="share-meta">发布时间：{{ shared?.publishedAt ? new Date(shared.publishedAt).toLocaleString() : '—' }}</p>
    <MdPreview :model-value="shared?.body ?? '加载中…'" language="zh-CN" />
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { getSharedPlanApi } from '../api/plan-doc';

const route = useRoute();
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
.share-page { max-width: 960px; margin: 24px auto; padding: 0 16px; background: #fff; }
.share-meta { color: #5c6b7a; }
</style>
