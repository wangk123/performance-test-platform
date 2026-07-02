import type { ScriptStep, ScenarioThreadGroupConfig } from '../types';

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
