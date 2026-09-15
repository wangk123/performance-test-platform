import { init } from 'echarts/core';
import type { EChartsType } from 'echarts/core';
import { buildMetricSeriesOption, buildTrendOption } from '../components/tasks/chart-options';
import { getPlanMethodApi } from '../api/plan-method';
import { getExecutionMonitoringApi, getExecutionTargetMonitoringApi } from '../api/task-plans';
import { getTargetMonitoringSeriesApi } from '../api/target-monitoring';
import type { MetricSeries, TaskMetricSeries } from '../types';

export type MethodChartKind = 'TPS' | 'RT' | 'CPU' | 'MEM';

export interface MethodChartImagePayload {
  executionId: number;
  kind: MethodChartKind;
  dataUrl: string;
}

const CHART_WIDTH = 480;
const CHART_HEIGHT = 270;

/**
 * 导出前收集测试方法章节趋势图：对每条非 hidden 执行离屏渲染 TPS / 响应时间（执行监控）与
 * CPU / 内存（被测目标 Prometheus 序列），getDataURL 后立即销毁。单图失败跳过，不阻断导出。
 */
export async function collectMethodChartImages(planId: number): Promise<MethodChartImagePayload[]> {
  const method = await getPlanMethodApi(planId);
  const executions = method.scenarios
    .flatMap((scenario) => scenario.executions)
    .filter((row) => !row.hidden);
  if (!executions.length) {
    return [];
  }

  const holder = createOffscreenHolder();
  const payload: MethodChartImagePayload[] = [];
  try {
    for (const row of executions) {
      const charts = await renderExecutionCharts(row.executionId, holder);
      payload.push(...charts);
    }
  } finally {
    disposeHolder(holder);
  }
  return payload;
}

async function renderExecutionCharts(
  executionId: number,
  holder: OffscreenHolder,
): Promise<MethodChartImagePayload[]> {
  const charts: MethodChartImagePayload[] = [];
  const [monitoring, targetMonitoring] = await Promise.allSettled([
    getExecutionMonitoringApi(executionId),
    getExecutionTargetMonitoringApi(executionId),
  ]);

  if (monitoring.status === 'fulfilled') {
    capture(monitoring.value, 'TPS', 'throughput', 'TPS', 'req/s', holder, charts, executionId);
    capture(monitoring.value, 'RT', 'avgRtMs', '平均响应时间', 'ms', holder, charts, executionId);
  }
  if (targetMonitoring.status === 'fulfilled' && targetMonitoring.value.serverTargets.length) {
    const targetIds = targetMonitoring.value.serverTargets.map((target) => target.id);
    const [cpu, mem] = await Promise.allSettled([
      getTargetMonitoringSeriesApi(executionId, 'SERVER_CPU', { targetIds }),
      getTargetMonitoringSeriesApi(executionId, 'SERVER_MEM', { targetIds }),
    ]);
    if (cpu.status === 'fulfilled') {
      captureMetric(cpu.value.series, cpu.value.unit, 'CPU', 'CPU 使用率', holder, charts, executionId);
    }
    if (mem.status === 'fulfilled') {
      captureMetric(mem.value.series, mem.value.unit, 'MEM', '内存使用率', holder, charts, executionId);
    }
  }
  return charts;
}

function capture(
  monitoring: TaskMetricSeries,
  kind: MethodChartKind,
  key: 'throughput' | 'avgRtMs',
  title: string,
  unit: string,
  holder: OffscreenHolder,
  charts: MethodChartImagePayload[],
  executionId: number,
) {
  if (!monitoring.ticks.length) return;
  const chart = init(holder.el);
  try {
    chart.setOption({ ...buildTrendOption(monitoring, key, title, unit), animation: false });
    const dataUrl = toPng(chart);
    if (dataUrl) charts.push({ executionId, kind, dataUrl });
  } catch (_) {
    // 单图失败跳过
  } finally {
    chart.dispose();
    clearHolder(holder);
  }
}

function captureMetric(
  series: MetricSeries[],
  unit: string,
  kind: MethodChartKind,
  title: string,
  holder: OffscreenHolder,
  charts: MethodChartImagePayload[],
  executionId: number,
) {
  if (!series.some((item) => item.points.length)) return;
  const chart = init(holder.el);
  try {
    chart.setOption({ ...buildMetricSeriesOption(series, unit, false), animation: false });
    const dataUrl = toPng(chart);
    if (dataUrl) charts.push({ executionId, kind, dataUrl });
  } catch (_) {
    // 单图失败跳过
  } finally {
    chart.dispose();
    clearHolder(holder);
  }
}

function toPng(chart: EChartsType): string | null {
  try {
    return chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' });
  } catch (_) {
    return null;
  }
}

interface OffscreenHolder {
  el: HTMLDivElement;
  parent: HTMLElement;
}

function createOffscreenHolder(): OffscreenHolder {
  const parent = document.createElement('div');
  parent.style.cssText = 'position:fixed;left:-9999px;top:0;width:0;height:0;overflow:hidden;';
  const el = document.createElement('div');
  el.style.cssText = `width:${CHART_WIDTH}px;height:${CHART_HEIGHT}px;`;
  parent.appendChild(el);
  document.body.appendChild(parent);
  return { el, parent };
}

function clearHolder(holder: OffscreenHolder) {
  holder.el.innerHTML = '';
}

function disposeHolder(holder: OffscreenHolder) {
  clearHolder(holder);
  holder.parent.remove();
}
