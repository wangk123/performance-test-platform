import type { TaskAggregateRow, TaskMetricPoint, TaskSample, TaskSummary, TestTask } from '../types';
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
  };
  remark: string;
  createdAt: string;
  startedAt: string | null;
};

export type BackendTaskResult = {
  summary: TaskSummary;
  metrics: TaskMetricPoint[];
  aggregateRows: TaskAggregateRow[];
  samples: TaskSample[];
};

export function listTasksApi(projectId: number) {
  return request<BackendTask[]>(`/api/projects/${projectId}/tasks`);
}

export function getTaskApi(taskId: number) {
  return request<BackendTask>(`/api/tasks/${taskId}`);
}

export function getTaskResultApi(taskId: number) {
  return request<BackendTaskResult>(`/api/tasks/${taskId}/result`);
}

export function submitTaskApi(projectId: number, task: TestTask, username: string) {
  return request<BackendTask>(`/api/projects/${projectId}/tasks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({
      scriptVersionId: task.scriptId,
      name: task.name,
      environment: task.environment,
      remark: task.remark,
    }),
  });
}

export function deleteTaskApi(taskId: number) {
  return request<void>(`/api/tasks/${taskId}`, { method: 'DELETE' });
}

export function mapBackendTask(task: BackendTask, result?: BackendTaskResult): TestTask {
  return {
    id: task.id,
    projectId: task.projectId,
    scriptId: task.scriptVersionId,
    name: task.name,
    status: task.status === 'QUEUED' ? 'PENDING' : task.status === 'INTERRUPTED' || task.status === 'CANCELLED' ? 'FAILED' : task.status,
    environment: task.config.environment,
    priority: '普通',
    remark: task.remark,
    createdAt: task.createdAt,
    lastRunAt: task.startedAt,
    summary: result?.summary ?? { samples: 0, throughput: 0, avgRt: 0, p95: 0, errorRate: 0 },
    metrics: result?.metrics ?? [],
    aggregateRows: result?.aggregateRows?.length ? result.aggregateRows : aggregateSamples(result?.samples ?? [], result?.summary),
    samples: result?.samples ?? [],
  };
}

function aggregateSamples(samples: TaskSample[], summary?: TaskSummary): TaskAggregateRow[] {
  const groups = new Map<string, TaskSample[]>();
  samples.forEach((sample) => {
    const threadName = normalizeThreadGroup(sample.threadName);
    const key = `${threadName}\n${sample.label}`;
    groups.set(key, [...(groups.get(key) ?? []), sample]);
  });
  const rows = Array.from(groups.values()).map((items) => {
    const elapsed = items.map((sample) => sample.elapsed).sort((left, right) => left - right);
    const failed = items.filter((sample) => !sample.success).length;
    const average = Math.round(elapsed.reduce((sum, value) => sum + value, 0) / elapsed.length);
    return {
      label: items[0].label,
      threadName: normalizeThreadGroup(items[0].threadName),
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

function percentile(values: number[], rate: number) {
  const index = Math.ceil(values.length * rate) - 1;
  return values[Math.max(0, Math.min(values.length - 1, index))] ?? 0;
}

function round(value: number) {
  return Math.round(value * 100) / 100;
}
