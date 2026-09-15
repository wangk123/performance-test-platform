<template>
  <div class="metric-chart-wrap">
    <v-chart v-if="hasData" class="echarts-panel metric-chart-canvas" :option="chartOption" autoresize />
    <a-empty v-else class="metric-chart-empty" :description="emptyText" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { buildMetricSeriesOption } from './chart-options';
import type { MetricSeries } from '../../types';

const props = withDefaults(defineProps<{
  series: MetricSeries[];
  unit?: string;
  dualAxis?: boolean;
  emptyText?: string;
}>(), {
  unit: '',
  dualAxis: false,
  emptyText: '暂无指标数据',
});

const hasData = computed(() => props.series.some((item) => item.points.length > 0));

const chartOption = computed(() =>
  buildMetricSeriesOption(props.series, props.unit, props.dualAxis),
);
</script>
