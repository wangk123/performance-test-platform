<template>
  <div class="panel-header">
    <div>
      <h2>调用记录</h2>
      <p>Gateway 调用审计；正文仅在开启落库时可见。</p>
    </div>
    <a-space>
      <a-select
        v-model:value="filters.status"
        allow-clear
        placeholder="状态"
        style="width: 120px"
        :options="[
          { label: 'SUCCESS', value: 'SUCCESS' },
          { label: 'FAILED', value: 'FAILED' },
        ]"
        @change="load"
      />
      <a-select
        v-model:value="filters.scene"
        allow-clear
        placeholder="场景"
        style="width: 180px"
        :options="[{ label: 'TEST_CONNECTION', value: 'TEST_CONNECTION' }]"
        @change="load"
      />
      <a-button @click="load">刷新</a-button>
    </a-space>
  </div>

  <a-table
    :columns="columns"
    :data-source="page.content"
    :loading="loading"
    :pagination="{
      current: page.page + 1,
      pageSize: page.size,
      total: page.totalElements,
      onChange: onPageChange,
    }"
    row-key="id"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'status'">
        <a-tag :color="record.status === 'SUCCESS' ? 'success' : 'error'">{{ record.status }}</a-tag>
      </template>
      <template v-else-if="column.key === 'body'">
        <a-button v-if="record.requestBody || record.responseBody" size="small" type="link" @click="showBody(record)">
          查看
        </a-button>
        <span v-else>—</span>
      </template>
    </template>
  </a-table>

  <a-modal v-model:open="bodyOpen" title="调用正文" :footer="null" width="800px">
    <div class="llm-format-bar">
      <span>预览格式</span>
      <a-radio-group v-model:value="formatMode" button-style="solid" size="small">
        <a-radio-button value="auto">自动</a-radio-button>
        <a-radio-button value="json">JSON</a-radio-button>
        <a-radio-button value="markdown">Markdown</a-radio-button>
      </a-radio-group>
    </div>
    <h4>Request <small v-if="requestPreview.mode !== 'raw'">({{ requestPreview.mode }})</small></h4>
    <pre class="llm-body" :class="{ 'llm-body-md': requestPreview.mode === 'markdown' }">{{ requestPreview.text }}</pre>
    <h4>Response <small v-if="responsePreview.mode !== 'raw'">({{ responsePreview.mode }})</small></h4>
    <pre class="llm-body" :class="{ 'llm-body-md': responsePreview.mode === 'markdown' }">{{ responsePreview.text }}</pre>
    <h4 v-if="active?.errorMessage">Error</h4>
    <pre v-if="active?.errorMessage" class="llm-body">{{ active.errorMessage }}</pre>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import { listLlmCallRecordsApi } from '../../api/llm';
import type { LlmCallRecord, LlmCallRecordPage, LlmCallScene, LlmCallStatus } from '../../types';
import { formatLlmBody, type LlmBodyFormatMode } from '../../utils/llm-body-preview';

const loading = ref(false);
const page = ref<LlmCallRecordPage>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
const filters = reactive<{ status?: LlmCallStatus; scene?: LlmCallScene }>({});
const bodyOpen = ref(false);
const active = ref<LlmCallRecord | null>(null);
const formatMode = ref<LlmBodyFormatMode>('auto');

const requestPreview = computed(() => formatLlmBody(active.value?.requestBody, formatMode.value));
const responsePreview = computed(() => formatLlmBody(active.value?.responseBody, formatMode.value));

const columns: TableColumnsType<LlmCallRecord> = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '提供商', dataIndex: 'providerNameSnapshot', key: 'provider' },
  { title: '模型', dataIndex: 'modelNameSnapshot', key: 'model' },
  { title: '协议', dataIndex: 'apiType', key: 'apiType', width: 110 },
  { title: '场景', dataIndex: 'scene', key: 'scene', width: 150 },
  { title: '状态', key: 'status', width: 100 },
  { title: '耗时(ms)', dataIndex: 'latencyMs', key: 'latencyMs', width: 90 },
  { title: '正文', key: 'body', width: 80 },
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 190 },
];

async function load() {
  loading.value = true;
  try {
    page.value = await listLlmCallRecordsApi({
      status: filters.status,
      scene: filters.scene,
      page: page.value.page,
      size: page.value.size,
    });
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载失败');
  } finally {
    loading.value = false;
  }
}

function onPageChange(current: number, pageSize: number) {
  page.value.page = current - 1;
  page.value.size = pageSize;
  void load();
}

function showBody(record: LlmCallRecord) {
  active.value = record;
  formatMode.value = 'auto';
  bodyOpen.value = true;
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.llm-format-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.llm-body {
  max-height: 240px;
  overflow: auto;
  background: #f6f7f9;
  padding: 12px;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
}

.llm-body-md {
  font-family: ui-sans-serif, system-ui, -apple-system, sans-serif;
  font-size: 13px;
  line-height: 1.55;
}

h4 small {
  margin-left: 6px;
  font-weight: 400;
  opacity: 0.65;
}
</style>
