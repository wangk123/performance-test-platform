<template>
  <!-- 规格：test-method-optimization-prototype.html 监控证据区（表格下方，场景级）；无执行时整区不渲染 -->
  <div v-if="hasExecutions" class="evidence-zone">
    <div class="zone-head">
      <b>监控证据</b>
      <a-select
        v-model:value="selectedExecutionId"
        class="exec-select"
        size="small"
        :options="executionOptions"
      />
      <span class="hint">默认最新成功执行，可切换；与执行详情页同源数据，只读渲染</span>
    </div>

    <a-spin :spinning="detailLoading">
      <div class="charts-grid">
        <div class="charts-trend">
          <div v-if="monitoringError" class="chart-fallback">TPS / 响应时间趋势加载失败</div>
          <TaskMonitoringCharts v-else :monitoring="monitoring ?? EMPTY_SERIES" />
        </div>
        <template v-if="serverTargets.length">
          <TargetMetricChartCard
            :execution-id="selectedExecutionId!"
            kind="SERVER_CPU"
            title="CPU 使用率"
            description="按服务器展示 CPU 占用百分比。"
            :target-ids="serverTargetIds"
            :polling="false"
            :refresh-interval-ms="5000"
          />
          <TargetMetricChartCard
            :execution-id="selectedExecutionId!"
            kind="SERVER_MEM"
            title="内存使用率"
            description="按可用内存反算使用率。"
            :target-ids="serverTargetIds"
            :polling="false"
            :refresh-interval-ms="5000"
          />
        </template>
        <div v-else class="target-fallback">
          <span v-if="targetError">被测目标监控加载失败</span>
          <a-empty v-else description="未绑定被测目标监控" :image-style="{ height: '48px' }" />
        </div>
      </div>
    </a-spin>

    <div class="zone-head sub-head">
      <b>补充截图</b>
      <span class="hint">手动上传 · 可挂当前选中执行 · 图注 / 排序</span>
    </div>
    <MethodEvidenceShots
      :plan-id="planId"
      :scenario-id="scenario.scenarioId"
      :images="selectedImages"
      :execution-id="selectedExecutionId"
      :can-edit="canEdit"
      @refresh="emit('refresh')"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { MethodScenarioData } from '../../../api/plan-method';
import {
  getExecutionMonitoringApi,
  getExecutionTargetMonitoringApi,
  toUiStatus,
} from '../../../api/task-plans';
import type { ScenarioExecution, ServerSelectable, TaskMetricSeries } from '../../../types';
import TaskMonitoringCharts from '../../tasks/TaskMonitoringCharts.vue';
import TargetMetricChartCard from '../../tasks/TargetMetricChartCard.vue';
import MethodEvidenceShots from './MethodEvidenceShots.vue';

const props = withDefaults(
  defineProps<{
    planId: number;
    scenario: MethodScenarioData;
    /** 补充截图编辑操作需 EDIT 权限（spec §7）；图表区只读不受限。 */
    canEdit?: boolean;
  }>(),
  { canEdit: true },
);
const emit = defineEmits<{ (e: 'refresh'): void }>();

const EMPTY_SERIES: TaskMetricSeries = { ticks: [] };

/* ---------- 执行选择器：非 hidden 行，默认最新成功执行 ---------- */

const visibleExecutions = computed(() => props.scenario.executions.filter((row) => !row.hidden));
const hasExecutions = computed(() => visibleExecutions.value.length > 0);

const isSuccess = (row: { status: string }) =>
  toUiStatus(row.status as ScenarioExecution['status']) === 'SUCCESS';

/** 与执行记录表行号对齐的时长显示（压测时间列同款格式）。 */
const durationLabel = (sec: number) => (!sec ? '—' : sec % 60 === 0 ? `${sec / 60}min` : `${sec}s`);

const executionOptions = computed(() =>
  visibleExecutions.value.map((row, index) => ({
    value: row.executionId,
    // 序号 = 表内非隐藏执行的第 N 行，与上方执行记录表 # 列一致
    label: `#${index + 1}-${row.threads}并发-${durationLabel(row.durationSec)}`,
  })),
);

/** 当前选中执行的截图：挂该执行的 + 仅挂场景的；挂其他执行的不在此显示。 */
const selectedImages = computed(() =>
  props.scenario.images.filter((img) => img.executionId == null || img.executionId === selectedExecutionId.value),
);

const selectedExecutionId = ref<number | null>(null);

watch(
  visibleExecutions,
  (rows) => {
    if (selectedExecutionId.value && rows.some((row) => row.executionId === selectedExecutionId.value)) return;
    const successRows = rows.filter(isSuccess);
    selectedExecutionId.value = (successRows.at(-1) ?? rows.at(-1))?.executionId ?? null;
  },
  { immediate: true },
);

/* ---------- 执行详情：monitoring + targetMonitoring 同执行详情页接口，各源独立降级 ---------- */

const detailLoading = ref(false);
const monitoring = ref<TaskMetricSeries | null>(null);
const monitoringError = ref(false);
const targetMonitoring = ref<{ serverTargets: ServerSelectable[] } | null>(null);
const targetError = ref(false);

const serverTargets = computed(() => targetMonitoring.value?.serverTargets ?? []);
const serverTargetIds = computed(() => serverTargets.value.map((target) => target.id));

watch(selectedExecutionId, (id) => void loadDetail(id), { immediate: true });

async function loadDetail(executionId: number | null) {
  monitoring.value = null;
  targetMonitoring.value = null;
  monitoringError.value = false;
  targetError.value = false;
  if (!executionId) return;
  detailLoading.value = true;
  const [mon, target] = await Promise.allSettled([
    getExecutionMonitoringApi(executionId),
    getExecutionTargetMonitoringApi(executionId),
  ]);
  if (selectedExecutionId.value !== executionId) return; // 切换后丢弃过期响应
  if (mon.status === 'fulfilled') monitoring.value = mon.value;
  else monitoringError.value = true;
  if (target.status === 'fulfilled') targetMonitoring.value = target.value;
  else targetError.value = true;
  detailLoading.value = false;
}
</script>

<style scoped>
.evidence-zone {
  margin-top: 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-soft);
  padding: 13px 15px 15px;
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.zone-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
}

.zone-head b { font-weight: 700; }
.zone-head .hint { color: var(--muted); font-weight: 400; font-size: 11.5px; }

.exec-select {
  min-width: 200px;
  max-width: 300px;
  font: 500 12px var(--font-data);
}

.sub-head {
  border-top: 1px dashed var(--line);
  padding-top: 11px;
  margin-top: 2px;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  align-items: start;
}

.charts-trend { grid-column: 1 / -1; }

.chart-fallback,
.target-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 140px;
  border: 1px dashed var(--line-strong);
  border-radius: 9px;
  color: var(--muted);
  font-size: 12.5px;
  background: var(--surface);
}

/* 无执行场景整区空态 */
/* 空态已去除：无执行时整区不渲染（v-if） */
</style>
