<template>
  <section class="monitoring-config density-b" data-density="b">
    <div class="page-head">
      <div>
        <h1>监控配置</h1>
        <p>按服务器维护资源采集，按需添加 JVM、MySQL、Redis、Nginx、Kafka 监控项。</p>
      </div>
      <a-button type="primary" @click="openCreate">新增服务器</a-button>
    </div>
    <div class="panel">
      <a-table
        class="monitor-target-table"
        :columns="targetColumns"
        :data-source="projectTargets"
        :loading="loadingMonitorTargets"
        :pagination="false"
        :row-key="(record: MonitorTarget) => record.id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'endpoint'">
            <div class="monitor-cell-stack">
              <span class="monitor-cell-primary">{{ record.env }}</span>
              <span class="monitor-cell-secondary">{{ record.address }}{{ record.metricsPath }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'items'">
            <span v-if="record.items.length" class="monitor-cell-secondary">{{ record.items.map(itemLabel).join(' / ') }}</span>
            <span v-else class="monitor-cell-muted">未配置应用或中间件监控项</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-space :size="6">
              <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '停用' }}</a-tag>
              <a-tag :color="checkColor(record.lastCheckStatus)">{{ checkText(record.lastCheckStatus) }}</a-tag>
            </a-space>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space :size="4">
              <a-button
                size="small"
                :disabled="!canDeploy(record)"
                :loading="deployingTargetId === record.id"
                @click="deployTarget(record)"
              >
                上传脚本
              </a-button>
              <a-button size="small" @click="checkMonitorTarget(record)">探活</a-button>
              <a-button size="small" @click="openEdit(record)">编辑</a-button>
              <a-button size="small" danger @click="deleteMonitorTarget(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="dialogVisible"
      :title="editingTarget ? '编辑监控目标' : '新增监控目标'"
      width="800px"
      class="monitor-target-dialog"
      :body-style="{ padding: '20px 24px 8px' }"
      destroy-on-close
    >
      <a-form class="monitor-dialog-form" layout="vertical">
        <div class="monitor-dialog-block">
          <div class="monitor-dialog-block-head">
            <div>
              <div class="monitor-dialog-block-title">服务器资源</div>
              <div class="monitor-dialog-block-desc">配置 Node Exporter 采集地址与基础标识</div>
            </div>
            <a-form-item label="状态" class="monitor-inline-switch">
              <a-switch v-model:checked="form.enabled" checked-children="启用" un-checked-children="停用" />
            </a-form-item>
          </div>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="目标名称" required>
                <a-input v-model:value="form.name" placeholder="订单服务器" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="Host" required>
                <a-input v-model:value="form.host" placeholder="127.0.0.1" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="环境">
                <a-input v-model:value="form.env" placeholder="test" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="资源 Exporter Port" required>
                <a-input-number v-model:value="form.port" :min="1" :max="65535" class="full-input" />
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="资源 Metrics Path" required>
                <a-input v-model:value="form.metricsPath" placeholder="/metrics" />
              </a-form-item>
            </a-col>
          </a-row>
        </div>

        <a-collapse v-model:activeKey="collapseKeys" class="monitor-collapse" expand-icon-position="end">
          <a-collapse-panel key="deploy">
            <template #header>
              <span class="monitor-collapse-title">远程部署</span>
              <span class="monitor-collapse-hint">可选 · 自动推送 Agent / Exporter</span>
            </template>
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="账号">
                  <a-input v-model:value="form.sshUsername" placeholder="root" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="密码">
                  <a-input-password
                    v-model:value="form.sshPassword"
                    :placeholder="passwordPlaceholder"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="SSH 端口">
                  <a-input-number v-model:value="form.sshPort" :min="1" :max="65535" class="full-input" placeholder="22" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="插件目录">
                  <a-input v-model:value="form.pluginDir" placeholder="/opt/monitoring" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-collapse-panel>
        </a-collapse>

        <div class="monitor-dialog-block">
          <div class="monitor-dialog-block-head monitor-dialog-block-head-tight">
            <div>
              <div class="monitor-dialog-block-title">应用与中间件监控项</div>
              <div class="monitor-dialog-block-desc">不添加时仅采集服务器资源</div>
            </div>
            <a-button type="dashed" size="small" @click="addItem">添加监控项</a-button>
          </div>

          <a-collapse
            v-if="form.items.length"
            v-model:activeKey="itemCollapseKeys"
            class="monitor-collapse monitor-items-collapse"
            expand-icon-position="end"
          >
            <a-collapse-panel v-for="(item, index) in form.items" :key="item.id">
              <template #header>
                <span class="monitor-collapse-title">{{ itemTypeText(item.type) }}</span>
                <span class="monitor-collapse-hint">{{ item.name || '未命名' }}</span>
              </template>
              <template #extra>
                <a-button type="link" danger size="small" @click.stop="removeItem(index)">删除</a-button>
              </template>
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="类型" required>
                    <a-select v-model:value="item.type" @change="resetItemByType(item)">
                      <a-select-option v-for="option in monitorItemTypeOptions" :key="option.value" :value="option.value">
                        {{ option.label }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="名称" required>
                    <a-input v-model:value="item.name" :placeholder="itemTypeText(item.type)" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="Exporter Port" required>
                    <a-input-number v-model:value="item.port" :min="1" :max="65535" class="full-input" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="Metrics Path" required>
                    <a-input v-model:value="item.metricsPath" placeholder="/metrics" />
                  </a-form-item>
                </a-col>
                <a-col v-if="item.type === 'JAVA_JMX_AGENT'" :span="24">
                  <a-form-item label="JVM 进程关键字" required>
                    <a-input v-model:value="item.processKeyword" placeholder="order-service.jar" />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-collapse-panel>
          </a-collapse>
          <a-empty v-else class="monitor-items-empty" description="未添加应用或中间件监控项" />
        </div>
      </a-form>
      <template #footer>
        <div class="monitor-dialog-actions">
          <a-button @click="dialogVisible = false">取消</a-button>
          <a-button type="primary" :disabled="!canSave" @click="submit">保存</a-button>
        </div>
      </template>
    </a-modal>

    <a-modal
      v-model:open="deployDialogVisible"
      title="部署结果"
      width="760px"
      :footer="null"
      destroy-on-close
    >
      <p class="deploy-dialog-desc">文件已上传到 {{ deployResult?.remoteDir }}</p>

      <div v-if="deployResult?.startResults?.length" class="deploy-section">
        <h4 class="deploy-section-title">Exporter 启动结果</h4>
        <div class="deploy-start-list">
          <div v-for="(item, index) in deployResult.startResults" :key="index" class="deploy-start-item">
            <div class="deploy-start-head">
              <strong>{{ item.title }}</strong>
              <a-tag :color="item.success ? 'green' : 'red'">{{ item.success ? '成功' : '失败' }}</a-tag>
            </div>
            <pre class="deploy-command-code">{{ item.output }}</pre>
          </div>
        </div>
      </div>

      <div v-if="deployResult?.agentCommands?.length" class="deploy-section">
        <h4 class="deploy-section-title">JVM Agent 配置（需写入应用启动参数）</h4>
        <div class="deploy-command-list">
          <div v-for="(item, index) in deployResult.agentCommands" :key="index" class="deploy-command-item">
            <div class="deploy-command-head">
              <strong>{{ item.title }}</strong>
              <a-button type="link" size="small" @click="copyCommand(item.command)">复制</a-button>
            </div>
            <pre class="deploy-command-code">{{ item.command }}</pre>
          </div>
        </div>
      </div>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import type { MonitorDeployResult, MonitorItem, MonitorItemType, MonitorTarget, MonitorTargetCheckStatus } from '../../types';
import { useMonitoring } from '../../composables/useMonitoring';
import { useWorkspace } from '../../composables/useWorkspace';

type MonitorItemForm = Omit<MonitorItem, 'labels'>;

const monitorItemTypeOptions: Array<{ label: string; value: MonitorItemType }> = [
  { label: 'JVM', value: 'JAVA_JMX_AGENT' },
  { label: 'MySQL', value: 'MYSQL_EXPORTER' },
  { label: 'Redis', value: 'REDIS_EXPORTER' },
  { label: 'Nginx', value: 'NGINX_EXPORTER' },
  { label: 'Kafka', value: 'KAFKA_EXPORTER' },
];

const defaultPorts: Record<MonitorItemType, number> = {
  JAVA_JMX_AGENT: 9404,
  MYSQL_EXPORTER: 9104,
  REDIS_EXPORTER: 9121,
  NGINX_EXPORTER: 9113,
  KAFKA_EXPORTER: 9308,
};

const { currentProject } = useWorkspace();
const { monitorTargets, loadingMonitorTargets, loadMonitorTargets, saveMonitorTarget, checkMonitorTarget, deleteMonitorTarget, deployMonitorTarget } = useMonitoring();

const dialogVisible = ref(false);
const deployDialogVisible = ref(false);
const deployResult = ref<MonitorDeployResult | null>(null);
const deployingTargetId = ref<number | null>(null);
const editingTarget = ref<MonitorTarget | null>(null);
const collapseKeys = ref<string[]>([]);
const itemCollapseKeys = ref<string[]>([]);
const form = reactive({
  name: '',
  host: '',
  sshUsername: '',
  sshPassword: '',
  sshPort: null as number | null,
  pluginDir: '',
  port: 9100,
  metricsPath: '/metrics',
  env: 'test',
  enabled: true,
  items: [] as MonitorItemForm[],
});

const projectTargets = computed(() =>
  currentProject.value ? monitorTargets.value.filter((target) => target.projectId === currentProject.value?.id) : [],
);

const targetColumns: TableColumnsType<MonitorTarget> = [
  { title: '目标名称', dataIndex: 'name', key: 'name', width: 140, ellipsis: true },
  { title: '采集地址', key: 'endpoint', width: 220 },
  { title: '监控项', key: 'items', ellipsis: true },
  { title: '状态', key: 'status', width: 160 },
  { title: '操作', key: 'actions', width: 280, fixed: 'right' },
];

const passwordPlaceholder = computed(() => {
  if (editingTarget.value?.sshPasswordConfigured) {
    return '已配置，留空不修改';
  }
  return editingTarget.value ? '留空则不修改' : '部署时必填';
});

const canSave = computed(() =>
  Boolean(form.name.trim() && form.host.trim() && form.port && form.metricsPath.trim())
  && form.items.every((item) => Boolean(
    item.name.trim()
    && item.port
    && item.metricsPath.trim(),
  )
    && (item.type !== 'JAVA_JMX_AGENT' || Boolean(item.processKeyword?.trim()))
  ),
);

watch(currentProject, (project) => {
  if (project) {
    void loadMonitorTargets(project.id);
  }
}, { immediate: true });

function openCreate() {
  editingTarget.value = null;
  collapseKeys.value = [];
  itemCollapseKeys.value = [];
  Object.assign(form, {
    name: '',
    host: '',
    sshUsername: '',
    sshPassword: '',
    sshPort: null,
    pluginDir: '',
    port: 9100,
    metricsPath: '/metrics',
    env: 'test',
    enabled: true,
    items: [],
  });
  dialogVisible.value = true;
}

function openEdit(target: MonitorTarget) {
  editingTarget.value = target;
  collapseKeys.value = target.sshUsername || target.pluginDir || target.sshPort ? ['deploy'] : [];
  itemCollapseKeys.value = [];
  Object.assign(form, {
    name: target.name,
    host: target.host,
    sshUsername: target.sshUsername ?? '',
    sshPassword: '',
    sshPort: target.sshPort,
    pluginDir: target.pluginDir ?? '',
    port: target.port,
    metricsPath: target.metricsPath,
    env: target.env,
    enabled: target.enabled,
    items: target.items.map((item) => ({ ...item })),
  });
  dialogVisible.value = true;
}

function addItem() {
  const item = createItem('JAVA_JMX_AGENT');
  form.items.push(item);
  itemCollapseKeys.value = [...itemCollapseKeys.value, item.id];
}

function removeItem(index: number) {
  const [removed] = form.items.splice(index, 1);
  if (removed) {
    itemCollapseKeys.value = itemCollapseKeys.value.filter((key) => key !== removed.id);
  }
}

function resetItemByType(item: MonitorItemForm) {
  const next = createItem(item.type);
  Object.assign(item, {
    name: next.name,
    port: next.port,
    metricsPath: next.metricsPath,
    serviceName: next.serviceName,
    processKeyword: next.processKeyword,
    instanceName: next.instanceName,
    databaseName: next.databaseName,
  });
}

async function submit() {
  if (!currentProject.value) {
    return;
  }
  const target = await saveMonitorTarget(currentProject.value.id, {
    name: form.name.trim(),
    serviceName: form.name.trim(),
    host: form.host.trim(),
    sshUsername: emptyToNull(form.sshUsername),
    sshPassword: emptyToNull(form.sshPassword),
    sshPort: form.sshPort ?? undefined,
    pluginDir: emptyToNull(form.pluginDir),
    port: form.port,
    metricsPath: form.metricsPath,
    env: form.env,
    labels: editingTarget.value?.labels ?? {},
    items: form.items.map(toMonitorItem),
    enabled: form.enabled,
  }, editingTarget.value);
  if (target) {
    dialogVisible.value = false;
  }
}

function canDeploy(target: MonitorTarget) {
  return Boolean(target.sshUsername && target.pluginDir && target.sshPasswordConfigured);
}

async function deployTarget(target: MonitorTarget) {
  deployingTargetId.value = target.id;
  try {
    const result = await deployMonitorTarget(target);
    if (result) {
      deployResult.value = result;
      deployDialogVisible.value = true;
    }
  } finally {
    deployingTargetId.value = null;
  }
}

async function copyCommand(command: string) {
  try {
    await navigator.clipboard.writeText(command);
    message.success('命令已复制');
  } catch {
    message.error('复制失败');
  }
}

function createItem(type: MonitorItemType): MonitorItemForm {
  return {
    id: crypto.randomUUID(),
    type,
    name: itemTypeText(type),
    port: defaultPorts[type],
    metricsPath: '/metrics',
    serviceName: type === 'JAVA_JMX_AGENT' ? '' : null,
    processKeyword: type === 'JAVA_JMX_AGENT' ? '' : null,
    instanceName: null,
    databaseName: null,
  };
}

function toMonitorItem(item: MonitorItemForm): MonitorItem {
  return {
    id: item.id,
    type: item.type,
    name: item.name,
    port: item.port,
    metricsPath: item.metricsPath,
    serviceName: item.type === 'JAVA_JMX_AGENT' ? emptyToNull(item.name) : null,
    processKeyword: emptyToNull(item.processKeyword),
    instanceName: null,
    databaseName: null,
    labels: {},
  };
}

function emptyToNull(value: string | null) {
  return value && value.trim() ? value.trim() : null;
}

function itemTypeText(type: MonitorItemType) {
  return monitorItemTypeOptions.find((item) => item.value === type)?.label ?? type;
}

function itemLabel(item: MonitorItem) {
  return `${itemTypeText(item.type)}:${item.name}`;
}

function checkText(status: MonitorTargetCheckStatus) {
  if (status === 'SUCCESS') {
    return '探活通过';
  }
  if (status === 'FAILED') {
    return '探活失败';
  }
  return '未探活';
}

function checkColor(status: MonitorTargetCheckStatus) {
  if (status === 'SUCCESS') {
    return 'green';
  }
  if (status === 'FAILED') {
    return 'red';
  }
  return 'default';
}
</script>

<style scoped>
.monitoring-config {
  display: grid;
  gap: 16px;
}

.monitor-dialog-form {
  display: grid;
  gap: 20px;
}

.monitor-dialog-form :deep(.ant-form-item) {
  margin-bottom: 16px;
}

.monitor-dialog-block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 4px;
}

.monitor-dialog-block-head-tight {
  margin-bottom: 12px;
}

.monitor-dialog-block-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}

.monitor-dialog-block-desc {
  margin-top: 2px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.monitor-inline-switch {
  margin: 0;
  flex-shrink: 0;
}

.monitor-inline-switch :deep(.ant-form-item-label) {
  padding-bottom: 4px;
}

.monitor-collapse {
  background: transparent;
  border: 0;
}

.monitor-collapse :deep(.ant-collapse-item) {
  overflow: hidden;
  border: 1px solid var(--border) !important;
  border-radius: 8px !important;
  background: var(--surface);
}

.monitor-collapse :deep(.ant-collapse-item + .ant-collapse-item) {
  margin-top: 10px;
  border-top: 1px solid var(--border) !important;
}

.monitor-items-collapse :deep(.ant-collapse-item + .ant-collapse-item) {
  margin-top: 8px;
}

.monitor-collapse :deep(.ant-collapse-header) {
  align-items: center !important;
  padding: 12px 16px !important;
}

.monitor-collapse :deep(.ant-collapse-content-box) {
  padding: 4px 16px 8px !important;
}

.monitor-collapse-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.monitor-collapse-hint {
  margin-left: 8px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
}

.monitor-items-empty {
  padding: 12px 0 4px;
}

.monitor-target-table :deep(.ant-table) {
  border-radius: 8px;
}

.monitor-cell-stack {
  display: grid;
  gap: 2px;
}

.monitor-cell-primary {
  color: var(--text);
  font-size: 13px;
}

.monitor-cell-secondary {
  color: var(--muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.monitor-cell-muted {
  color: var(--muted);
  font-size: 12px;
}

.full-input {
  width: 100%;
}

.deploy-section {
  margin-bottom: 18px;
}

.deploy-section-title {
  margin: 0 0 10px;
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.deploy-start-list {
  display: grid;
  gap: 10px;
}

.deploy-start-item {
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface-soft);
}

.deploy-start-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.deploy-start-head strong {
  color: var(--text);
  font-size: 13px;
}

.deploy-command-list {
  display: grid;
  gap: 12px;
  max-height: 420px;
  overflow: auto;
}

.deploy-dialog-desc {
  margin: 0 0 14px;
  color: var(--muted);
  font-size: 13px;
}

.deploy-command-item {
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface-soft);
}

.deploy-command-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.deploy-command-head strong {
  color: var(--text);
  font-size: 13px;
}

.deploy-command-code {
  margin: 0;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--surface);
  color: var(--text);
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.monitor-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 760px) {
  .monitor-dialog-block-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
