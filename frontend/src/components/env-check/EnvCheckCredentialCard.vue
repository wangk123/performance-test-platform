<template>
  <section class="ec-card">
    <header class="ec-card-head">
      <div>
        <h3>项目设置 · 环境检查凭据</h3>
        <p>密码加密存储、只写不回显；计划可用专属凭据覆盖某一台</p>
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

    <a-drawer v-model:open="editorOpen" :title="editing ? '编辑凭据' : '新增凭据'" :width="420" destroy-on-close>
      <a-form layout="vertical">
        <a-form-item label="机器地址（IP）" required>
          <a-input v-model:value="form.host" placeholder="如 10.1.1.10" />
        </a-form-item>
        <a-form-item label="SSH 端口">
          <a-input-number v-model:value="form.sshPort" :min="1" :max="65535" style="width: 100%" />
        </a-form-item>
        <a-form-item label="账号" required>
          <a-input v-model:value="form.username" placeholder="如 perftest" />
        </a-form-item>
        <a-form-item label="认证方式">
          <a-radio-group v-model:value="form.authType">
            <a-radio value="PASSWORD">密码</a-radio>
            <a-radio value="KEY">密钥</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="form.authType === 'PASSWORD'" label="密码" :extra="editing ? '留空则不修改原密码' : ''">
          <a-input-password v-model:value="form.password" placeholder="只写不回显" autocomplete="new-password" />
        </a-form-item>
        <a-form-item v-else label="私钥内容" :extra="editing ? '留空则不修改原密钥' : ''">
          <a-textarea v-model:value="form.keyMaterial" :rows="5" placeholder="PEM 私钥，只写不回显" />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="form.remark" placeholder="如 订单服务 / 非 22 端口" />
        </a-form-item>
        <a-form-item v-if="planId != null">
          <a-checkbox v-model:checked="form.planScoped">仅当前计划生效</a-checkbox>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-space>
          <a-button @click="editorOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="saveCredential">保存</a-button>
        </a-space>
      </template>
    </a-drawer>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import type { EnvCheckCredential } from '../../types';
import {
  deleteCredentialApi,
  fetchCredentialsApi,
  saveCredentialApi,
  testCredentialApi,
} from '../../api/env-check';

const props = defineProps<{ projectId: number; planId?: number }>();

const credentials = ref<EnvCheckCredential[]>([]);
const loading = ref(false);
const saving = ref(false);
const editorOpen = ref(false);
const editing = ref<EnvCheckCredential | null>(null);
const testingId = ref<number | null>(null);
/** 行内测试结果：凭据 id → ok/文案。 */
const testResults = ref(new Map<number, { ok: boolean; message: string }>());

const form = reactive({
  host: '',
  sshPort: 22,
  username: '',
  authType: 'PASSWORD' as EnvCheckCredential['authType'],
  password: '',
  keyMaterial: '',
  remark: '',
  planScoped: false,
});

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
  form.host = record?.host ?? '';
  form.sshPort = record?.sshPort ?? 22;
  form.username = record?.username ?? '';
  form.authType = record?.authType ?? 'PASSWORD';
  form.password = '';
  form.keyMaterial = '';
  form.remark = record?.remark ?? '';
  form.planScoped = record?.planId != null; // 新增默认项目级；编辑本计划凭据保持其范围
  editorOpen.value = true;
}

async function saveCredential() {
  if (!form.host.trim() || !form.username.trim()) {
    message.warning('请填写机器地址与账号');
    return;
  }
  if (form.authType === 'PASSWORD' && !editing.value && !form.password) {
    message.warning('请填写密码');
    return;
  }
  if (form.authType === 'KEY' && !editing.value && !form.keyMaterial.trim()) {
    message.warning('请填写私钥内容');
    return;
  }
  saving.value = true;
  try {
    await saveCredentialApi(props.projectId, {
      host: form.host.trim(),
      sshPort: form.sshPort,
      username: form.username.trim(),
      // 认证方式由携带的字段推断（后端以 keyMaterial 优先）：密码或私钥只传其一
      password: form.authType === 'PASSWORD' && form.password ? form.password : undefined,
      keyMaterial: form.authType === 'KEY' && form.keyMaterial.trim() ? form.keyMaterial.trim() : undefined,
      remark: form.remark.trim() || undefined,
      // 计划级覆盖：勾选「仅当前计划生效」时携带 planId，resolve 优先于项目池
      planId: props.planId != null && form.planScoped ? props.planId : undefined,
    });
    editorOpen.value = false;
    message.success('凭据已保存');
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '凭据保存失败');
  } finally {
    saving.value = false;
  }
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
