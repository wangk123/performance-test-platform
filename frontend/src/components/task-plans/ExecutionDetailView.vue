<template>
  <section v-if="execution" class="task-detail">
    <div class="panel task-detail-hero">
      <div>
        <div class="task-detail-nav">
          <a-button class="task-back-button" @click="$emit('back')">返回场景详情</a-button>
          <span class="eyebrow">Execution #{{ execution.id }}</span>
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
          :pagination="samplePagination"
          :row-key="(record: TaskSample) => record.id"
          :custom-row="sampleRowEvents"
          :row-class-name="sampleRowClassName"
          size="small"
          :locale="{ emptyText: '暂无异常样本。' }"
          @change="onSampleTableChange"
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
      </div>
      <div class="panel sample-detail-panel">
        <div class="panel-header">
          <div>
            <h2>{{ selectedSample?.label || '样本详情' }}</h2>
            <p v-if="selectedSample">{{ selectedSample.statusCode }} · {{ selectedSample.elapsed }}ms</p>
          </div>
        </div>
        <div class="sample-inspector">
          <a-spin :spinning="sampleDetailLoading">
            <a-segmented v-model:value="payloadMode" :options="payloadModeOptions" />
            <pre class="sample-payload-viewer">{{ activePayload || '暂无内容' }}</pre>
          </a-spin>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue';
import type { TableColumnsType, TableProps } from 'ant-design-vue';
import type { ExecutionDetail, TaskSample } from '../../types';
import { useTaskPlans } from '../../composables/useTaskPlans';

const TaskMonitoringCharts = defineAsyncComponent(() => import('../tasks/TaskMonitoringCharts.vue'));
const TargetServerMetricsPanel = defineAsyncComponent(() => import('../tasks/TargetServerMetricsPanel.vue'));
const TargetJvmMetricsPanel = defineAsyncComponent(() => import('../tasks/TargetJvmMetricsPanel.vue'));

const props = defineProps<{ execution: ExecutionDetail | null }>();
defineEmits<{ (e: 'back'): void }>();

const {
  resultPage,
  pageSize,
  resultTotal,
  pagedSamples,
  selectedSample,
  selectedSampleId,
  sampleDetailLoading,
  executionStatusText,
  toUiStatus,
  scriptById,
  stopActiveExecution,
} = useTaskPlans();

const payloadMode = ref<'request' | 'response'>('request');
const payloadModeOptions = [
  { label: '请求内容', value: 'request' },
  { label: '响应内容', value: 'response' },
];

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

const samplePagination = computed(() => ({
  current: resultPage.value,
  pageSize: pageSize.value,
  total: resultTotal.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  size: 'small' as const,
}));

function onSampleTableChange(pagination: { current?: number; pageSize?: number }) {
  if (pagination.current) resultPage.value = pagination.current;
  if (pagination.pageSize) pageSize.value = pagination.pageSize;
}

const sampleRowEvents: TableProps<TaskSample>['customRow'] = (record) => ({
  onClick: () => { selectedSampleId.value = record.id; },
});

const sampleRowClassName: TableProps<TaskSample>['rowClassName'] = (record) =>
  selectedSample.value?.id === record.id ? 'selected-table-row' : '';

const activePayload = computed(() => {
  const sample = selectedSample.value;
  if (!sample) return '';
  if (payloadMode.value === 'request') {
    const parts = [sample.requestLine, sample.requestHeaders, sample.requestBody].filter((p) => !!p?.trim());
    return parts.length ? parts.join('\n\n') : (sample.request ?? '');
  }
  const parts: string[] = [];
  if (sample.statusCode) parts.push(`HTTP ${sample.statusCode}`);
  if (sample.responseHeaders?.trim()) parts.push(sample.responseHeaders);
  if (sample.responseBody?.trim()) parts.push(sample.responseBody);
  return parts.length ? parts.join('\n\n') : (sample.response ?? '');
});
</script>
