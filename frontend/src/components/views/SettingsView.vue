<template>
  <section class="hero-band">
    <div>
      <span class="eyebrow">System Configuration</span>
      <h1>系统配置</h1>
      <p>平台级配置不属于具体项目，先覆盖用户、角色、权限三类基础能力，后续可扩展组织、环境、审计策略。</p>
    </div>
  </section>

  <section class="settings-layout">
    <aside class="panel settings-nav">
      <button
        v-for="option in configTabOptions"
        :key="option.value"
        class="module-link"
        :class="{ active: activeConfigTab === option.value }"
        type="button"
        @click="selectConfigTab(option.value)"
      >
        <span>{{ configIndex(option.value) }}</span>
        <strong>{{ option.label }}</strong>
      </button>

      <div class="settings-nav-group">
        <div class="settings-nav-group-title">模型配置管理</div>
        <button
          v-for="option in llmConfigTabOptions"
          :key="option.value"
          class="module-link settings-nav-child"
          :class="{ active: activeConfigTab === option.value }"
          type="button"
          @click="selectConfigTab(option.value)"
        >
          <span>{{ option.index }}</span>
          <strong>{{ option.label }}</strong>
        </button>
      </div>
    </aside>

    <div class="panel settings-panel">
      <template v-if="activeConfigTab === 'users'">
        <div class="panel-header">
          <div>
            <h2>用户管理</h2>
            <p>维护平台登录账号、系统角色和账号状态。</p>
          </div>
          <a-button type="primary">新建用户</a-button>
        </div>
        <a-table
          :columns="userColumns"
          :data-source="systemUsers"
          :pagination="false"
          :row-key="(record: SystemUser) => record.username"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="record.status === '启用' ? 'success' : 'default'">{{ record.status }}</a-tag>
            </template>
          </template>
        </a-table>
      </template>

      <template v-else-if="activeConfigTab === 'roles'">
        <div class="panel-header">
          <div>
            <h2>角色管理</h2>
            <p>角色聚合权限，项目内负责人/成员权限仍由项目成员关系控制。</p>
          </div>
          <a-button type="primary">新建角色</a-button>
        </div>
        <div class="role-grid">
          <div v-for="role in systemRoles" :key="role.name">
            <strong>{{ role.name }}</strong>
            <span>{{ role.description }}</span>
            <small>{{ role.permissions.join(' / ') }}</small>
          </div>
        </div>
      </template>

      <template v-else-if="activeConfigTab === 'permissions'">
        <div class="panel-header">
          <div>
            <h2>权限配置</h2>
            <p>按平台模块定义权限点，供系统角色授权使用。</p>
          </div>
        </div>
        <div class="permission-list">
          <div v-for="permission in systemPermissions" :key="permission.code">
            <span>{{ permission.module }}</span>
            <strong>{{ permission.name }}</strong>
            <small>{{ permission.code }}</small>
          </div>
        </div>
      </template>

      <template v-else-if="activeConfigTab === 'nodes'">
        <div class="panel-header">
          <div>
            <h2>执行节点</h2>
            <p>维护远程 JMeter Controller / Worker 节点。</p>
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
              <a-input v-model:value="form.host" placeholder="10.0.0.12" />
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
      </template>

      <LlmProvidersPanel v-else-if="activeConfigTab === 'llm-providers'" />
      <LlmModelsPanel v-else-if="activeConfigTab === 'llm-models'" />
      <LlmCallRecordsPanel v-else-if="activeConfigTab === 'llm-call-records'" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { watch } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import { useRoute, useRouter } from 'vue-router';
import { configTabOptions, systemPermissions, systemRoles, systemUsers } from '../../constants';
import { configIndex } from '../../utils/format';
import { useNavigation } from '../../composables/useNavigation';
import { useExecutionNodes } from '../../composables/useExecutionNodes';
import type { ConfigTab, ExecutionNode, ExecutionNodeRole, ExecutionNodeStatus } from '../../types';
import LlmProvidersPanel from '../settings/LlmProvidersPanel.vue';
import LlmModelsPanel from '../settings/LlmModelsPanel.vue';
import LlmCallRecordsPanel from '../settings/LlmCallRecordsPanel.vue';

const route = useRoute();
const router = useRouter();
const { activeConfigTab } = useNavigation();
const { nodes, loading, form, loadNodes, registerNode, checkNode } = useExecutionNodes();

const llmConfigTabOptions: Array<{ label: string; value: ConfigTab; index: string }> = [
  { label: '提供商', value: 'llm-providers', index: 'L1' },
  { label: '模型', value: 'llm-models', index: 'L2' },
  { label: '调用记录', value: 'llm-call-records', index: 'L3' },
];

type SystemUser = (typeof systemUsers)[number];

const userColumns: TableColumnsType<SystemUser> = [
  { title: '账号', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
  { title: '系统角色', dataIndex: 'role', key: 'role' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '最近登录', dataIndex: 'lastLogin', key: 'lastLogin' },
];

const nodeColumns: TableColumnsType<ExecutionNode> = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '主机', dataIndex: 'host', key: 'host' },
  { title: '角色', dataIndex: 'role', key: 'role' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '最近信息', dataIndex: 'lastMessage', key: 'lastMessage' },
  { title: '操作', key: 'actions', width: 100 },
];

function selectConfigTab(tab: ConfigTab) {
  activeConfigTab.value = tab;
  if (tab === 'llm-providers') {
    void router.push('/settings/llm/providers');
  } else if (tab === 'llm-models') {
    void router.push('/settings/llm/models');
  } else if (tab === 'llm-call-records') {
    void router.push('/settings/llm/call-records');
  } else {
    void router.push('/settings');
  }
}

function syncTabFromRoute() {
  if (route.path.endsWith('/llm/providers')) {
    activeConfigTab.value = 'llm-providers';
  } else if (route.path.endsWith('/llm/models')) {
    activeConfigTab.value = 'llm-models';
  } else if (route.path.endsWith('/llm/call-records')) {
    activeConfigTab.value = 'llm-call-records';
  } else if (route.path.startsWith('/settings') && !route.path.includes('/llm/')) {
    if (
      activeConfigTab.value === 'llm-providers' ||
      activeConfigTab.value === 'llm-models' ||
      activeConfigTab.value === 'llm-call-records'
    ) {
      activeConfigTab.value = 'users';
    }
  }
}

watch(() => route.path, syncTabFromRoute, { immediate: true });
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
