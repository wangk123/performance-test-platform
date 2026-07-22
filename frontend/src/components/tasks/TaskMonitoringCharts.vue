<template>
  <section class="task-chart-grid">
    <div class="panel task-chart-card">
      <h2>TPS 视图</h2>
      <p>按接口分别展示每秒采样吞吐，单位 req/s。</p>
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
import type { TaskMetricSeries } from '../../types';

type ChartOption = ComposeOption<GridComponentOption | LegendComponentOption | TooltipComponentOption | LineSeriesOption>;

const props = defineProps<{
  monitoring: TaskMetricSeries;
}>();

use([CanvasRenderer, LineChart, GridComponent, LegendComponent, TooltipComponent]);

const colors = ['#0B7F8A', '#C9850A', '#3d6fb6', '#D14343', '#5C6B7A', '#2F9B6A'];

const interfaceNames = computed<string[]>(() => {
  const names = new Set<string>();
  for (const tick of props.monitoring.ticks) {
    for (const label of tick.labels) names.add(label.label);
  }
  return Array.from(names);
});

const timeAxis = computed<string[]>(() =>
  props.monitoring.ticks.map((tick) => formatTime(tick.bucketTimeMs)),
);

const tpsOption = computed<ChartOption>(() => buildOption('throughput', 'TPS', 'req/s'));
const responseOption = computed<ChartOption>(() => buildOption('avgRtMs', '平均响应时间', 'ms'));

function buildOption(
  key: 'throughput' | 'avgRtMs',
  title: string,
  unit: string,
): ChartOption {
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
      showSymbol: false,
      symbolSize: 6,
      data: seriesData(name, key),
      emphasis: { focus: 'series' },
      lineStyle: { width: 2 },
      connectNulls: false,
    })),
  };
}

function seriesData(interfaceName: string, key: 'throughput' | 'avgRtMs') {
  return props.monitoring.ticks.map((tick) => {
    const label = tick.labels.find((item) => item.label === interfaceName);
    return label ? label[key] : null;
  });
}

function formatTime(ms: number): string {
  const date = new Date(ms);
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value);
}
</script>
