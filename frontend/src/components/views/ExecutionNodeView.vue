<template>
  <section class="hero-band">
    <div>
      <span class="eyebrow">Execution Console</span>
      <h1>执行管理台</h1>
      <p>管理远程 JMeter Controller / Worker 节点，注册后用于分布式压测调度。</p>
    </div>
  </section>

  <section class="panel settings-panel">
    <div class="panel-header">
      <div>
        <h2>执行节点</h2>
        <p>注册节点会立即检测 SSH、Docker 和 JMeter 镜像拉取状态。</p>
      </div>
      <a-button type="primary" :loading="loading" @click="registerNode">注册节点</a-button>
    </div>

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
        <a-form-item label="密钥路径">
          <a-input v-model:value="form.sshKeyPath" placeholder="/Users/wangk/.ssh/id_rsa" />
        </a-form-item>
      </div>
      <a-form-item label="远程工作目录">
        <a-input v-model:value="form.remoteWorkDir" />
      </a-form-item>
    </a-form>

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
          <a-button size="small" @click="checkNode(record)">健康检查</a-button>
        </template>
      </template>
    </a-table>
  </section>
</template>

<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue';
import { useExecutionNodes } from '../../composables/useExecutionNodes';
import type { ExecutionNode, ExecutionNodeRole, ExecutionNodeStatus } from '../../types';

const { nodes, loading, form, loadNodes, registerNode, checkNode } = useExecutionNodes();

const nodeColumns: TableColumnsType<ExecutionNode> = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '主机', dataIndex: 'host', key: 'host' },
  { title: '角色', dataIndex: 'role', key: 'role' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '最近信息', dataIndex: 'lastMessage', key: 'lastMessage' },
  { title: '操作', key: 'actions', width: 100 },
];

void loadNodes();

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
