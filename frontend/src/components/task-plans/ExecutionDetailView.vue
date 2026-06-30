<template>
  <section v-if="execution" class="task-detail">
    <div class="panel task-detail-hero">
      <div>
        <div class="task-detail-nav">
          <div class="task-detail-nav-start">
            <a-button class="task-back-button" @click="$emit('back')">返回场景详情</a-button>
            <span class="eyebrow">Scenario</span>
          </div>
          <a-dropdown
            v-model:open="historyDropdownOpen"
            :trigger="['click']"
            placement="bottomRight"
            overlay-class-name="execution-history-dropdown"
            @openChange="onHistoryDropdownOpenChange"
          >
            <button
              type="button"
              class="execution-nav-trigger"
              :class="{ 'is-open': historyDropdownOpen }"
            >
              <HistoryOutlined class="execution-nav-icon" />
              <span class="execution-nav-label">查看历史记录</span>
              <DownOutlined class="execution-nav-arrow" />
            </button>
            <template #overlay>
              <div class="execution-history-panel" @mousedown.stop @click.stop>
                <div class="execution-history-header">
                  <div>
                    <span class="execution-history-eyebrow">History</span>
                    <strong class="execution-history-title">执行记录</strong>
                  </div>
                  <span class="execution-history-count-badge">{{ historyExecutions.length }}</span>
                </div>
                <div class="execution-history-toolbar">
                  <span class="execution-history-hint">
                    {{ historyEditMode ? `已选 ${selectedExecutionIds.length} 条` : '点击记录切换查看' }}
                  </span>
                  <div class="execution-history-actions">
                    <template v-if="historyEditMode">
                      <button type="button" class="execution-history-action" @click.stop.prevent="selectAllExecutions">全选</button>
                      <button type="button" class="execution-history-action" @click.stop.prevent="deselectAllExecutions">取消全选</button>
                      <button
                        type="button"
                        class="execution-history-action is-danger"
                        :disabled="selectedExecutionIds.length === 0"
                        @click.stop.prevent="batchDeleteExecutions"
                      >删除{{ selectedExecutionIds.length ? ` (${selectedExecutionIds.length})` : '' }}</button>
                    </template>
                    <button
                      v-else
                      type="button"
                      class="execution-history-icon-btn"
                      :disabled="!historyExecutions.length"
                      title="编辑"
                      @click.stop.prevent="enterHistoryEditMode"
                    >
                      <EditOutlined />
                    </button>
                  </div>
                </div>
                <div class="execution-history-list">
                  <div
                    v-for="item in historyExecutions"
                    :key="item.id"
                    class="execution-history-item"
                    :class="{
                      active: item.id === execution.id,
                      selected: selectedExecutionIds.includes(item.id),
                      'is-selecting': historyEditMode,
                    }"
                  >
                    <a-checkbox
                      v-if="historyEditMode"
                      class="execution-history-checkbox"
                      :checked="selectedExecutionIds.includes(item.id)"
                      @click.stop
                      @change="(e: any) => toggleExecutionSelect(item.id, e.target.checked)"
                    />
                    <button
                      type="button"
                      class="execution-history-item-body"
                      @click="onHistoryItemClick(item.id)"
                    >
                      <strong>{{ item.executionName || formatDate(item.startedAt || item.createdAt) }}</strong>
                      <small>{{ formatDate(item.startedAt || item.createdAt) }} · {{ formatDuration(item.durationMs) }}</small>
                    </button>
                    <span class="execution-history-status" :class="historyStatusClass(item.status)">
                      {{ historyStatusText(item.status) }}
                    </span>
                  </div>
                  <div v-if="!historyExecutions.length" class="execution-history-empty">暂无历史记录</div>
                </div>
              </div>
            </template>
          </a-dropdown>
        </div>
        <h2>{{ execution.scenarioName }}</h2>
        <p>{{ script?.name }} · {{ executionStatusText(uiStatus) }}</p>
      </div>
      <a-button
        v-if="uiStatus === 'RUNNING' || uiStatus === 'PENDING' || uiStatus === 'STOPPING'"
        danger
        @click="stopActiveExecution"
      >停止执行</a-button>
    </div>

    <div v-if="uiStatus === 'FAILED' || uiStatus === 'INTERRUPTED'" class="panel task-failure-panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Failure Detail</span>
          <h2>{{ uiStatus === 'INTERRUPTED' ? '执行已停止' : '执行失败' }}</h2>
          <p>{{ execution.errorMessage || '请查看下方日志。' }}</p>
        </div>
      </div>
      <pre class="task-failure-log">{{ execution.executionLogs || '暂无执行日志' }}</pre>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Aggregate Report</span>
          <h2>聚合报告</h2>
          <span v-if="accuracyLabel" class="aggregate-accuracy-badge" :class="accuracyClass">{{ accuracyLabel }}</span>
        </div>
      </div>
      <div class="summary-strip task-summary-strip">
        <div class="summary-cell"><span>Samples</span><strong>{{ execution.summary.samples.toLocaleString() }}</strong></div>
        <div class="summary-cell"><span>Throughput</span><strong>{{ execution.summary.throughput }}/s</strong></div>
        <div class="summary-cell"><span>Avg RT</span><strong>{{ execution.summary.avgRt }}ms</strong></div>
        <div class="summary-cell"><span>P95</span><strong>{{ execution.summary.p95 }}ms</strong></div>
        <div class="summary-cell"><span>Error</span><strong>{{ execution.summary.errorRate }}%</strong></div>
      </div>
      <div class="aggregate-table-wrap">
        <table class="aggregate-table">
          <thead>
            <tr>
              <th>Label</th><th>Thread Group</th><th># Samples</th><th>Average</th><th>Median</th><th>90% Line</th><th>95% Line</th><th>99% Line</th><th>Min</th><th>Max</th><th>Error %</th><th>Throughput</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in aggregateRows" :key="`${row.threadName}-${row.label}`">
              <td>{{ row.label }}</td>
              <td>{{ row.threadName }}</td>
              <td>{{ row.samples.toLocaleString() }}</td>
              <td>{{ row.average }}ms</td>
              <td>{{ row.median }}ms</td>
              <td>{{ row.p90 }}ms</td>
              <td>{{ row.p95 }}ms</td>
              <td>{{ row.p99 }}ms</td>
              <td>{{ row.min }}ms</td>
              <td>{{ row.max }}ms</td>
              <td>{{ row.errorRate }}%</td>
              <td>{{ row.throughput }}/s</td>
            </tr>
            <tr v-if="!aggregateRows.length"><td colspan="12" class="aggregate-empty">暂无聚合数据</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div><span class="eyebrow">Live Metrics</span><h2>实时监控</h2></div>
      </div>
      <TaskMonitoringCharts :monitoring="execution.monitoring" />
    </div>

    <div class="panel">
      <div class="panel-header">
        <div><span class="eyebrow">Target Metrics</span><h2>被测目标监控</h2></div>
      </div>
      <TargetServerMetricsPanel
        v-if="targetMonitoring?.serverTargets?.length"
        :execution-id="execution.id"
        :targets="targetMonitoring.serverTargets"
        :polling="targetMonitoringPolling"
        :refresh-interval-ms="5000"
      />
      <TargetJvmMetricsPanel
        v-if="targetMonitoring?.jvmInstances?.length"
        :execution-id="execution.id"
        :instances="targetMonitoring.jvmInstances"
        :polling="targetMonitoringPolling"
        :refresh-interval-ms="5000"
      />
      <a-empty v-if="!targetMonitoring?.targets?.length" description="未绑定被测目标监控" />
    </div>

    <section class="task-result-workbench">
      <div class="panel result-tree-panel">
        <div class="panel-header">
          <div><span class="eyebrow">View Results Tree</span><h2>异常样本</h2></div>
        </div>
        <a-table
          class="workspace-table result-sample-table"
          :columns="sampleColumns"
          :data-source="pagedSamples"
          :pagination="false"
          :row-key="(record: TaskSample) => record.id"
          :custom-row="sampleRowEvents"
          :row-class-name="sampleRowClassName"
          size="small"
          :locale="{ emptyText: '暂无异常样本。' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'statusCode'">
              <a-tag :color="record.success ? 'success' : 'error'">{{ record.statusCode }}</a-tag>
            </template>
            <template v-else-if="column.key === 'label'">
              <div class="table-main-cell"><strong>{{ record.label }}</strong><small>{{ record.threadName }}</small></div>
            </template>
            <template v-else-if="column.key === 'elapsed'">
              <div class="table-main-cell table-main-cell-end"><strong>{{ record.elapsed }}ms</strong><small>{{ record.message }}</small></div>
            </template>
          </template>
        </a-table>
        <a-pagination
          v-if="resultTotal > 0"
          class="result-sample-pagination"
          :current="resultPage"
          :total="resultTotal"
          :page-size="pageSize"
          show-size-changer
          show-quick-jumper
          show-less-items
          size="small"
          :page-size-options="['10', '20', '50']"
          :show-total="paginationShowTotal"
          @change="onPaginationChange"
        />
      </div>
      <div class="panel sample-detail-panel">
        <div class="panel-header sample-detail-header">
          <div>
            <h2>{{ selectedSample?.label || '样本详情' }}</h2>
          </div>
        </div>
        <div class="sample-inspector">
          <a-spin :spinning="sampleDetailLoading" wrapper-class-name="sample-inspector-spin">
            <a-tabs v-if="selectedSample" v-model:activeKey="payloadTab" class="sample-inspector-tabs">
              <a-tab-pane key="request" tab="请求内容">
                <div class="sample-payload">
                  <div class="sample-section sample-section-headers">
                    <div class="sample-section-title">Headers</div>
                    <pre class="sample-headers">{{ requestHeadersText || '无请求头' }}</pre>
                  </div>
                  <div class="sample-section sample-section-body">
                    <div class="sample-section-title">
                      <span>Body</span>
                      <a-tag v-if="hasRequestBody" class="sample-section-tag">{{ requestBodyLanguage.toUpperCase() }}</a-tag>
                    </div>
                    <pre v-if="hasRequestBody" class="sample-body">{{ requestBodyFormatted }}</pre>
                    <div v-else class="sample-empty">无请求体</div>
                  </div>
                </div>
              </a-tab-pane>
              <a-tab-pane key="response" tab="响应内容">
                <div class="sample-payload">
                  <div class="sample-section sample-section-headers">
                    <div class="sample-section-title">Headers</div>
                    <pre class="sample-headers">{{ responseHeadersText || '无响应头' }}</pre>
                  </div>
                  <div class="sample-section sample-section-body">
                    <div class="sample-section-title">
                      <span>Body</span>
                      <a-tag v-if="hasResponseBody" class="sample-section-tag">{{ responseBodyLanguage.toUpperCase() }}</a-tag>
                    </div>
                    <pre v-if="hasResponseBody" class="sample-body">{{ responseBodyFormatted }}</pre>
                    <div v-else class="sample-empty">无响应体</div>
                  </div>
                </div>
              </a-tab-pane>
              <a-tab-pane v-if="assertionMessage" key="assertion">
                <template #tab>
                  <span class="sample-tab-assertion">断言结果<span class="sample-tab-dot" /></span>
                </template>
                <pre class="sample-assertion">{{ assertionMessage }}</pre>
              </a-tab-pane>
            </a-tabs>
            <div v-else class="sample-empty sample-empty-large">请选择左侧异常样本查看详情</div>
          </a-spin>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue';
import type { TableColumnsType, TableProps } from 'ant-design-vue';
import { message } from 'ant-design-vue';
import { DownOutlined, EditOutlined, HistoryOutlined } from '@ant-design/icons-vue';
import type { ExecutionDetail, ScenarioExecution, TaskSample } from '../../types';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { listExecutionsApi, deleteExecutionsApi, toUiStatus } from '../../api/task-plans';
import { formatDate } from '../../utils/format';
import { detectHttpBodyLanguage, formatHttpBodyAuto } from '../../utils/http-request-config';

const TaskMonitoringCharts = defineAsyncComponent(() => import('../tasks/TaskMonitoringCharts.vue'));
const TargetServerMetricsPanel = defineAsyncComponent(() => import('../tasks/TargetServerMetricsPanel.vue'));
const TargetJvmMetricsPanel = defineAsyncComponent(() => import('../tasks/TargetJvmMetricsPanel.vue'));

const props = defineProps<{ execution: ExecutionDetail | null }>();
const emit = defineEmits<{ (e: 'back'): void }>();

const {
  resultPage,
  pageSize,
  resultTotal,
  pagedSamples,
  selectedSample,
  selectedSampleId,
  sampleDetailLoading,
  executionStatusText,
  scriptById,
  stopActiveExecution,
  openExecution,
} = useTaskPlans();

const payloadTab = ref<'request' | 'response' | 'assertion'>('request');
const historyExecutions = ref<ScenarioExecution[]>([]);
const selectedExecutionIds = ref<number[]>([]);
const historyEditMode = ref(false);
const historyDropdownOpen = ref(false);

onMounted(() => {
  if (props.execution) loadHistoryExecutions();
});

watch(() => props.execution?.id, () => {
  selectedExecutionIds.value = [];
  historyEditMode.value = false;
});

function loadHistoryExecutions() {
  if (!props.execution) return;
  listExecutionsApi(props.execution.scenarioId).then(list => {
    historyExecutions.value = list;
  }).catch(() => {
    historyExecutions.value = [];
  });
}

const uiStatus = computed(() => (props.execution ? toUiStatus(props.execution.status) : 'PENDING'));
const script = computed(() => (props.execution ? scriptById(props.execution.scriptVersionId) : null));
const aggregateRows = computed(() => props.execution?.aggregateRows ?? []);

const accuracyLabel = computed(() => {
  const accuracy = props.execution?.summary.accuracy;
  if (accuracy === 'final') return '最终精确报告';
  if (accuracy === 'final_partial') return '中断后的最终报告（不完整）';
  if (accuracy === 'live') return '实时精确（每 3 秒刷新）';
  return '';
});
const accuracyClass = computed(() => {
  const accuracy = props.execution?.summary.accuracy;
  if (accuracy === 'final') return 'is-final';
  if (accuracy === 'final_partial') return 'is-partial';
  if (accuracy === 'live') return 'is-live';
  return '';
});
const targetMonitoring = computed(() => props.execution?.targetMonitoring ?? null);
const targetMonitoringPolling = computed(() => ['RUNNING', 'PENDING', 'STOPPING'].includes(uiStatus.value));

const sampleColumns: TableColumnsType<TaskSample> = [
  { title: '状态', key: 'statusCode', width: 72 },
  { title: '样本', key: 'label', ellipsis: true },
  { title: '耗时', key: 'elapsed', width: 120, align: 'right' },
];

function formatDuration(ms: number | null) {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms}ms`;
  return `${Math.round(ms / 1000)}s`;
}

function onHistoryDropdownOpenChange(open: boolean) {
  if (!open) {
    historyEditMode.value = false;
    selectedExecutionIds.value = [];
  }
}

function enterHistoryEditMode() {
  historyEditMode.value = true;
  historyDropdownOpen.value = true;
}

function selectAllExecutions() {
  selectedExecutionIds.value = historyExecutions.value.map(item => item.id);
  historyDropdownOpen.value = true;
}

function deselectAllExecutions() {
  selectedExecutionIds.value = [];
  historyDropdownOpen.value = true;
}

function toggleExecutionSelect(id: number, checked: boolean) {
  if (checked) {
    if (!selectedExecutionIds.value.includes(id)) {
      selectedExecutionIds.value.push(id);
    }
  } else {
    selectedExecutionIds.value = selectedExecutionIds.value.filter(i => i !== id);
  }
}

function onHistoryItemClick(executionId: number) {
  if (historyEditMode.value) {
    const checked = !selectedExecutionIds.value.includes(executionId);
    toggleExecutionSelect(executionId, checked);
    return;
  }
  switchToExecution(executionId);
}

function switchToExecution(executionId: number) {
  if (executionId === props.execution?.id) return;
  openExecution({ id: executionId, projectId: props.execution!.projectId } as ScenarioExecution);
}

function historyStatusText(status: ScenarioExecution['status']) {
  const ui = toUiStatus(status);
  const map: Record<string, string> = {
    PENDING: '排队中', RUNNING: '运行中', STOPPING: '停止中',
    SUCCESS: '成功', FAILED: '失败', INTERRUPTED: '已停止',
  };
  return map[ui] || status;
}

function historyStatusClass(status: ScenarioExecution['status']) {
  const ui = toUiStatus(status);
  return {
    pending: ui === 'PENDING', running: ui === 'RUNNING' || ui === 'STOPPING',
    success: ui === 'SUCCESS', error: ui === 'FAILED' || ui === 'INTERRUPTED',
  };
}

async function batchDeleteExecutions() {
  const ids = selectedExecutionIds.value;
  if (!ids.length) return;
  try {
    await deleteExecutionsApi(ids);
    message.success(`已删除 ${ids.length} 条记录`);
    selectedExecutionIds.value = [];
    historyEditMode.value = false;
    loadHistoryExecutions();
    if (ids.includes(props.execution!.id)) {
      const remaining = historyExecutions.value.filter(e => !ids.includes(e.id));
      if (remaining.length > 0) {
        switchToExecution(remaining[0].id);
      } else {
        emit('back');
      }
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败');
  }
}

function onPaginationChange(page: number, size: number) {
  resultPage.value = page;
  pageSize.value = size;
}

function paginationShowTotal(total: number) {
  return `共 ${total} 条`;
}

const sampleRowEvents: TableProps<TaskSample>['customRow'] = (record) => ({
  onClick: () => { selectedSampleId.value = record.id; },
});

const sampleRowClassName: TableProps<TaskSample>['rowClassName'] = (record) =>
  selectedSample.value?.id === record.id ? 'selected-table-row' : '';

const requestHeadersText = computed(() => {
  const sample = selectedSample.value;
  if (!sample) return '';
  const parts: string[] = [];
  if (sample.requestLine?.trim()) parts.push(sample.requestLine.trim());
  if (sample.requestHeaders?.trim()) parts.push(sample.requestHeaders.trim());
  return parts.join('\n');
});
const responseHeadersText = computed(() => {
  const sample = selectedSample.value;
  if (!sample) return '';
  const parts: string[] = [];
  if (sample.statusCode) parts.push(`HTTP ${sample.statusCode}`);
  if (sample.responseHeaders?.trim()) parts.push(sample.responseHeaders.trim());
  return parts.join('\n');
});
const requestBodyRaw = computed(() => selectedSample.value?.requestBody?.trim() ?? '');
const responseBodyRaw = computed(() => selectedSample.value?.responseBody?.trim() ?? '');
const hasRequestBody = computed(() => requestBodyRaw.value.length > 0);
const hasResponseBody = computed(() => responseBodyRaw.value.length > 0);
const requestBodyLanguage = computed(() => detectHttpBodyLanguage(requestBodyRaw.value));
const responseBodyLanguage = computed(() => detectHttpBodyLanguage(responseBodyRaw.value));
const requestBodyFormatted = computed(() => formatHttpBodyAuto(requestBodyRaw.value));
const responseBodyFormatted = computed(() => formatHttpBodyAuto(responseBodyRaw.value));
const assertionMessage = computed(() => selectedSample.value?.failureMessage?.trim() ?? '');

watch(selectedSample, (sample) => {
  if (!sample) return;
  if (payloadTab.value === 'assertion' && !sample.failureMessage?.trim()) {
    payloadTab.value = 'request';
  }
});
</script>
