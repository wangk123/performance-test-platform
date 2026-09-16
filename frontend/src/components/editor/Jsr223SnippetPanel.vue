<template>
  <aside class="jsr223-snippet-panel">
    <h4>
      内置片段库
      <span v-if="!loading && !loadError" class="snippet-count">GET /api/jsr223-snippets · {{ snippets.length }}</span>
    </h4>

    <a-spin v-if="loading" size="small" class="snippet-state" />
    <div v-else-if="loadError" class="snippet-state snippet-error">
      <span>片段库加载失败：{{ loadError }}</span>
      <a @click="loadSnippets">重试</a>
    </div>
    <div v-else-if="snippetGroups.length === 0" class="snippet-state">暂无内置片段</div>

    <template v-else>
      <div v-for="group in snippetGroups" :key="group.category" class="snippet-group">
        <div class="snippet-group-label">{{ group.category }}</div>
        <div
          v-for="snippet in group.items"
          :key="snippet.key"
          class="snippet-item"
          title="点击插入到脚本光标处"
          @click="props.onInsert(snippet.code)"
        >
          <div class="snippet-name">
            <span>{{ snippet.name }}</span>
            <span class="snippet-insert">插入 →</span>
          </div>
          <div class="snippet-desc">{{ snippet.description }}</div>
          <div v-if="snippet.params.length" class="snippet-keys">
            <template v-for="(param, index) in snippet.params" :key="param">
              <code>{{ `\${__P(${param})}` }}</code><span v-if="index < snippet.params.length - 1"> · </span>
            </template>
          </div>
        </div>
      </div>
      <div class="snippet-footnote">密钥位一律参数化占位（${__P(...)}），不落明文。</div>
    </template>
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { request } from '../../api/http';

type Jsr223Snippet = {
  key: string;
  name: string;
  category: string;
  description: string;
  params: string[];
  code: string;
};

const props = defineProps<{
  onInsert: (code: string) => void;
}>();

const snippets = ref<Jsr223Snippet[]>([]);
const loading = ref(false);
const loadError = ref('');

const snippetGroups = computed(() => {
  const groups: { category: string; items: Jsr223Snippet[] }[] = [];
  snippets.value.forEach((snippet) => {
    const group = groups.find((item) => item.category === snippet.category);
    if (group) {
      group.items.push(snippet);
    } else {
      groups.push({ category: snippet.category, items: [snippet] });
    }
  });
  return groups;
});

onMounted(loadSnippets);

async function loadSnippets() {
  loading.value = true;
  loadError.value = '';
  try {
    snippets.value = await request<Jsr223Snippet[]>('/api/jsr223-snippets');
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '请求失败';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.jsr223-snippet-panel {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-soft);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.snippet-count {
  font-size: 10.5px;
  font-weight: 500;
  font-family: var(--font-data);
  color: var(--muted);
}

.snippet-state {
  font-size: 12px;
  color: var(--muted);
  padding: 8px 2px;
}

.snippet-error {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.snippet-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.snippet-group-label {
  font-size: 10.5px;
  font-weight: 700;
  color: var(--muted);
  letter-spacing: 0.5px;
}

.snippet-item {
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--surface);
  padding: 8px 11px;
  cursor: pointer;
  transition: border-color 0.12s;
}

.snippet-item:hover {
  border-color: var(--primary);
}

.snippet-name {
  font-size: 12.5px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.snippet-insert {
  margin-left: auto;
  color: var(--primary);
  font-size: 11px;
  font-weight: 600;
  opacity: 0;
  transition: opacity 0.12s;
}

.snippet-item:hover .snippet-insert {
  opacity: 1;
}

.snippet-desc {
  font-size: 11.5px;
  color: var(--muted);
  margin-top: 2px;
  line-height: 1.5;
}

.snippet-keys {
  font-size: 11px;
  margin-top: 4px;
  color: var(--muted);
}

.snippet-keys code {
  font-size: 10.5px;
  font-weight: 600;
  font-family: var(--font-data);
  color: var(--primary);
  background: var(--primary-bg);
  border-radius: 4px;
  padding: 0 5px;
}

.snippet-footnote {
  font-size: 11px;
  color: var(--muted);
  border-top: 1px solid var(--border);
  padding-top: 8px;
}
</style>
