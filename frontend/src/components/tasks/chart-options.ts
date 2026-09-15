import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import type { ComposeOption } from 'echarts/core';
import type { LineSeriesOption } from 'echarts/charts';
import type { GridComponentOption, LegendComponentOption, TooltipComponentOption } from 'echarts/components';
import type { MetricSeries, TaskMetricSeries } from '../../types';

export type ChartOption = ComposeOption<
  GridComponentOption | LegendComponentOption | TooltipComponentOption | LineSeriesOption
>;

use([CanvasRenderer, LineChart, GridComponent, LegendComponent, TooltipComponent]);

const TREND_COLORS = ['#0B7F8A', '#C9850A', '#3d6fb6', '#D14343', '#5C6B7A', '#2F9B6A'];
const METRIC_COLORS = ['#0B7F8A', '#C9850A', '#3d6fb6', '#D14343', '#7a5cba', '#5b7c28', '#2f7f99', '#9c5f2d'];

/** 监控趋势图（TPS / 响应时间）：TaskMonitoringCharts 与导出离屏渲染共用。 */
export function buildTrendOption(
  monitoring: TaskMetricSeries,
  key: 'throughput' | 'avgRtMs',
  title: string,
  unit: string,
): ChartOption {
  const ticks = monitoring.ticks;
  const names = trendInterfaceNames(ticks);
  return {
    color: TREND_COLORS,
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
      data: ticks.map((tick) => formatTime(tick.bucketTimeMs)),
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
    series: names.map((name) => ({
      name,
      type: 'line',
      smooth: true,
      showSymbol: false,
      symbolSize: 6,
      data: ticks.map((tick) => {
        const label = tick.labels.find((item) => item.label === name);
        return label ? label[key] : null;
      }),
      emphasis: { focus: 'series' },
      lineStyle: { width: 2 },
      connectNulls: false,
    })),
  };
}

function trendInterfaceNames(ticks: TaskMetricSeries['ticks']): string[] {
  const names = new Set<string>();
  for (const tick of ticks) {
    for (const label of tick.labels) names.add(label.label);
  }
  return Array.from(names);
}

/** 被测目标指标图（CPU / 内存等）：MetricChart 与导出离屏渲染共用。 */
export function buildMetricSeriesOption(
  series: MetricSeries[],
  unit: string,
  dualAxis: boolean,
): ChartOption {
  const timeSet = new Set<number>();
  series.forEach((item) => item.points.forEach((point) => timeSet.add(point.timestamp)));
  const times = Array.from(timeSet).sort((a, b) => a - b);
  const labels = times.map((timestamp) => formatTimestamp(timestamp));
  const useDualAxis = dualAxis || series.some((item) => item.yAxisIndex === 1);

  return {
    color: METRIC_COLORS,
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value) => formatMetricValue(Number(value), unit),
    },
    legend: {
      top: 0,
      type: 'scroll',
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 6,
      textStyle: { color: '#657489', fontSize: 11 },
      formatter: (name: string) => formatMetricLegend(series, name, unit),
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
      ? [buildMetricYAxis('左轴', unit, 0), buildMetricYAxis('右轴', unit, 1)]
      : buildMetricYAxis(unit || '值', unit, 0),
    series: series.map((item) => ({
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
}

function buildMetricYAxis(name: string, unit: string, index: number) {
  return {
    type: 'value' as const,
    name,
    min: 0,
    boundaryGap: [0, '12%'],
    nameTextStyle: { color: '#657489', padding: [0, 0, 0, 24] },
    splitLine: { lineStyle: { color: '#edf1f5' } },
    axisLabel: {
      color: '#657489',
      formatter: (value: number) => formatMetricValue(value, unit),
    },
    ...(index === 1 ? { position: 'right' as const } : {}),
  };
}

function formatMetricLegend(series: MetricSeries[], name: string, unit: string): string {
  const item = series.find((entry) => entry.displayName === name);
  if (!item || !item.points.length) {
    return name;
  }
  const values = item.points.map((point) => point.value);
  const last = values[values.length - 1] ?? 0;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const avg = values.reduce((sum, value) => sum + value, 0) / values.length;
  return `${name}  last ${formatMetricValue(last, unit)}  avg ${formatMetricValue(avg, unit)}  min ${formatMetricValue(min, unit)}  max ${formatMetricValue(max, unit)}`;
}

function formatMetricValue(value: number, unit: string): string {
  if (!Number.isFinite(value)) {
    return '-';
  }
  if (unit === 'percent' || unit === 'percentunit') {
    return `${(value * 100).toFixed(1)}%`;
  }
  if (unit === 'bytes') {
    return formatBytes(value);
  }
  if (unit === 'Bps') {
    return `${formatBytes(value)}/s`;
  }
  return value.toFixed(2);
}

function formatBytes(value: number): string {
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

function formatTime(ms: number): string {
  const date = new Date(ms);
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function formatTimestamp(timestamp: number): string {
  return new Date(timestamp * 1000).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value);
}
