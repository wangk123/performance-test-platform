<template>
  <div class="panel-header">
    <div>
      <h2>模型提供商</h2>
      <p>配置平台级 BaseUrl / API Key；Anthropic BaseUrl 选填，未填时回退默认 BaseUrl。</p>
    </div>
    <a-button type="primary" @click="openCreate">新建提供商</a-button>
  </div>

  <a-table :columns="columns" :data-source="providers" :loading="loading" :pagination="false" row-key="id">
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'enabled'">
        <a-tag :color="record.enabled ? 'success' : 'default'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
      </template>
      <template v-else-if="column.key === 'apiKey'">
        {{ record.apiKeyConfigured ? '已配置' : '未配置' }}
      </template>
      <template v-else-if="column.key === 'actions'">
        <a-space>
          <a-button size="small" @click="openEdit(record)">编辑</a-button>
          <a-button size="small" @click="fetchModels(record)">获取模型</a-button>
          <a-button size="small" @click="testProvider(record)">连通性测试</a-button>
          <a-button size="small" danger @click="removeProvider(record)">删除</a-button>
        </a-space>
      </template>
    </template>
  </a-table>

  <a-modal v-model:open="formOpen" :title="editingId ? '编辑提供商' : '新建提供商'" @ok="submitForm">
    <a-form layout="vertical">
      <a-form-item label="名称" required>
        <a-input v-model:value="form.name" />
      </a-form-item>
      <a-form-item label="BaseUrl" required>
        <a-input v-model:value="form.baseUrl" placeholder="https://api.deepseek.com/v1" />
      </a-form-item>
      <a-form-item label="Anthropic BaseUrl（选填）">
        <a-input v-model:value="form.baseUrlAnthropic" placeholder="未填则与默认 BaseUrl 相同" />
      </a-form-item>
      <a-form-item :label="editingId ? 'API Key（留空不修改）' : 'API Key'" :required="!editingId">
        <a-input-password v-model:value="form.apiKey" />
      </a-form-item>
      <a-form-item label="启用">
        <a-switch v-model:checked="form.enabled" />
      </a-form-item>
      <a-form-item label="默认落库请求/响应正文">
        <a-switch v-model:checked="form.storeBodyDefault" />
      </a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="importOpen" title="导入模型" @ok="confirmImport">
    <a-form layout="vertical">
      <a-form-item label="协议类型">
        <a-select v-model:value="importApiType">
          <a-select-option value="OPENAI">OPENAI</a-select-option>
          <a-select-option value="ANTHROPIC">ANTHROPIC</a-select-option>
        </a-select>
      </a-form-item>
      <a-checkbox-group v-model:value="selectedModels" :options="candidateModels.map((m) => ({ label: m, value: m }))" />
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import {
  createLlmProviderApi,
  deleteLlmProviderApi,
  fetchLlmModelsApi,
  importLlmModelsApi,
  listLlmProvidersApi,
  testLlmProviderApi,
  updateLlmProviderApi,
} from '../../api/llm';
import type { LlmApiType, LlmProvider } from '../../types';

const providers = ref<LlmProvider[]>([]);
const loading = ref(false);
const formOpen = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({
  name: '',
  baseUrl: '',
  baseUrlAnthropic: '',
  apiKey: '',
  enabled: true,
  storeBodyDefault: false,
});

const importOpen = ref(false);
const importProviderId = ref<number | null>(null);
const importApiType = ref<LlmApiType>('OPENAI');
const candidateModels = ref<string[]>([]);
const selectedModels = ref<string[]>([]);

const columns: TableColumnsType<LlmProvider> = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: 'BaseUrl', dataIndex: 'baseUrl', key: 'baseUrl', ellipsis: true },
  { title: 'Anthropic Url', dataIndex: 'baseUrlAnthropic', key: 'baseUrlAnthropic', ellipsis: true },
  { title: 'API Key', key: 'apiKey', width: 90 },
  { title: '状态', key: 'enabled', width: 80 },
  { title: '操作', key: 'actions', width: 340 },
];

async function load() {
  loading.value = true;
  try {
    providers.value = await listLlmProvidersApi();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载失败');
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, {
    name: '',
    baseUrl: '',
    baseUrlAnthropic: '',
    apiKey: '',
    enabled: true,
    storeBodyDefault: false,
  });
  formOpen.value = true;
}

function openEdit(record: LlmProvider) {
  editingId.value = record.id;
  Object.assign(form, {
    name: record.name,
    baseUrl: record.baseUrl,
    baseUrlAnthropic: record.baseUrlAnthropic ?? '',
    apiKey: '',
    enabled: record.enabled,
    storeBodyDefault: record.storeBodyDefault,
  });
  formOpen.value = true;
}

async function submitForm() {
  try {
    if (editingId.value) {
      await updateLlmProviderApi(editingId.value, {
        name: form.name,
        baseUrl: form.baseUrl,
        baseUrlAnthropic: form.baseUrlAnthropic || null,
        apiKey: form.apiKey || null,
        enabled: form.enabled,
        storeBodyDefault: form.storeBodyDefault,
      });
    } else {
      await createLlmProviderApi({
        name: form.name,
        baseUrl: form.baseUrl,
        baseUrlAnthropic: form.baseUrlAnthropic || null,
        apiKey: form.apiKey,
        enabled: form.enabled,
        storeBodyDefault: form.storeBodyDefault,
      });
    }
    formOpen.value = false;
    message.success('已保存');
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败');
  }
}

async function fetchModels(record: LlmProvider) {
  try {
    const result = await fetchLlmModelsApi(record.id, importApiType.value);
    importProviderId.value = record.id;
    candidateModels.value = result.models;
    selectedModels.value = [...result.models];
    importApiType.value = result.apiType;
    importOpen.value = true;
  } catch (e) {
    message.error(e instanceof Error ? e.message : '获取模型失败');
  }
}

async function confirmImport() {
  if (importProviderId.value == null) return;
  try {
    const imported = await importLlmModelsApi(
      importProviderId.value,
      importApiType.value,
      selectedModels.value.map((modelName) => ({ modelName })),
    );
    importOpen.value = false;
    message.success(`已导入 ${imported.length} 个模型`);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '导入失败');
  }
}

async function testProvider(record: LlmProvider) {
  try {
    const result = await testLlmProviderApi(record.id);
    if (result.success) {
      message.success(`连通成功，耗时 ${result.latencyMs}ms`);
    } else {
      message.error(result.errorMessage || '连通失败');
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '连通失败');
  }
}

async function removeProvider(record: LlmProvider) {
  try {
    await deleteLlmProviderApi(record.id, false);
    message.success('已删除');
    await load();
  } catch (e) {
    const err = e as Error & { status?: number };
    if (err.status === 409) {
      Modal.confirm({
        title: '确认级联删除？',
        content: `${err.message}。确定后将同步删除该提供商下全部模型。`,
        okType: 'danger',
        async onOk() {
          await deleteLlmProviderApi(record.id, true);
          message.success('已级联删除');
          await load();
        },
      });
      return;
    }
    message.error(err.message || '删除失败');
  }
}

onMounted(() => {
  void load();
});
</script>
