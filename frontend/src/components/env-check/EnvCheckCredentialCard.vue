<template>
  <section class="ec-card">
    <header class="ec-card-head">
      <div>
        <h3>SSH 凭据池</h3>
        <p>密码加密存储、只写不回显；按机器地址匹配计划文档解析出的检查目标，项目内所有计划共用</p>
      </div>
      <a-button @click="openEditor(null)">+ 新增凭据</a-button>
    </header>

    <a-table
      :columns="columns"
      :data-source="credentials"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'host'">
          <span class="ec-host">{{ record.host }}</span>
        </template>
        <template v-else-if="column.key === 'authType'">
          {{ record.authType === 'PASSWORD' ? '密码' : '密钥' }}
        </template>
        <template v-else-if="column.key === 'remark'">
          <span class="ec-remark">{{ record.remark || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'scope'">
          <span v-if="record.planId != null" class="ec-scope-chip plan">本计划</span>
          <span v-else class="ec-scope-chip">项目</span>
        </template>
        <template v-else-if="column.key === 'conn'">
          <span v-if="testResults.has(record.id)" class="ec-conn" :class="testResults.get(record.id)!.ok ? 'ok' : 'fail'">
            {{ testResults.get(record.id)!.ok ? '✓' : '✗' }} {{ testResults.get(record.id)!.message }}
          </span>
          <span v-else class="ec-conn">未测试</span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button type="link" size="small" :loading="testingId === record.id" @click="testConnection(record)">测试连接</a-button>
          <a-button type="link" size="small" @click="openEditor(record)">编辑</a-button>
          <a-button type="link" size="small" danger @click="removeCredential(record)">删除</a-button>
        </template>
      </template>
    </a-table>

    <CredentialFormModal v-model:open="editorOpen" :project-id="projectId" :editing="editing" @saved="load" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import type { EnvCheckCredential } from '../../types';
import { deleteCredentialApi, fetchCredentialsApi, testCredentialApi } from '../../api/env-check';
import CredentialFormModal from './CredentialFormModal.vue';

const props = defineProps<{ projectId: number }>();

const credentials = ref<EnvCheckCredential[]>([]);
const loading = ref(false);
const editorOpen = ref(false);
const editing = ref<EnvCheckCredential | null>(null);
const testingId = ref<number | null>(null);
/** 行内测试结果：凭据 id → ok/文案。 */
const testResults = ref(new Map<number, { ok: boolean; message: string }>());

const columns: TableColumnsType<EnvCheckCredential> = [
  { title: '机器', dataIndex: 'host', key: 'host', width: 130 },
  { title: '端口', dataIndex: 'sshPort', key: 'sshPort', width: 70 },
  { title: '账号', dataIndex: 'username', key: 'username', width: 110 },
  { title: '认证方式', key: 'authType', width: 90 },
  { title: '备注', key: 'remark' },
  { title: '范围', key: 'scope', width: 80 },
  { title: '连接状态', key: 'conn', width: 190 },
  { title: '操作', key: 'actions', width: 210 },
];

async function load() {
  if (!props.projectId) return;
  loading.value = true;
  try {
    credentials.value = await fetchCredentialsApi(props.projectId);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '凭据加载失败');
  } finally {
    loading.value = false;
  }
}

function openEditor(record: EnvCheckCredential | null) {
  editing.value = record;
  editorOpen.value = true;
}

function removeCredential(record: EnvCheckCredential) {
  Modal.confirm({
    title: '删除凭据',
    content: `确认删除 ${record.host}:${record.sshPort} 的凭据？删除后该机器无法参与环境检查。`,
    okText: '删除',
    okType: 'danger',
    async onOk() {
      try {
        await deleteCredentialApi(props.projectId, record.id);
        message.success('凭据已删除');
        await load();
      } catch (error) {
        message.error(error instanceof Error ? error.message : '删除失败');
        throw error;
      }
    },
  });
}

async function testConnection(record: EnvCheckCredential) {
  testingId.value = record.id;
  const time = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
  try {
    const result = await testCredentialApi(props.projectId, record.id);
    testResults.value.set(record.id, { ok: result.ok, message: result.message || (result.ok ? `测试通过（${time}）` : '连接失败') });
  } catch (error) {
    testResults.value.set(record.id, { ok: false, message: error instanceof Error ? error.message : `连接失败（${time}）` });
  } finally {
    testingId.value = null;
    testResults.value = new Map(testResults.value);
  }
}

onMounted(load);
</script>

<style scoped>
.ec-host {
  font-family: var(--font-data);
  font-size: 12px;
}
.ec-remark {
  color: var(--muted);
  font-size: 12px;
}
.ec-scope-chip {
  font: 500 10.5px var(--font-data);
  color: var(--muted);
  border: 1px solid var(--line);
  border-radius: 5px;
  padding: 0 6px;
  line-height: 17px;
}
.ec-scope-chip.plan {
  color: var(--accent);
  background: var(--accent-soft);
  border: none;
  font-weight: 600;
}
.ec-conn {
  font-size: 11.5px;
}
.ec-conn.ok {
  color: var(--ok);
}
.ec-conn.fail {
  color: var(--danger);
}
</style>
