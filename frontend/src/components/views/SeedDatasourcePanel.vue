<template>
  <div>
    <div class="tab-toolbar">
      <a-button type="primary" @click="openCreate">新增数据源</a-button>
    </div>
    <a-table :columns="columns" :data-source="datasources" :pagination="false" row-key="id" :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'conn'">
          {{ record.host }}:{{ record.port }}/{{ record.databaseName }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a-button size="small" @click="testDs(record.id)">测连</a-button>
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-button size="small" danger @click="remove(record)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑数据源' : '新增数据源'"
      :confirm-loading="saving"
      destroy-on-close
      @ok="save"
    >
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="如 订单测库" />
        </a-form-item>
        <a-form-item label="Host" required>
          <a-input v-model:value="form.host" placeholder="127.0.0.1" />
        </a-form-item>
        <a-form-item label="Port" required>
          <a-input-number v-model:value="form.port" :min="1" :max="65535" style="width: 100%" />
        </a-form-item>
        <a-form-item label="Database" required>
          <a-input v-model:value="form.databaseName" />
        </a-form-item>
        <a-form-item label="用户名" required>
          <a-input v-model:value="form.username" />
        </a-form-item>
        <a-form-item :label="editingId ? '密码（留空不修改）' : '密码'" :required="!editingId">
          <a-input-password v-model:value="form.password" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import { Modal, message } from 'ant-design-vue';
import { useWorkspace } from '../../composables/useWorkspace';
import {
  createSeedDatasource,
  deleteSeedDatasource,
  listSeedDatasources,
  testSeedDatasource,
  updateSeedDatasource,
  type SeedDatasource,
} from '../../api/seed';

const emit = defineEmits<{ changed: [items: SeedDatasource[]] }>();

const { currentProject } = useWorkspace();
const loading = ref(false);
const saving = ref(false);
const datasources = ref<SeedDatasource[]>([]);
const modalOpen = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({
  name: '',
  host: '',
  port: 3306,
  databaseName: '',
  username: '',
  password: '',
});

const columns: TableColumnsType<SeedDatasource> = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '连接', key: 'conn' },
  { title: '用户', dataIndex: 'username', key: 'username' },
  { title: '操作', key: 'actions', width: 220 },
];

onMounted(() => {
  void load();
});

async function load() {
  const projectId = currentProject.value?.id;
  if (!projectId) return;
  loading.value = true;
  try {
    datasources.value = await listSeedDatasources(projectId);
    emit('changed', datasources.value);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载失败');
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, { name: '', host: '', port: 3306, databaseName: '', username: '', password: '' });
  modalOpen.value = true;
}

function openEdit(record: SeedDatasource) {
  editingId.value = record.id;
  Object.assign(form, {
    name: record.name,
    host: record.host,
    port: record.port,
    databaseName: record.databaseName,
    username: record.username,
    password: '',
  });
  modalOpen.value = true;
}

function requireText(value: string | undefined, label: string): string {
  if (value == null || !String(value).trim()) throw new Error(`${label}不能为空`);
  return String(value).trim();
}

async function save() {
  const projectId = currentProject.value?.id;
  if (!projectId) return Promise.reject();
  try {
    const name = requireText(form.name, '名称');
    const host = requireText(form.host, 'Host');
    const databaseName = requireText(form.databaseName, 'Database');
    const username = requireText(form.username, '用户名');
    if (form.port == null || form.port < 1) throw new Error('Port 不能为空');
    if (!editingId.value) requireText(form.password, '密码');
    saving.value = true;
    const body = {
      name,
      host,
      port: form.port,
      databaseName,
      username,
      password: form.password?.trim() || null,
    };
    if (editingId.value) await updateSeedDatasource(projectId, editingId.value, body);
    else await createSeedDatasource(projectId, body);
    message.success('已保存');
    modalOpen.value = false;
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败');
    return Promise.reject();
  } finally {
    saving.value = false;
  }
}

function remove(record: SeedDatasource) {
  const projectId = currentProject.value?.id;
  if (!projectId) return;
  Modal.confirm({
    title: `删除数据源「${record.name}」？`,
    content: '删除后不可恢复。',
    okType: 'danger',
    async onOk() {
      try {
        await deleteSeedDatasource(projectId, record.id);
        message.success('已删除');
        await load();
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败');
        return Promise.reject();
      }
    },
  });
}

async function testDs(id: number) {
  const projectId = currentProject.value?.id;
  if (!projectId) return;
  try {
    const result = await testSeedDatasource(projectId, id);
    if (result.ok) message.success('连接成功');
    else message.error(result.message);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '测连失败');
  }
}
</script>

<style scoped>
.tab-toolbar { margin-bottom: 12px; }
</style>
