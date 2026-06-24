import type { ScriptAsset, TaskAggregateRow, TaskMetricPoint, TaskMonitoringResult, TaskSample, TaskSamplePage, TaskSummary, TestTask } from '../types';
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
    mode?: 'LOCAL' | 'DISTRIBUTED';
    controllerNodeId?: number | null;
    workerNodeIds?: number[];
  };
  remark: string;
  createdAt: string;
  startedAt: string | null;
  endedAt: string | null;
  errorMessage: string | null;
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

export function getTaskSamplesApi(taskId: number, page: number, pageSize: number) {
  return request<TaskSamplePage>(`/api/tasks/${taskId}/samples?page=${page}&pageSize=${pageSize}`);
}

export function submitTaskApi(projectId: number, task: TestTask, username: string) {
  return request<BackendTask>(`/api/projects/${projectId}/tasks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({
      scriptVersionId: task.scriptId,
      name: task.name,
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
      remark: '从脚本列表直接执行',
    }),
  });
}

export function getTaskLogsApi(taskId: number) {
  return request<string>(`/api/tasks/${taskId}/logs`);
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
    executionMode: task.config.mode ?? 'DISTRIBUTED',
    controllerNodeId: task.config.controllerNodeId ?? null,
    workerNodeIds: task.config.workerNodeIds ?? [],
    grafanaUrl: task.grafanaUrl,
    remark: task.remark,
    createdAt: task.createdAt,
    lastRunAt: task.startedAt,
    endedAt: task.endedAt,
    errorMessage: task.errorMessage,
    executionLogs: '',
    summary: result?.summary ?? { samples: 0, throughput: 0, avgRt: 0, p95: 0, errorRate: 0 },
    metrics: result?.metrics ?? [],
    monitoring: monitoring ?? { interfaces: [], points: [] },
    aggregateRows: result?.aggregateRows ?? [],
    samples: result?.samples ?? [],
    sampleTotal: 0,
  };
}
