<template>
  <section class="settings-panel">
    <div class="page-head">
      <div>
        <h1>执行器配置</h1>
        <p>维护远程 JMeter Controller / Worker 节点。</p>
      </div>
      <a-button type="primary" @click="openCreateDialog">新增执行器</a-button>
    </div>
    <div class="panel">

    <a-table
      :columns="nodeColumns"
      :data-source="nodes"
      :loading="loading"
      :pagination="false"
      :row-key="(record: ExecutionNode) => record.id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="nodeStatusColor(record.status)">{{ nodeStatusText(record.status) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'role'">{{ nodeRoleText(record.role) }}</template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a-button size="small" @click="openEditDialog(record)">修改</a-button>
            <a-button size="small" @click="openInitializeDialog(record)">初始化</a-button>
            <a-button size="small" @click="checkNode(record)">健康检查</a-button>
            <a-button size="small" danger @click="confirmDelete(record)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
    </div>

    <a-modal v-model:open="dialogVisible" :title="dialogTitle" width="720px" destroy-on-close>
      <a-form class="node-form" layout="vertical">
        <div class="task-form-grid">
          <a-form-item label="节点名称">
            <a-input v-model:value="form.name" placeholder="worker-01" />
          </a-form-item>
          <a-form-item label="节点角色">
            <a-select v-model:value="form.role">
              <a-select-option value="CONTROLLER">Controller</a-select-option>
              <a-select-option value="WORKER">Worker</a-select-option>
              <a-select-option value="BOTH">Controller + Worker</a-select-option>
            </a-select>
          </a-form-item>
        </div>
        <div class="task-form-grid">
          <a-form-item label="主机 IP">
            <a-input v-model:value="form.host" placeholder="192.168.17.217" />
          </a-form-item>
          <a-form-item label="SSH 端口">
            <a-input-number v-model:value="form.sshPort" :min="1" :max="65535" style="width: 100%" />
          </a-form-item>
        </div>
        <div class="task-form-grid">
          <a-form-item label="SSH 用户">
            <a-input v-model:value="form.sshUsername" placeholder="root" />
          </a-form-item>
          <a-form-item label="一次性 SSH 密码">
            <a-input-password v-model:value="form.sshPassword" />
          </a-form-item>
        </div>
        <div class="task-form-grid">
          <a-form-item label="密钥路径">
            <a-input v-model:value="form.sshKeyPath" placeholder="提交初始化后自动生成" />
          </a-form-item>
          <a-form-item label="远程工作目录">
            <a-input v-model:value="form.remoteWorkDir" />
          </a-form-item>
        </div>
      </a-form>

      <template #footer>
        <a-button @click="dialogVisible = false">取消</a-button>
        <a-button type="primary" :loading="loading" @click="submitNodeDialog">{{ submitText }}</a-button>
      </template>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import { useExecutionNodes } from '../../composables/useExecutionNodes';
import type { ExecutionNode, ExecutionNodeRole, ExecutionNodeStatus } from '../../types';
import { confirmAction } from '../../utils/feedback';

const { nodes, loading, form, loadNodes, resetForm, saveNode, initializeNode, deleteNode, checkNode } = useExecutionNodes();
const dialogVisible = ref(false);
const dialogMode = ref<'create' | 'edit' | 'initialize'>('create');
const currentNode = ref<ExecutionNode | null>(null);

const nodeColumns: TableColumnsType<ExecutionNode> = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '主机', dataIndex: 'host', key: 'host' },
  { title: '角色', dataIndex: 'role', key: 'role' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '最近信息', dataIndex: 'lastMessage', key: 'lastMessage' },
  { title: '操作', key: 'actions', width: 280 },
];

const dialogTitle = computed(() => {
  if (dialogMode.value === 'edit') return '修改执行器';
  if (dialogMode.value === 'initialize') return '初始化执行器';
  return '新增执行器';
});
const submitText = computed(() => dialogMode.value === 'initialize' ? '初始化' : '保存并初始化');

void loadNodes();

function openCreateDialog() {
  dialogMode.value = 'create';
  currentNode.value = null;
  resetForm();
  dialogVisible.value = true;
}

function openEditDialog(node: ExecutionNode) {
  dialogMode.value = 'edit';
  currentNode.value = node;
  resetForm(node);
  dialogVisible.value = true;
}

function openInitializeDialog(node: ExecutionNode) {
  dialogMode.value = 'initialize';
  currentNode.value = node;
  resetForm(node);
  dialogVisible.value = true;
}

async function submitNodeDialog() {
  const success = dialogMode.value === 'initialize' && currentNode.value
    ? await initializeNode(currentNode.value)
    : await saveNode(currentNode.value?.id);
  if (success) {
    dialogVisible.value = false;
  }
}

async function confirmDelete(node: ExecutionNode) {
  try {
    await confirmAction({
      title: '删除执行器',
      content: `确认删除 ${node.name}？`,
      okText: '删除',
      okType: 'danger',
    });
  } catch {
    return;
  }
  await deleteNode(node);
}

function nodeStatusText(status: ExecutionNodeStatus) {
  return status === 'AVAILABLE' ? '可用' : status === 'OFFLINE' ? '离线' : '未知';
}

function nodeStatusColor(status: ExecutionNodeStatus) {
  return status === 'AVAILABLE' ? 'success' : status === 'OFFLINE' ? 'error' : 'default';
}

function nodeRoleText(role: ExecutionNodeRole) {
  if (role === 'CONTROLLER') return 'Controller';
  if (role === 'WORKER') return 'Worker';
  return 'Controller + Worker';
}
</script>
