<template>
  <section class="settings-shell">
    <div class="panel">
    <SettingsTabBar v-model="activeConfigTab" :options="tabOptions" />

    <div class="settings-body">
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

      <template v-else-if="activeTab === 'observability' && observabilityProto">
        <div class="panel-header">
          <div>
            <h2>观测数据源 <a-tag color="warning">演示数据</a-tag></h2>
            <p>链路追踪（SkyWalking）等深度观测数据源的平台级配置。Phase 1 实际生效配置为后端 application.yml，本页为配置界面目标态（Phase 2 落地）。</p>
          </div>
          <a-space>
            <a-button @click="testObservabilityConnection">测试连接</a-button>
            <a-button type="primary" @click="saveObservability">保存</a-button>
          </a-space>
        </div>
        <a-form class="observability-form" layout="vertical">
          <a-form-item label="SkyWalking OAP GraphQL 端点">
            <a-input v-model:value="oapEndpoint" placeholder="http://skywalking-oap:12800" />
            <template #extra>后端探针查询入口（queryBasicTraces / queryTrace）。对应配置：platform.evidence.deep.kinds.trace.endpoint</template>
          </a-form-item>
          <a-form-item label="SkyWalking UI 地址">
            <a-input v-model:value="skywalkingUiUrl" placeholder="http://localhost:8080" />
            <template #extra>链路详情抽屉「在 SkyWalking 中打开」的 deep-link 跳转目标，可看拓扑图与全量检索。</template>
          </a-form-item>
          <a-form-item label="执行终态快照">
            <a-switch v-model:checked="traceSnapshotEnabled" size="small" />
            <span class="observability-hint">执行结束后固化 trace 摘要列表，防 OAP 保留期（默认 7 天）过后复查变空</span>
          </a-form-item>
        </a-form>
      </template>

      <AgentApiKeysPanel v-else-if="activeConfigTab === 'agent-api-keys' && isAdmin" />
    </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import { message } from 'ant-design-vue';
import { configTabOptions, systemPermissions, systemRoles, systemUsers } from '../../constants';
import { useAuth } from '../../composables/useAuth';
import { useNavigation } from '../../composables/useNavigation';
import { isProto } from '../../composables/usePrototype';
import SettingsTabBar from '../settings/SettingsTabBar.vue';
import AgentApiKeysPanel from '../settings/AgentApiKeysPanel.vue';

const { activeConfigTab } = useNavigation();
const { currentUser } = useAuth();

const isAdmin = computed(() => (currentUser.value?.roles ?? []).includes('ADMIN'));

const observabilityProto = isProto('trace');
const oapEndpoint = ref('http://skywalking-oap:12800');
const skywalkingUiUrl = ref('http://localhost:8080');
const traceSnapshotEnabled = ref(true);

function testObservabilityConnection() {
  message.success('连接成功：SkyWalking OAP v9.7.0（演示）');
}

function saveObservability() {
  message.success('已保存（演示）——Phase 1 请配置后端 application.yml 生效');
}

// 原型 Tab 值 'observability' 不入产品级 ConfigTab 联合类型，本地放宽比较与选项类型。
const activeTab = computed(() => activeConfigTab.value as string);

const tabOptions = computed<Array<{ label: string; value: string }>>(() => {
  const options: Array<{ label: string; value: string }> = configTabOptions
    .filter((option) => option.value !== 'agent-api-keys' || isAdmin.value)
    .map((option) => ({
      label: option.label,
      value: option.value,
    }));
  if (observabilityProto) {
    options.push({ label: '观测数据源', value: 'observability' });
  }
  return options;
});

watch(
  [isAdmin, activeConfigTab],
  () => {
    if (activeConfigTab.value === 'agent-api-keys' && !isAdmin.value) {
      activeConfigTab.value = 'users';
    }
  },
  { immediate: true },
);

type SystemUser = (typeof systemUsers)[number];

const userColumns: TableColumnsType<SystemUser> = [
  { title: '账号', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
  { title: '系统角色', dataIndex: 'role', key: 'role' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '最近登录', dataIndex: 'lastLogin', key: 'lastLogin' },
];
</script>

<style scoped>
.observability-form { max-width: 560px; padding: 0 18px 18px; }
.observability-hint { margin-left: 10px; font-size: 12px; color: #5c6b7a; }
</style>
