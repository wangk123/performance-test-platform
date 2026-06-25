<template>
  <div class="panel task-chart-card target-metric-card">
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
    <a-spin :spinning="loading && !series.length">
      <MetricChart
        :series="series"
        :unit="unit"
        :dual-axis="dualAxis"
        :empty-text="error ?? '暂无指标数据'"
      />
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, toRef } from 'vue';
import MetricChart from './MetricChart.vue';
import { usePrometheusSeries, type MetricsRefreshInterval } from '../../composables/usePrometheusSeries';
import type { MetricKind } from '../../types';

const props = defineProps<{
  executionId: number;
  kind: MetricKind;
  title: string;
  description: string;
  targetIds: number[];
  itemId?: string | null;
  polling: boolean;
  refreshIntervalMs: number;
  dualAxis?: boolean;
}>();

const { series, unit, loading, error } = usePrometheusSeries({
  taskId: toRef(props, 'executionId'),
  executionId: toRef(props, 'executionId'),
  kind: props.kind,
  targetIds: computed(() => props.targetIds),
  itemId: computed(() => props.itemId ?? null),
  polling: computed(() => props.polling),
  refreshIntervalMs: computed(() => props.refreshIntervalMs as MetricsRefreshInterval),
});
</script>
