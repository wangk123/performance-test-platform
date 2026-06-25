<template>
  <section v-if="task" class="task-detail">
    <div class="panel task-detail-hero">
      <div>
        <div class="task-detail-nav">
          <a-button class="task-back-button" @click="$emit('back')">返回任务列表</a-button>
          <span class="eyebrow">Execution Detail</span>
        </div>
        <h2>{{ task.name }}</h2>
        <p>{{ script?.name }} · {{ taskStatusText(task.status) }}</p>
      </div>
    </div>

    <div v-if="task.status === 'FAILED'" class="panel task-failure-panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Failure Detail</span>
          <h2>执行失败</h2>
          <p>{{ task.errorMessage || '任务执行失败，请查看下方日志。' }}</p>
        </div>
      </div>
      <pre class="task-failure-log">{{ task.executionLogs || '暂无执行日志' }}</pre>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Aggregate Report</span>
          <h2>聚合报告</h2>
          <p>按接口汇总展示 JMeter 聚合指标。</p>
        </div>
      </div>
      <div class="summary-strip task-summary-strip">
        <div class="summary-cell"><span>Samples</span><strong>{{ task.summary.samples.toLocaleString() }}</strong></div>
        <div class="summary-cell"><span>Throughput</span><strong>{{ task.summary.throughput }}/s</strong></div>
        <div class="summary-cell"><span>Avg RT</span><strong>{{ task.summary.avgRt }}ms</strong></div>
        <div class="summary-cell"><span>P95</span><strong>{{ task.summary.p95 }}ms</strong></div>
        <div class="summary-cell"><span>Error</span><strong>{{ task.summary.errorRate }}%</strong></div>
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
            <tr v-if="!aggregateRows.length">
              <td colspan="12" class="aggregate-empty">暂无聚合数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Live Metrics</span>
          <h2>实时监控</h2>
          <p>按本次执行 ID 从 InfluxDB 汇总多节点接口指标。</p>
        </div>
      </div>
      <TaskMonitoringCharts :monitoring="task.monitoring" :fallback-metrics="metrics" />
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Target Metrics</span>
          <h2>被测目标监控</h2>
          <p>按本次执行绑定的 JMX Agent 目标打开 Grafana JVM 模板。</p>
        </div>
        <a-button v-if="targetMonitoring?.grafanaUrl" type="primary" :href="targetMonitoring.grafanaUrl" target="_blank">
          打开 Grafana
        </a-button>
      </div>
      <div class="monitor-target-list">
        <div v-for="target in targetMonitoringTargets" :key="target.id" class="monitor-target-chip">
          <strong>{{ target.serviceName }}</strong>
          <span>{{ target.env }} · {{ target.address }}</span>
        </div>
        <a-empty v-if="!targetMonitoringTargets.length" description="当前任务未绑定被测目标监控" />
      </div>
    </div>

    <section class="task-result-workbench">
      <div class="panel result-tree-panel">
        <div class="panel-header">
          <div>
            <span class="eyebrow">View Results Tree</span>
            <h2>查看结果树</h2>
            <p>仅展示最近 1000 条异常样本，按页读取。</p>
          </div>
        </div>
        <a-table
          class="workspace-table result-sample-table"
          :columns="sampleColumns"
          :data-source="pagedSamples"
          :pagination="samplePagination"
          :row-key="(record: TaskSample) => record.id"
          :custom-row="sampleRowEvents"
          :row-class-name="sampleRowClassName"
          :scroll="{ y: sampleTableScrollY }"
          size="small"
          :locale="{ emptyText: '暂无异常样本。' }"
          @change="onSampleTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'statusCode'">
              <a-tag :color="record.success ? 'success' : 'error'">{{ record.statusCode }}</a-tag>
            </template>
            <template v-else-if="column.key === 'label'">
              <div class="table-main-cell">
                <strong>{{ record.label }}</strong>
                <small>{{ record.threadName }}</small>
              </div>
            </template>
            <template v-else-if="column.key === 'elapsed'">
              <div class="table-main-cell table-main-cell-end">
                <strong>{{ record.elapsed }}ms</strong>
                <small>{{ record.message }}</small>
              </div>
            </template>
          </template>
        </a-table>
      </div>

      <div class="panel sample-detail-panel">
        <div class="panel-header">
          <div>
            <span class="eyebrow">Sampler Detail</span>
            <h2>{{ selectedSample?.label }}</h2>
            <p>{{ selectedSample?.statusCode }} · {{ selectedSample?.elapsed }}ms · {{ selectedSample?.message }} · {{ selectedSample?.threadName }}</p>
          </div>
        </div>
        <div class="sample-inspector">
          <div class="sample-inspector-toolbar">
            <a-segmented v-model:value="payloadMode" :options="payloadModeOptions" />
            <div class="sample-inspector-meta">
              <span>{{ activePayloadTitle }}</span>
              <strong>{{ activePayload.length.toLocaleString() }} chars</strong>
            </div>
          </div>
          <pre class="sample-payload-viewer">{{ activePayload || '当前结果没有保存该内容，请重新执行任务生成完整请求与响应。' }}</pre>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue';
import type { TableColumnsType, TableProps } from 'ant-design-vue';
import type { TaskSample, TestTask } from '../../types';
import { useTaskSchedule } from '../../composables/useTaskSchedule';
const TaskMonitoringCharts = defineAsyncComponent(() => import('./TaskMonitoringCharts.vue'));

const props = defineProps<{
  task: TestTask | null;
}>();

defineEmits<{
  (e: 'back'): void;
}>();

const {
  resultPage,
  pageSize,
  resultTotal,
  pagedSamples,
  selectedSample,
  selectedSampleId,
  taskStatusText,
  scriptById,
} = useTaskSchedule();

const payloadMode = ref<'request' | 'response'>('request');
const payloadModeOptions = [
  { label: '请求内容', value: 'request' },
  { label: '响应内容', value: 'response' },
];

const sampleTableScrollY = 'calc(var(--result-panel-height) - 220px)';

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
  showTotal: (total: number) => `${total} 条样本`,
  size: 'small' as const,
}));

function onSampleTableChange(pagination: { current?: number; pageSize?: number }) {
  if (pagination.current) {
    resultPage.value = pagination.current;
  }
  if (pagination.pageSize) {
    pageSize.value = pagination.pageSize;
  }
}

const sampleRowEvents: TableProps<TaskSample>['customRow'] = (record) => ({
  onClick: () => {
    selectedSampleId.value = record.id;
  },
});

const sampleRowClassName: TableProps<TaskSample>['rowClassName'] = (record) =>
  selectedSample.value?.id === record.id ? 'selected-table-row' : '';

const script = computed(() => (props.task ? scriptById(props.task.scriptId) : null));
const aggregateRows = computed(() => props.task?.aggregateRows ?? []);
const targetMonitoring = computed(() => props.task?.targetMonitoring ?? null);
const targetMonitoringTargets = computed(() => targetMonitoring.value?.targets ?? []);
const activePayload = computed(() => {
  const sample = selectedSample.value;
  if (!sample) {
    return '';
  }
  return payloadMode.value === 'request' ? buildRequestContent(sample) : buildResponseContent(sample);
});
const activePayloadTitle = computed(() => payloadMode.value === 'request' ? 'HTTP Request' : `HTTP ${selectedSample.value?.statusCode ?? '-'}`);
const metrics = computed(() => props.task?.metrics ?? []);

function buildRequestContent(sample: TaskSample) {
  const parts = [sample.requestLine, sample.requestHeaders, sample.requestBody].filter((part) => !!part?.trim());
  return parts.length ? parts.join('\n\n') : (sample.request ?? '');
}

function buildResponseContent(sample: TaskSample) {
  const parts: string[] = [];
  if (sample.statusCode) {
    parts.push(`HTTP ${sample.statusCode}${sample.message ? ` ${sample.message}` : ''}`);
  }
  if (sample.responseHeaders?.trim()) {
    parts.push(sample.responseHeaders);
  }
  if (sample.responseBody?.trim()) {
    parts.push(sample.responseBody);
  }
  if (sample.failureMessage?.trim()) {
    parts.push(`--- Failure Message ---\n${sample.failureMessage}`);
  }
  return parts.length ? parts.join('\n\n') : (sample.response ?? '');
}
</script>
