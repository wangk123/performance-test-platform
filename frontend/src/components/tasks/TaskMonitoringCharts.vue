<template>
  <section class="task-chart-grid">
    <div class="panel task-chart-card">
      <h2>TPS 视图</h2>
      <p>按接口汇总多节点采样吞吐，单位 req/s。</p>
      <v-chart class="echarts-panel" :option="tpsOption" autoresize />
    </div>

    <div class="panel task-chart-card">
      <h2>响应时间视图</h2>
      <p>按接口展示平均响应时间，单位 ms。</p>
      <v-chart class="echarts-panel" :option="responseOption" autoresize />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import type { ComposeOption } from 'echarts/core';
import type { LineSeriesOption } from 'echarts/charts';
import type { GridComponentOption, LegendComponentOption, TooltipComponentOption } from 'echarts/components';
import type { TaskMetricPoint, TaskMonitoringResult } from '../../types';

type ChartOption = ComposeOption<GridComponentOption | LegendComponentOption | TooltipComponentOption | LineSeriesOption>;

const props = defineProps<{
  monitoring: TaskMonitoringResult;
  fallbackMetrics: TaskMetricPoint[];
}>();

use([CanvasRenderer, LineChart, GridComponent, LegendComponent, TooltipComponent]);

const colors = ['#1f6f5f', '#d98724', '#3d6fb6', '#c24132', '#7a5cba', '#5b7c28'];
const timeAxis = computed(() => {
  const values = props.monitoring.points.length
    ? props.monitoring.points.map((point) => point.time)
    : props.fallbackMetrics.map((point) => point.time);
  return Array.from(new Set(values));
});

const interfaceNames = computed(() => props.monitoring.interfaces.length ? props.monitoring.interfaces : ['JTL 汇总']);

const tpsOption = computed<ChartOption>(() => buildOption('tps', 'TPS', 'req/s'));
const responseOption = computed<ChartOption>(() => buildOption('avgRt', '平均响应时间', 'ms'));

function buildOption(key: 'tps' | 'avgRt', title: string, unit: string): ChartOption {
  return {
    color: colors,
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value) => `${value}${unit}`,
    },
    legend: {
      top: 0,
      type: 'scroll',
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 6,
      textStyle: { color: '#657489', fontSize: 12 },
    },
    grid: {
      top: 38,
      left: 44,
      right: 18,
      bottom: 34,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: timeAxis.value,
      axisLine: { lineStyle: { color: '#d7dde6' } },
      axisLabel: { color: '#657489' },
    },
    yAxis: {
      type: 'value',
      name: title,
      min: 0,
      boundaryGap: [0, '15%'],
      nameTextStyle: { color: '#657489', padding: [0, 0, 0, 28] },
      splitLine: { lineStyle: { color: '#edf1f5' } },
      axisLabel: { color: '#657489' },
    },
    series: interfaceNames.value.map((name) => ({
      name,
      type: 'line',
      smooth: true,
      showSymbol: true,
      symbolSize: 7,
      data: seriesData(name, key),
      emphasis: { focus: 'series' },
      lineStyle: { width: 2 },
    })),
  };
}

function seriesData(interfaceName: string, key: 'tps' | 'avgRt') {
  if (!props.monitoring.points.length) {
    return props.fallbackMetrics.map((point) => key === 'tps' ? point.tps : point.avgRt);
  }
  return timeAxis.value.map((time) => {
    const point = props.monitoring.points.find((item) => item.time === time && item.interfaceName === interfaceName);
    return point?.[key] ?? null;
  });
}
</script>
