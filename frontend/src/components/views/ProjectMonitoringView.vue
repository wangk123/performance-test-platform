<template>
  <section class="monitoring-config">
    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Target Monitoring</span>
          <h2>监控配置</h2>
          <p>按服务器维护资源采集，按需添加 JVM、MySQL、Redis、Nginx、Kafka 监控项。</p>
        </div>
        <a-button type="primary" @click="openCreate">新增服务器</a-button>
      </div>

      <div class="monitor-target-list">
        <div v-for="target in projectTargets" :key="target.id" class="monitor-target-row">
          <div class="monitor-target-main">
            <strong>{{ target.name }}</strong>
            <span>服务器资源 · {{ target.env }} · {{ target.address }}{{ target.metricsPath }}</span>
            <small>{{ target.items.length ? target.items.map(itemLabel).join(' / ') : '未配置应用或中间件监控项' }}</small>
          </div>
          <div class="monitor-target-status">
            <a-tag :color="target.enabled ? 'green' : 'default'">{{ target.enabled ? '启用' : '停用' }}</a-tag>
            <a-tag :color="checkColor(target.lastCheckStatus)">{{ checkText(target.lastCheckStatus) }}</a-tag>
          </div>
          <div class="monitor-target-actions">
            <a-button size="small" @click="checkMonitorTarget(target)">探活</a-button>
            <a-button size="small" @click="openEdit(target)">编辑</a-button>
            <a-button size="small" danger @click="deleteMonitorTarget(target)">删除</a-button>
          </div>
        </div>
        <a-empty v-if="!projectTargets.length && !loadingMonitorTargets" description="暂无监控目标" />
      </div>
    </div>

    <a-modal v-model:open="dialogVisible" :title="editingTarget ? '编辑监控目标' : '新增监控目标'" width="860px" destroy-on-close>
      <a-form layout="vertical">
        <div class="monitor-section-title">服务器资源</div>
        <div class="monitor-form-grid">
          <a-form-item label="目标名称" required>
            <a-input v-model:value="form.name" placeholder="订单服务器" />
          </a-form-item>
          <a-form-item label="Host" required>
            <a-input v-model:value="form.host" placeholder="127.0.0.1" />
          </a-form-item>
          <a-form-item label="资源 Exporter Port" required>
            <a-input-number v-model:value="form.port" :min="1" :max="65535" class="full-input" />
          </a-form-item>
          <a-form-item label="资源 Metrics Path" required>
            <a-input v-model:value="form.metricsPath" placeholder="/metrics" />
          </a-form-item>
          <a-form-item label="环境">
            <a-input v-model:value="form.env" placeholder="test" />
          </a-form-item>
          <a-form-item label="状态">
            <a-switch v-model:checked="form.enabled" checked-children="启用" un-checked-children="停用" />
          </a-form-item>
        </div>

        <a-form-item label="Labels">
          <a-textarea v-model:value="form.labelsText" :rows="3" placeholder="team=loan&#10;zone=shanghai" />
        </a-form-item>

        <div class="monitor-item-header">
          <div>
            <div class="monitor-section-title">应用与中间件监控项</div>
            <span>不添加监控项时，只采集服务器资源。</span>
          </div>
          <a-button @click="addItem">添加监控项</a-button>
        </div>

        <div class="monitor-item-list">
          <div v-for="(item, index) in form.items" :key="item.id" class="monitor-item-card">
            <div class="monitor-item-title">
              <button type="button" class="monitor-item-toggle" @click="item.collapsed = !item.collapsed">
                <span>{{ item.collapsed ? '展开' : '折叠' }}</span>
              </button>
              <strong>{{ itemTypeText(item.type) }}</strong>
              <span>{{ item.name || '未命名' }}</span>
              <a-button v-if="!item.collapsed" size="small" danger @click="removeItem(index)">删除</a-button>
            </div>
            <div v-if="!item.collapsed" class="monitor-item-grid">
              <a-form-item label="类型" required>
                <a-select v-model:value="item.type" @change="resetItemByType(item)">
                  <a-select-option v-for="option in monitorItemTypeOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="名称" required>
                <a-input v-model:value="item.name" :placeholder="itemTypeText(item.type)" />
              </a-form-item>
              <a-form-item label="Exporter Port" required>
                <a-input-number v-model:value="item.port" :min="1" :max="65535" class="full-input" />
              </a-form-item>
              <a-form-item label="Metrics Path" required>
                <a-input v-model:value="item.metricsPath" placeholder="/metrics" />
              </a-form-item>
              <a-form-item v-if="item.type === 'JAVA_JMX_AGENT'" label="JVM 进程关键字" required>
                <a-input v-model:value="item.processKeyword" placeholder="order-service.jar" />
              </a-form-item>
            </div>
          </div>
          <a-empty v-if="!form.items.length" description="未添加应用或中间件监控项" />
        </div>
      </a-form>
      <template #footer>
        <a-button @click="dialogVisible = false">取消</a-button>
        <a-button type="primary" :disabled="!canSave" @click="submit">保存</a-button>
      </template>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import type { MonitorItem, MonitorItemType, MonitorTarget, MonitorTargetCheckStatus } from '../../types';
import { useMonitoring } from '../../composables/useMonitoring';
import { useWorkspace } from '../../composables/useWorkspace';

type MonitorItemForm = Omit<MonitorItem, 'labels'> & { collapsed: boolean };

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
const { monitorTargets, loadingMonitorTargets, loadMonitorTargets, saveMonitorTarget, checkMonitorTarget, deleteMonitorTarget } = useMonitoring();

const dialogVisible = ref(false);
const editingTarget = ref<MonitorTarget | null>(null);
const form = reactive({
  name: '',
  host: '',
  port: 9100,
  metricsPath: '/metrics',
  env: 'test',
  labelsText: '',
  enabled: true,
  items: [] as MonitorItemForm[],
});

const projectTargets = computed(() =>
  currentProject.value ? monitorTargets.value.filter((target) => target.projectId === currentProject.value?.id) : [],
);
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
  Object.assign(form, {
    name: '',
    host: '',
    port: 9100,
    metricsPath: '/metrics',
    env: 'test',
    labelsText: '',
    enabled: true,
    items: [],
  });
  dialogVisible.value = true;
}

function openEdit(target: MonitorTarget) {
  editingTarget.value = target;
  Object.assign(form, {
    name: target.name,
    host: target.host,
    port: target.port,
    metricsPath: target.metricsPath,
    env: target.env,
    labelsText: labelsToText(target.labels),
    enabled: target.enabled,
    items: target.items.map((item) => ({ ...item, collapsed: true })),
  });
  dialogVisible.value = true;
}

function addItem() {
  form.items.push(createItem('JAVA_JMX_AGENT'));
}

function removeItem(index: number) {
  form.items.splice(index, 1);
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
    collapsed: false,
  });
}

async function submit() {
  if (!currentProject.value) {
    return;
  }
  const ok = await saveMonitorTarget(currentProject.value.id, {
    name: form.name,
    serviceName: form.name,
    host: form.host,
    port: form.port,
    metricsPath: form.metricsPath,
    env: form.env,
    labels: parseLabels(form.labelsText),
    items: form.items.map(toMonitorItem),
    enabled: form.enabled,
  }, editingTarget.value);
  if (ok) {
    dialogVisible.value = false;
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
    collapsed: false,
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

function parseLabels(value: string) {
  return Object.fromEntries(value.split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const index = line.indexOf('=');
      return index > 0 ? [line.slice(0, index).trim(), line.slice(index + 1).trim()] : [line, ''];
    })
    .filter(([, labelValue]) => labelValue));
}

function labelsToText(labels: Record<string, string>) {
  return Object.entries(labels).map(([key, value]) => `${key}=${value}`).join('\n');
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

.monitor-target-list,
.monitor-item-list {
  display: grid;
  gap: 10px;
}

.monitor-target-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface-soft);
}

.monitor-target-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.monitor-target-main strong {
  color: var(--text);
}

.monitor-target-main span,
.monitor-target-main small,
.monitor-item-header span {
  color: var(--muted);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.monitor-target-status,
.monitor-target-actions,
.monitor-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.monitor-item-header {
  justify-content: space-between;
  margin: 10px 0;
}

.monitor-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 800;
}

.monitor-form-grid,
.monitor-item-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.monitor-item-card {
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface-soft);
}

.monitor-item-title {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.monitor-item-title strong,
.monitor-item-title span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.monitor-item-toggle {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--primary);
  cursor: pointer;
}

.monitor-item-grid {
  padding-top: 12px;
}

.full-input {
  width: 100%;
}

@media (max-width: 760px) {
  .monitor-target-row,
  .monitor-form-grid,
  .monitor-item-grid {
    grid-template-columns: 1fr;
  }

  .monitor-target-actions,
  .monitor-item-header {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .monitor-item-title {
    grid-template-columns: auto auto minmax(0, 1fr);
  }

  .monitor-item-title .ant-btn {
    grid-column: 1 / -1;
    width: fit-content;
  }
}
</style>
