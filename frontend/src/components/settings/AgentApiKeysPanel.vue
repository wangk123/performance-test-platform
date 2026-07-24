<template>
  <div class="panel-header">
    <div>
      <h2>Agent API Key</h2>
      <p>签发供外部 Agent（MCP / Claude Code）使用的机器凭据；明文仅在创建时展示一次。</p>
    </div>
    <a-button type="primary" @click="openIssue">签发 API Key</a-button>
  </div>

  <a-table :columns="columns" :data-source="keys" :loading="loading" :pagination="false" row-key="id">
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'status'">
        <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
      </template>
      <template v-else-if="column.key === 'actions'">
        <a-button
          size="small"
          danger
          :disabled="record.status !== 'ACTIVE'"
          @click="revokeKey(record)"
        >
          吊销
        </a-button>
      </template>
    </template>
  </a-table>

  <a-modal
    v-model:open="issueOpen"
    title="签发 Agent API Key"
    ok-text="签发"
    :confirm-loading="issuing"
    @ok="submitIssue"
  >
    <a-form layout="vertical">
      <a-form-item label="Scope（预留，可选）">
        <a-input v-model:value="issueForm.scope" placeholder="例如 ops" />
      </a-form-item>
    </a-form>
  </a-modal>

  <a-modal
    v-model:open="plainOpen"
    title="请立即复制 API Key"
    :footer="null"
    :mask-closable="false"
    @cancel="closePlain"
  >
    <a-alert
      type="warning"
      show-icon
      message="明文只展示一次，关闭后无法再次查看。"
      style="margin-bottom: 12px"
    />
    <a-input-group compact>
      <a-input :value="plainKey" readonly style="width: calc(100% - 88px)" />
      <a-button type="primary" style="width: 88px" @click="copyPlain">复制</a-button>
    </a-input-group>
  </a-modal>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import {
  issueAgentApiKeyApi,
  listAgentApiKeysApi,
  revokeAgentApiKeyApi,
  type AgentApiKeyView,
} from '../../api/agent-api-keys';

const keys = ref<AgentApiKeyView[]>([]);
const loading = ref(false);
const issuing = ref(false);
const issueOpen = ref(false);
const plainOpen = ref(false);
const plainKey = ref('');
const issueForm = reactive({ scope: '' });

const columns: TableColumnsType<AgentApiKeyView> = [
  { title: '前缀', dataIndex: 'prefix', key: 'prefix', width: 140 },
  { title: 'Scope', dataIndex: 'scope', key: 'scope' },
  { title: '状态', key: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '过期时间', dataIndex: 'expiresAt', key: 'expiresAt' },
  { title: '操作', key: 'actions', width: 100 },
];

function statusLabel(status: AgentApiKeyView['status']) {
  if (status === 'ACTIVE') return '有效';
  if (status === 'REVOKED') return '已吊销';
  return '已过期';
}

function statusColor(status: AgentApiKeyView['status']) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'REVOKED') return 'error';
  return 'default';
}

async function load() {
  loading.value = true;
  try {
    keys.value = await listAgentApiKeysApi();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载失败');
  } finally {
    loading.value = false;
  }
}

function openIssue() {
  issueForm.scope = '';
  issueOpen.value = true;
}

async function submitIssue() {
  issuing.value = true;
  try {
    const issued = await issueAgentApiKeyApi({
      scope: issueForm.scope.trim() || undefined,
    });
    issueOpen.value = false;
    plainKey.value = issued.plainKey;
    plainOpen.value = true;
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '签发失败');
    throw e;
  } finally {
    issuing.value = false;
  }
}

function closePlain() {
  plainOpen.value = false;
  plainKey.value = '';
}

async function copyPlain() {
  try {
    await navigator.clipboard.writeText(plainKey.value);
    message.success('已复制');
  } catch {
    message.error('复制失败，请手动选择复制');
  }
}

function revokeKey(record: AgentApiKeyView) {
  Modal.confirm({
    title: '确认吊销该 API Key？',
    content: `前缀 ${record.prefix} 吊销后立即失效，无法恢复。`,
    okType: 'danger',
    async onOk() {
      try {
        await revokeAgentApiKeyApi(record.id);
        message.success('已吊销');
        await load();
      } catch (e) {
        message.error(e instanceof Error ? e.message : '吊销失败');
        throw e;
      }
    },
  });
}

onMounted(load);
</script>
