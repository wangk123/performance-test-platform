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
          <p>当前任务执行结果摘要，后续接入报告生成接口。</p>
        </div>
      </div>
      <div class="summary-strip task-summary-strip">
        <div class="summary-cell"><span>Samples</span><strong>{{ task.summary.samples.toLocaleString() }}</strong></div>
        <div class="summary-cell"><span>Throughput</span><strong>{{ task.summary.throughput }}/s</strong></div>
        <div class="summary-cell"><span>Avg RT</span><strong>{{ task.summary.avgRt }}ms</strong></div>
        <div class="summary-cell"><span>P95</span><strong>{{ task.summary.p95 }}ms</strong></div>
        <div class="summary-cell"><span>Error</span><strong>{{ task.summary.errorRate }}%</strong></div>
      </div>
    </div>

    <section class="task-chart-grid">
      <div class="panel task-chart-card">
        <h2>TPS 视图</h2>
        <p>横轴为执行时间，纵轴为 TPS。</p>
        <div class="axis-chart">
          <span v-for="tick in tpsTicks" :key="tick.label" class="axis-label axis-y" :style="{ top: tick.top }">{{ tick.label }}</span>
          <span v-for="tick in timeTicks" :key="tick.label" class="axis-label axis-x" :style="{ left: tick.left }">{{ tick.label }}</span>
          <svg viewBox="0 0 420 172" preserveAspectRatio="none">
            <polyline :points="tpsPoints" fill="none" stroke="#1f6f5f" stroke-width="4" />
            <polyline :points="targetTpsPoints" fill="none" stroke="#d98724" stroke-width="3" stroke-dasharray="6 5" />
          </svg>
        </div>
        <div class="chart-legend"><span>当前 TPS</span><span class="warning">目标 TPS</span></div>
      </div>

      <div class="panel task-chart-card">
        <h2>响应时间视图</h2>
        <p>展示 Avg、P90、P95 响应时间趋势，单位 ms。</p>
        <div class="axis-chart">
          <span v-for="tick in rtTicks" :key="tick.label" class="axis-label axis-y" :style="{ top: tick.top }">{{ tick.label }}</span>
          <span v-for="tick in timeTicks" :key="tick.label" class="axis-label axis-x" :style="{ left: tick.left }">{{ tick.label }}</span>
          <svg viewBox="0 0 420 172" preserveAspectRatio="none">
            <polyline :points="p95Points" fill="none" stroke="#1f6f5f" stroke-width="4" />
            <polyline :points="p90Points" fill="none" stroke="#d98724" stroke-width="3" />
            <polyline :points="avgRtPoints" fill="none" stroke="#c24132" stroke-width="2.5" />
          </svg>
        </div>
        <div class="chart-legend"><span>P95</span><span class="warning">P90</span><span class="danger">Avg</span></div>
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
            <span>
              <strong>{{ sample.label }}</strong>
              <small>{{ sample.threadName }} · {{ sample.message }}</small>
            </span>
            <strong>{{ sample.elapsed }}ms</strong>
          </button>
        </div>
        <div class="result-pagination">
          <span>第 {{ resultPage }} / {{ resultPageCount }} 页 · {{ resultSamples.length }} 条</span>
          <el-pagination
            v-model:current-page="resultPage"
            small
            layout="prev, pager, next"
            :page-size="pageSize"
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
        <div class="payload-grid">
          <div class="payload-card">
            <header><strong>请求内容</strong><span>HTTP Request</span></header>
            <pre>{{ selectedSample?.request }}</pre>
          </div>
          <div class="payload-card">
            <header><strong>响应内容</strong><span>HTTP {{ selectedSample?.statusCode }}</span></header>
            <pre>{{ selectedSample?.response }}</pre>
          </div>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
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
  resultPageCount,
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

const script = computed(() => (props.task ? scriptById(props.task.scriptId) : null));
const tpsTicks = [
  { label: '600', top: '10px' },
  { label: '450', top: '55px' },
  { label: '300', top: '100px' },
  { label: '150', top: '145px' },
  { label: '0', top: '184px' },
];
const rtTicks = [
  { label: '800', top: '10px' },
  { label: '600', top: '55px' },
  { label: '400', top: '100px' },
  { label: '200', top: '145px' },
  { label: '0', top: '184px' },
];
const timeTicks = computed(() => [
  { label: props.task?.metrics[0]?.time ?? '16:30', left: '48px' },
  { label: props.task?.metrics[Math.floor((props.task?.metrics.length ?? 1) / 2)]?.time ?? '16:35', left: '42%' },
  { label: props.task?.metrics.at(-1)?.time ?? '16:40', left: 'calc(100% - 54px)' },
]);

function buildPoints(key: 'tps' | 'targetTps' | 'avgRt' | 'p90' | 'p95', max: number) {
  const metrics = props.task?.metrics ?? [];
  const width = 420;
  const height = 172;
  const step = metrics.length > 1 ? width / (metrics.length - 1) : width;
  return metrics
    .map((point, index) => {
      const y = height - (point[key] / max) * height;
      return `${index * step},${Math.max(0, Math.min(height, y))}`;
    })
    .join(' ');
}

const tpsPoints = computed(() => buildPoints('tps', 600));
const targetTpsPoints = computed(() => buildPoints('targetTps', 600));
const avgRtPoints = computed(() => buildPoints('avgRt', 800));
const p90Points = computed(() => buildPoints('p90', 800));
const p95Points = computed(() => buildPoints('p95', 800));
</script>
