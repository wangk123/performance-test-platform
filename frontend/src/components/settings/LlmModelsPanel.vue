<template>
  <div class="panel-header">
    <div>
      <h2>模型</h2>
      <p>模型挂在提供商下；可同时勾选 OPENAI / ANTHROPIC。业务选用请使用 modelId。</p>
    </div>
    <a-space>
      <a-select
        v-model:value="filterProviderId"
        allow-clear
        placeholder="按提供商筛选"
        style="width: 220px"
        :options="providerOptions"
        @change="loadModels"
      />
      <a-button type="primary" @click="openCreate">新建模型</a-button>
    </a-space>
  </div>

  <a-table :columns="columns" :data-source="models" :loading="loading" :pagination="false" row-key="id">
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'provider'">
        {{ providerName(record.providerId) }}
      </template>
      <template v-else-if="column.key === 'apiTypes'">
        <a-space>
          <a-tag v-for="type in record.apiTypes ?? [record.apiType]" :key="type">{{ type }}</a-tag>
        </a-space>
      </template>
      <template v-else-if="column.key === 'enabled'">
        <a-tag :color="record.enabled ? 'success' : 'default'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
      </template>
      <template v-else-if="column.key === 'isDefault'">
        <a-tag v-if="record.isDefault" color="processing">默认</a-tag>
      </template>
      <template v-else-if="column.key === 'actions'">
        <a-space>
          <a-button size="small" @click="openEdit(record)">编辑</a-button>
          <a-button size="small" :disabled="record.isDefault" @click="setDefault(record)">设为默认</a-button>
          <a-button size="small" danger @click="removeModel(record)">删除</a-button>
        </a-space>
      </template>
    </template>
  </a-table>

  <a-modal v-model:open="formOpen" :title="editingId ? '编辑模型' : '新建模型'" @ok="submitForm">
    <a-form layout="vertical">
      <a-form-item v-if="!editingId" label="提供商" required>
        <a-select v-model:value="form.providerId" :options="providerOptions" />
      </a-form-item>
      <a-form-item v-if="!editingId" label="模型名称" required>
        <a-input v-model:value="form.modelName" placeholder="deepseek-v4-flash" />
      </a-form-item>
      <a-form-item label="展示名">
        <a-input v-model:value="form.displayName" />
      </a-form-item>
      <a-form-item label="支持协议" required>
        <a-checkbox-group v-model:value="form.apiTypes">
          <a-checkbox value="OPENAI">OPENAI</a-checkbox>
          <a-checkbox value="ANTHROPIC">ANTHROPIC</a-checkbox>
        </a-checkbox-group>
      </a-form-item>
      <a-form-item label="启用">
        <a-switch v-model:checked="form.enabled" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import {
  createLlmModelApi,
  deleteLlmModelApi,
  listLlmModelsApi,
  listLlmProvidersApi,
  setDefaultLlmModelApi,
  updateLlmModelApi,
} from '../../api/llm';
import type { LlmApiType, LlmModel, LlmProvider } from '../../types';

const providers = ref<LlmProvider[]>([]);
const models = ref<LlmModel[]>([]);
const loading = ref(false);
const filterProviderId = ref<number | undefined>();
const formOpen = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({
  providerId: undefined as number | undefined,
  modelName: '',
  displayName: '',
  apiTypes: ['OPENAI'] as LlmApiType[],
  enabled: true,
});

const providerOptions = computed(() =>
  providers.value.map((p) => ({ label: p.name, value: p.id })),
);

const columns: TableColumnsType<LlmModel> = [
  { title: '提供商', key: 'provider', width: 140 },
  { title: '模型名称', dataIndex: 'modelName', key: 'modelName' },
  { title: '展示名', dataIndex: 'displayName', key: 'displayName' },
  { title: '协议', key: 'apiTypes', width: 200 },
  { title: '状态', key: 'enabled', width: 80 },
  { title: '默认', key: 'isDefault', width: 80 },
  { title: '操作', key: 'actions', width: 240 },
];

function providerName(providerId: number) {
  return providers.value.find((p) => p.id === providerId)?.name ?? String(providerId);
}

async function loadProviders() {
  providers.value = await listLlmProvidersApi();
}

async function loadModels() {
  loading.value = true;
  try {
    models.value = await listLlmModelsApi(filterProviderId.value);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载失败');
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, {
    providerId: filterProviderId.value ?? providers.value[0]?.id,
    modelName: '',
    displayName: '',
    apiTypes: ['OPENAI'] as LlmApiType[],
    enabled: true,
  });
  formOpen.value = true;
}

function openEdit(record: LlmModel) {
  editingId.value = record.id;
  Object.assign(form, {
    providerId: record.providerId,
    modelName: record.modelName,
    displayName: record.displayName ?? '',
    apiTypes: [...(record.apiTypes?.length ? record.apiTypes : [record.apiType])],
    enabled: record.enabled,
  });
  formOpen.value = true;
}

async function submitForm() {
  if (!form.apiTypes.length) {
    message.error('至少选择一种协议');
    return;
  }
  try {
    if (editingId.value) {
      await updateLlmModelApi(editingId.value, {
        displayName: form.displayName || undefined,
        apiTypes: form.apiTypes,
        enabled: form.enabled,
      });
    } else {
      if (form.providerId == null) {
        message.error('请选择提供商');
        return;
      }
      await createLlmModelApi({
        providerId: form.providerId,
        modelName: form.modelName,
        displayName: form.displayName || undefined,
        apiTypes: form.apiTypes,
        enabled: form.enabled,
      });
    }
    formOpen.value = false;
    message.success('已保存');
    await loadModels();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败');
  }
}

async function setDefault(record: LlmModel) {
  try {
    await setDefaultLlmModelApi(record.id);
    message.success('已设为默认');
    await loadModels();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '设置失败');
  }
}

function removeModel(record: LlmModel) {
  Modal.confirm({
    title: '删除模型？',
    content: record.modelName,
    okType: 'danger',
    async onOk() {
      await deleteLlmModelApi(record.id);
      message.success('已删除');
      await loadModels();
    },
  });
}

onMounted(async () => {
  await loadProviders();
  await loadModels();
});
</script>
