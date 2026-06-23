import type { ScriptAsset, TaskAggregateRow, TaskMetricPoint, TaskMonitoringResult, TaskSample, TaskSummary, TestTask } from '../types';
import { request } from './http';

type BackendTask = {
  id: number;
  projectId: number;
  scriptVersionId: number;
  name: string;
  status: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'INTERRUPTED';
  config: {
    threads: number;
    rampUp: number;
    duration: number;
    loops: number;
    environment: string;
    mode?: 'LOCAL' | 'DISTRIBUTED';
    controllerNodeId?: number | null;
    workerNodeIds?: number[];
  };
  remark: string;
  createdAt: string;
  startedAt: string | null;
  grafanaUrl: string | null;
};

export type BackendTaskResult = {
  summary: TaskSummary;
  metrics: TaskMetricPoint[];
  aggregateRows: TaskAggregateRow[];
  samples: TaskSample[];
};

export type BackendTaskMonitoringResult = TaskMonitoringResult;

export function listTasksApi(projectId: number) {
  return request<BackendTask[]>(`/api/projects/${projectId}/tasks`);
}

export function getTaskApi(taskId: number) {
  return request<BackendTask>(`/api/tasks/${taskId}`);
}

export function getTaskResultApi(taskId: number) {
  return request<BackendTaskResult>(`/api/tasks/${taskId}/result`);
}

export function getTaskMonitoringApi(taskId: number) {
  return request<BackendTaskMonitoringResult>(`/api/tasks/${taskId}/monitoring`);
}

export function submitTaskApi(projectId: number, task: TestTask, username: string) {
  return request<BackendTask>(`/api/projects/${projectId}/tasks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({
      scriptVersionId: task.scriptId,
      name: task.name,
      environment: task.environment,
      executionMode: task.executionMode,
      controllerNodeId: task.controllerNodeId,
      workerNodeIds: task.workerNodeIds,
      remark: task.remark,
    }),
  });
}

export function submitScriptTaskApi(projectId: number, script: ScriptAsset, username: string) {
  return request<BackendTask>(`/api/projects/${projectId}/tasks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({
      scriptVersionId: script.id,
      name: `${script.name} / 即时执行`,
      environment: 'SIT / 127.0.0.1',
      remark: '从脚本列表直接执行',
    }),
  });
}

export function deleteTaskApi(taskId: number) {
  return request<void>(`/api/tasks/${taskId}`, { method: 'DELETE' });
}

export function mapBackendTask(task: BackendTask, result?: BackendTaskResult, monitoring?: BackendTaskMonitoringResult): TestTask {
  return {
    id: task.id,
    projectId: task.projectId,
    scriptId: task.scriptVersionId,
    name: task.name,
    status: task.status === 'QUEUED' ? 'PENDING' : task.status === 'INTERRUPTED' || task.status === 'CANCELLED' ? 'FAILED' : task.status,
    executionMode: task.config.mode ?? 'LOCAL',
    controllerNodeId: task.config.controllerNodeId ?? null,
    workerNodeIds: task.config.workerNodeIds ?? [],
    grafanaUrl: task.grafanaUrl,
    environment: task.config.environment,
    priority: '普通',
    remark: task.remark,
    createdAt: task.createdAt,
    lastRunAt: task.startedAt,
    summary: result?.summary ?? { samples: 0, throughput: 0, avgRt: 0, p95: 0, errorRate: 0 },
    metrics: result?.metrics ?? [],
    monitoring: monitoring ?? { interfaces: [], points: [] },
    aggregateRows: result?.aggregateRows?.length ? result.aggregateRows : aggregateSamples(result?.samples ?? [], result?.summary),
    samples: result?.samples ?? [],
  };
}

function aggregateSamples(samples: TaskSample[], summary?: TaskSummary): TaskAggregateRow[] {
  const groups = new Map<string, TaskSample[]>();
  samples.forEach((sample) => {
    groups.set(sample.label, [...(groups.get(sample.label) ?? []), sample]);
  });
  const rows = Array.from(groups.values()).map((items) => {
    const elapsed = items.map((sample) => sample.elapsed).sort((left, right) => left - right);
    const failed = items.filter((sample) => !sample.success).length;
    const average = Math.round(elapsed.reduce((sum, value) => sum + value, 0) / elapsed.length);
    return {
      label: items[0].label,
      threadName: aggregateThreadName(items),
      samples: items.length,
      average,
      median: percentile(elapsed, 0.50),
      p90: percentile(elapsed, 0.90),
      p95: percentile(elapsed, 0.95),
      p99: percentile(elapsed, 0.99),
      min: elapsed[0],
      max: elapsed[elapsed.length - 1],
      errorRate: round(failed * 100 / items.length),
      throughput: 0,
    };
  });
  if (rows.length === 1 && summary) {
    rows[0].throughput = summary.throughput;
  }
  return rows;
}

function normalizeThreadGroup(threadName: string) {
  return threadName.replace(/\s+\d+-\d+$/, '');
}

function aggregateThreadName(samples: TaskSample[]) {
  const names = Array.from(new Set(samples.map((sample) => normalizeThreadGroup(sample.threadName))));
  return names.length === 1 ? names[0] : '全部节点';
}

function percentile(values: number[], rate: number) {
  const index = Math.ceil(values.length * rate) - 1;
  return values[Math.max(0, Math.min(values.length - 1, index))] ?? 0;
}

function round(value: number) {
  return Math.round(value * 100) / 100;
}
