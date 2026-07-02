import type { ScriptStep, ScenarioThreadGroupConfig, ThreadGroupConfigSummary } from '../types';

export function listThreadGroupSteps(steps: ScriptStep[]) {
  const result: { id: string; name: string }[] = [];
  const walk = (items: ScriptStep[]) => {
    for (const step of items) {
      if (step.type === 'THREAD_GROUP') {
        result.push({ id: step.id, name: step.name });
      }
      if (step.children.length > 0) {
        walk(step.children);
      }
    }
  };
  walk(steps);
  return result;
}

export function threadGroupConfigLabel(config: Pick<ScenarioThreadGroupConfig, 'stepName' | 'threads' | 'rampUp' | 'duration'>) {
  return `${config.stepName} · ${config.threads} 线程 · Ramp ${config.rampUp}s · 持续 ${config.duration}s`;
}

export function roundReportLabel(config: {
  stepName?: string | null;
  threads: number;
  rampUp?: number;
  duration?: number;
}) {
  if (config.stepName) {
    return threadGroupConfigLabel({
      stepName: config.stepName,
      threads: config.threads,
      rampUp: config.rampUp ?? 0,
      duration: config.duration ?? 0,
    });
  }
  return `${config.threads} 并发`;
}

export function emptyThreadGroupConfig(stepId = '', stepName = ''): ScenarioThreadGroupConfig {
  return {
    id: 0,
    stepId,
    stepName,
    threads: 50,
    rampUp: 30,
    duration: 300,
    sortOrder: 0,
  };
}

export type ThreadGroupStepOption = { id: string; name: string };

export type ThreadGroupPresetGroup = {
  sortOrder: number;
  rows: ScenarioThreadGroupConfig[];
};

export function syncPresetRows(
  existing: ScenarioThreadGroupConfig[],
  options: ThreadGroupStepOption[],
  sortOrder: number,
): ScenarioThreadGroupConfig[] {
  return options.map((option) => {
    const matched = existing.find((item) => item.stepId === option.id);
    if (matched) {
      return { ...matched, stepName: option.name, sortOrder };
    }
    return { ...emptyThreadGroupConfig(option.id, option.name), sortOrder };
  });
}

export function groupThreadGroupConfigs(
  configs: ScenarioThreadGroupConfig[],
  options: ThreadGroupStepOption[],
): ThreadGroupPresetGroup[] {
  if (options.length === 0) return [];

  const orderSet = new Set<number>();
  for (const config of configs) {
    orderSet.add(config.sortOrder);
  }
  const orders = orderSet.size > 0 ? [...orderSet].sort((a, b) => a - b) : [0];

  return orders.map((sortOrder) => ({
    sortOrder,
    rows: syncPresetRows(
      configs.filter((item) => item.sortOrder === sortOrder),
      options,
      sortOrder,
    ),
  }));
}

export function flattenThreadGroupPresets(groups: ThreadGroupPresetGroup[]): ScenarioThreadGroupConfig[] {
  return groups.flatMap((group) => group.rows.map((row) => ({ ...row, sortOrder: group.sortOrder })));
}

export function createDefaultPreset(
  options: ThreadGroupStepOption[],
  sortOrder: number,
): ThreadGroupPresetGroup {
  return {
    sortOrder,
    rows: syncPresetRows([], options, sortOrder),
  };
}

export function groupStoredThreadGroupConfigs(configs: ScenarioThreadGroupConfig[]): ThreadGroupPresetGroup[] {
  if (configs.length === 0) return [];

  const groups = new Map<number, ScenarioThreadGroupConfig[]>();
  for (const config of configs) {
    const list = groups.get(config.sortOrder) ?? [];
    list.push(config);
    groups.set(config.sortOrder, list);
  }

  return [...groups.entries()]
    .sort(([left], [right]) => left - right)
    .map(([sortOrder, rows]) => ({ sortOrder, rows }));
}

export function sumPresetThreads(rows: ScenarioThreadGroupConfig[]) {
  return rows.reduce((total, row) => total + row.threads, 0);
}

export function presetRepresentativeConfigId(group: ThreadGroupPresetGroup) {
  return group.rows[0]?.id ?? 0;
}

export function executePresetDetail(group: ThreadGroupPresetGroup) {
  return group.rows.map((row) => threadGroupConfigLabel(row)).join('；');
}

export function aggregatePresetSummary(rows: ScenarioThreadGroupConfig[]): ThreadGroupConfigSummary | null {
  const summaries = rows
    .map((row) => row.latestSummary)
    .filter((summary): summary is NonNullable<typeof summary> => summary != null && summary.samples > 0);

  if (summaries.length === 0) return null;

  const deduped = summaries.filter((summary, index, list) => (
    list.findIndex((item) => (
      item.samples === summary.samples
      && item.throughput === summary.throughput
      && item.avgRt === summary.avgRt
      && item.errorRate === summary.errorRate
    )) === index
  ));
  if (deduped.length === 1) {
    return deduped[0];
  }

  let totalSamples = 0;
  let totalThroughput = 0;
  let weightedRt = 0;
  let weightedError = 0;

  for (const summary of deduped) {
    totalSamples += summary.samples;
    totalThroughput += summary.throughput;
    weightedRt += summary.avgRt * summary.samples;
    weightedError += summary.errorRate * summary.samples;
  }

  return {
    samples: totalSamples,
    throughput: totalThroughput,
    avgRt: totalSamples > 0 ? Math.round(weightedRt / totalSamples) : 0,
    errorRate: totalSamples > 0 ? weightedError / totalSamples : 0,
  };
}

export function formatMetric(value: number | string | null | undefined, suffix = '') {
  if (value == null || value === '') return '—';
  return `${value}${suffix}`;
}

export function formatSamples(value: number | null | undefined) {
  if (value == null) return '—';
  return value.toLocaleString();
}

export function formatThroughput(value: number | null | undefined) {
  if (value == null) return '—';
  return value.toFixed(1);
}

export function formatErrorRate(value: number | null | undefined) {
  if (value == null) return '—';
  return `${value.toFixed(2)}%`;
}
