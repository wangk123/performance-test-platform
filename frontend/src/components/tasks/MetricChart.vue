<template>
  <div class="metric-chart-wrap">
    <v-chart v-if="hasData" class="echarts-panel metric-chart-canvas" :option="chartOption" autoresize />
    <a-empty v-else class="metric-chart-empty" :description="emptyText" />
  </div>
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
import type { MetricSeries } from '../../types';

type ChartOption = ComposeOption<GridComponentOption | LegendComponentOption | TooltipComponentOption | LineSeriesOption>;

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

use([CanvasRenderer, LineChart, GridComponent, LegendComponent, TooltipComponent]);

const colors = ['#1f6f5f', '#d98724', '#3d6fb6', '#c24132', '#7a5cba', '#5b7c28', '#2f7f99', '#9c5f2d'];

const hasData = computed(() => props.series.some((item) => item.points.length > 0));

const chartOption = computed<ChartOption>(() => {
  const timeSet = new Set<number>();
  props.series.forEach((item) => item.points.forEach((point) => timeSet.add(point.timestamp)));
  const times = Array.from(timeSet).sort((a, b) => a - b);
  const labels = times.map((timestamp) => formatTime(timestamp));
  const useDualAxis = props.dualAxis || props.series.some((item) => item.yAxisIndex === 1);

  return {
    color: colors,
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value) => `${formatValue(Number(value))}`,
    },
    legend: {
      top: 0,
      type: 'scroll',
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 6,
      textStyle: { color: '#657489', fontSize: 11 },
      formatter: (name: string) => formatLegend(name),
    },
    grid: {
      top: 42,
      left: 48,
      right: useDualAxis ? 48 : 18,
      bottom: 34,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisLine: { lineStyle: { color: '#d7dde6' } },
      axisLabel: { color: '#657489' },
    },
    yAxis: useDualAxis
      ? [
          buildYAxis('左轴', 0),
          buildYAxis('右轴', 1),
        ]
      : buildYAxis(props.unit || '值', 0),
    series: props.series.map((item) => ({
      name: item.displayName,
      type: 'line',
      smooth: true,
      showSymbol: false,
      animation: false,
      yAxisIndex: item.yAxisIndex ?? 0,
      data: times.map((timestamp) => {
        const point = item.points.find((entry) => entry.timestamp === timestamp);
        return point?.value ?? null;
      }),
      emphasis: { focus: 'series' },
      lineStyle: { width: 2 },
    })),
  } as ChartOption;
});

function buildYAxis(name: string, index: number) {
  return {
    type: 'value',
    name,
    min: 0,
    boundaryGap: [0, '12%'],
    nameTextStyle: { color: '#657489', padding: [0, 0, 0, 24] },
    splitLine: { lineStyle: { color: '#edf1f5' } },
    axisLabel: {
      color: '#657489',
      formatter: (value: number) => formatValue(value),
    },
    ...(index === 1 ? { position: 'right' } : {}),
  };
}

function formatTime(timestamp: number) {
  return new Date(timestamp * 1000).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function formatValue(value: number) {
  if (!Number.isFinite(value)) {
    return '-';
  }
  if (props.unit === 'percent' || props.unit === 'percentunit') {
    return `${(value * 100).toFixed(1)}%`;
  }
  if (props.unit === 'bytes') {
    return formatBytes(value);
  }
  if (props.unit === 'Bps') {
    return `${formatBytes(value)}/s`;
  }
  return value.toFixed(2);
}

function formatBytes(value: number) {
  if (value >= 1024 ** 3) {
    return `${(value / 1024 ** 3).toFixed(1)} GiB`;
  }
  if (value >= 1024 ** 2) {
    return `${(value / 1024 ** 2).toFixed(1)} MiB`;
  }
  if (value >= 1024) {
    return `${(value / 1024).toFixed(1)} KiB`;
  }
  return `${value.toFixed(0)} B`;
}

function formatLegend(name: string) {
  const item = props.series.find((entry) => entry.displayName === name);
  if (!item || !item.points.length) {
    return name;
  }
  const values = item.points.map((point) => point.value);
  const last = values[values.length - 1] ?? 0;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const avg = values.reduce((sum, value) => sum + value, 0) / values.length;
  return `${name}  last ${formatValue(last)}  avg ${formatValue(avg)}  min ${formatValue(min)}  max ${formatValue(max)}`;
}
</script>
