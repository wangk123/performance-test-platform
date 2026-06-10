<template>
  <section v-if="task" class="task-detail">
    <div class="panel task-detail-hero">
      <div>
        <div class="task-detail-nav">
          <el-button class="task-back-button" @click="$emit('back')">返回任务列表</el-button>
          <span class="eyebrow">Execution Detail</span>
        </div>
        <h2>{{ task.name }}</h2>
        <p>{{ script?.name }} · {{ task.environment }} · {{ taskStatusText(task.status) }}</p>
      </div>
      <div class="row-actions">
        <el-button class="task-fixed-button" @click="$emit('edit', task)">编辑</el-button>
        <el-button class="task-fixed-button" type="primary" @click="runTask(task)">立即执行</el-button>
      </div>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Aggregate Report</span>
          <h2>聚合报告</h2>
          <p>按线程组和接口展示 JMeter 聚合指标。</p>
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

    <section class="task-chart-grid">
      <div class="panel task-chart-card">
        <h2>TPS 视图</h2>
        <p>横轴为执行时间，纵轴为 TPS。</p>
        <div class="axis-chart">
          <span v-for="tick in tpsTicks" :key="`${tick.label}-${tick.top}`" class="axis-label axis-y" :style="{ top: tick.top }">{{ tick.label }}</span>
          <span v-for="tick in tpsTimeTicks" :key="`${tick.label}-${tick.left}`" class="axis-label axis-x" :style="{ left: tick.left }">{{ tick.label }}</span>
          <svg viewBox="0 0 420 172" preserveAspectRatio="none" @mouseleave="clearMetric">
            <polyline :points="tpsPoints" fill="none" stroke="#1f6f5f" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
            <polyline :points="targetTpsPoints" fill="none" stroke="#d98724" stroke-width="1.1" stroke-linecap="round" stroke-dasharray="5 6" vector-effect="non-scaling-stroke" />
            <circle v-for="point in tpsDotPoints" :key="point.index" :cx="point.x" :cy="point.y" r="12" fill="transparent" @mouseenter="showMetric(point.index, 'tps')" />
          </svg>
          <div v-if="tpsHoveredMetric" class="chart-tooltip" :style="{ left: tooltipLeft, top: tooltipTop }">
            <strong>{{ tpsHoveredMetric.time }}</strong>
            <span>TPS {{ tpsHoveredMetric.tps }}/s</span>
            <span>目标 {{ tpsHoveredMetric.targetTps }}/s</span>
          </div>
        </div>
        <div class="chart-legend"><span>当前 TPS</span><span class="warning">目标 TPS</span></div>
      </div>

      <div class="panel task-chart-card">
        <h2>响应时间视图</h2>
        <p>按采样结果展示成功与失败响应时间，单位 ms。</p>
        <div class="axis-chart">
          <span v-for="tick in responseTicks" :key="`${tick.label}-${tick.top}`" class="axis-label axis-y" :style="{ top: tick.top }">{{ tick.label }}</span>
          <span v-for="tick in responseTimeTicks" :key="`${tick.label}-${tick.left}`" class="axis-label axis-x" :style="{ left: tick.left }">{{ tick.label }}</span>
          <svg viewBox="0 0 420 172" preserveAspectRatio="none" @mouseleave="clearMetric">
            <polyline :points="successResponsePoints" fill="none" stroke="#1f6f5f" stroke-width="1.25" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
            <polyline :points="failedResponsePoints" fill="none" stroke="#c24132" stroke-width="1.25" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
            <circle v-for="point in responseDotPoints" :key="point.index" :cx="point.x" :cy="point.y" r="10" fill="transparent" @mouseenter="showMetric(point.index, 'rt')" />
          </svg>
          <div v-if="rtHoveredSample" class="chart-tooltip" :style="{ left: tooltipLeft, top: tooltipTop }">
            <strong>{{ rtHoveredSample.time }}</strong>
            <span>{{ rtHoveredSample.success ? '成功' : '失败' }} {{ rtHoveredSample.elapsed }}ms</span>
            <span>{{ rtHoveredSample.statusCode }} · {{ rtHoveredSample.label }}</span>
          </div>
        </div>
        <div class="chart-legend"><span>成功</span><span class="danger">失败</span></div>
      </div>
    </section>

    <section class="task-result-workbench">
      <div class="panel result-tree-panel">
        <div class="panel-header">
          <div>
            <span class="eyebrow">View Results Tree</span>
            <h2>查看结果树</h2>
          </div>
        </div>
        <el-segmented v-model="resultFilter" :options="resultFilterOptions" />
        <div class="sample-list">
          <button
            v-for="sample in pagedSamples"
            :key="sample.id"
            class="sample-row"
            :class="{ selected: selectedSample?.id === sample.id }"
            type="button"
            @click="selectedSampleId = sample.id"
          >
            <span class="status" :class="sample.success ? 'success' : 'error'">{{ sample.statusCode }}</span>
            <span class="sample-row-main">
              <strong>{{ sample.label }}</strong>
              <small>{{ sample.threadName }}</small>
            </span>
            <span class="sample-row-meta">
              <strong>{{ sample.elapsed }}ms</strong>
              <small>{{ sample.message }}</small>
            </span>
          </button>
        </div>
        <div class="result-pagination">
          <span>{{ resultSamples.length }} 条样本</span>
          <el-pagination
            v-model:current-page="resultPage"
            v-model:page-size="pageSize"
            small
            layout="sizes, prev, pager, next, jumper"
            :page-sizes="[10, 20, 50]"
            :pager-count="5"
            :total="resultSamples.length"
          />
        </div>
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
            <el-segmented v-model="payloadMode" :options="payloadModeOptions" />
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
import { computed, ref } from 'vue';
import type { TestTask } from '../../types';
import { useTaskSchedule } from '../../composables/useTaskSchedule';

const props = defineProps<{
  task: TestTask | null;
}>();

defineEmits<{
  (e: 'back'): void;
  (e: 'edit', task: TestTask): void;
}>();

const {
  resultFilter,
  resultPage,
  pageSize,
  resultSamples,
  pagedSamples,
  selectedSample,
  selectedSampleId,
  taskStatusText,
  scriptById,
  runTask,
} = useTaskSchedule();

const resultFilterOptions = [
  { label: '全部', value: 'ALL' },
  { label: '仅错误', value: 'ERROR' },
  { label: '仅成功', value: 'SUCCESS' },
];
const payloadMode = ref<'request' | 'response'>('request');
const hoveredMetricIndex = ref<number | null>(null);
const hoveredChart = ref<'tps' | 'rt' | null>(null);
const payloadModeOptions = [
  { label: '请求内容', value: 'request' },
  { label: '响应内容', value: 'response' },
];

const script = computed(() => (props.task ? scriptById(props.task.scriptId) : null));
const aggregateRows = computed(() => props.task?.aggregateRows ?? []);
const activePayload = computed(() => payloadMode.value === 'request' ? selectedSample.value?.request ?? '' : selectedSample.value?.response ?? '');
const activePayloadTitle = computed(() => payloadMode.value === 'request' ? 'HTTP Request' : `HTTP ${selectedSample.value?.statusCode ?? '-'}`);
const metrics = computed(() => props.task?.metrics ?? []);
const samples = computed(() => props.task?.samples ?? []);
const tpsMax = computed(() => axisMax(metrics.value.flatMap((item) => [item.tps, item.targetTps])));
const responseMax = computed(() => axisMax(samples.value.map((item) => item.elapsed)));
const tpsTicks = computed(() => buildTicks(tpsMax.value));
const responseTicks = computed(() => buildTicks(responseMax.value));
const tpsTimeTicks = computed(() => timeTicks(metrics.value.map((item) => item.time)));
const responseTimeTicks = computed(() => timeTicks(samples.value.map((item) => item.time)));
const hoveredMetric = computed(() => hoveredMetricIndex.value === null ? null : metrics.value[hoveredMetricIndex.value] ?? null);
const hoveredSample = computed(() => hoveredMetricIndex.value === null ? null : samples.value[hoveredMetricIndex.value] ?? null);
const tpsHoveredMetric = computed(() => hoveredChart.value === 'tps' ? hoveredMetric.value : null);
const rtHoveredSample = computed(() => hoveredChart.value === 'rt' ? hoveredSample.value : null);
const hoveredPoint = computed(() => hoveredMetricIndex.value === null ? null : hoveredChart.value === 'tps' ? pointAt(hoveredMetricIndex.value, 'tps', tpsMax.value) : responsePointAt(hoveredMetricIndex.value));
const tooltipLeft = computed(() => `${Math.min(330, Math.max(58, hoveredPoint.value?.x ?? 58))}px`);
const tooltipTop = computed(() => `${Math.max(18, (hoveredPoint.value?.y ?? 28) - 8)}px`);

function timeTicks(values: string[]) {
  return [
    { label: values[0] ?? '-', left: '44px' },
    { label: values[Math.floor(values.length / 2)] ?? '-', left: '50%' },
    { label: values.at(-1) ?? '-', left: 'calc(100% - 54px)' },
  ];
}

function buildPoints(key: 'tps' | 'targetTps' | 'avgRt' | 'p90' | 'p95', max: number) {
  return metrics.value
    .map((_, index) => {
      const point = pointAt(index, key, max);
      return `${point.x},${point.y}`;
    })
    .join(' ');
}

function showMetric(index: number, key: 'tps' | 'rt') {
  hoveredMetricIndex.value = index; hoveredChart.value = key;
}

function clearMetric() {
  hoveredMetricIndex.value = null; hoveredChart.value = null;
}

function pointAt(index: number, key: 'tps' | 'targetTps' | 'avgRt' | 'p90' | 'p95', max: number) {
  const width = 420;
  const height = 172;
  const step = metrics.value.length > 1 ? width / (metrics.value.length - 1) : 0;
  const y = height - ((metrics.value[index]?.[key] ?? 0) / max) * height;
  return { index, x: index * step, y: Math.max(0, Math.min(height, y)) };
}

function responsePointAt(index: number) {
  const width = 420;
  const height = 172;
  const step = samples.value.length > 1 ? width / (samples.value.length - 1) : 0;
  const y = height - ((samples.value[index]?.elapsed ?? 0) / responseMax.value) * height;
  return { index, x: index * step, y: Math.max(0, Math.min(height, y)) };
}

function axisMax(values: number[]) {
  const max = Math.max(1, ...values);
  const base = Math.pow(10, Math.max(0, Math.floor(Math.log10(max)) - 1));
  return Math.ceil(max / base) * base;
}

function buildTicks(max: number) {
  return [max, max * 0.75, max * 0.5, max * 0.25, 0].map((value, index) => ({
    label: `${Math.round(value)}`,
    top: `${10 + index * 43}px`,
  }));
}

const tpsPoints = computed(() => buildPoints('tps', tpsMax.value));
const targetTpsPoints = computed(() => buildPoints('targetTps', tpsMax.value));
const tpsDotPoints = computed(() => metrics.value.map((_, index) => pointAt(index, 'tps', tpsMax.value)));
const responseDotPoints = computed(() => samples.value.map((_, index) => responsePointAt(index)));
const successResponsePoints = computed(() => buildResponsePoints(true));
const failedResponsePoints = computed(() => buildResponsePoints(false));
function buildResponsePoints(success: boolean) {
  return samples.value
    .map((sample, index) => sample.success === success ? responsePointAt(index) : null)
    .filter((point): point is { index: number; x: number; y: number } => point !== null)
    .map((point) => `${point.x},${point.y}`)
    .join(' ');
}
</script>
