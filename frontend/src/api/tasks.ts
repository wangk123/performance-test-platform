import type { TaskMetricPoint, TaskSample, TaskSummary, TestTask } from '../types';
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
      threads: task.threads,
      rampUp: task.rampUp,
      duration: task.duration,
      loops: task.loops,
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
    threads: task.config.threads,
    rampUp: task.config.rampUp,
    duration: task.config.duration,
    loops: task.config.loops,
    priority: '普通',
    remark: task.remark,
    createdAt: task.createdAt,
    lastRunAt: task.startedAt,
    summary: result?.summary ?? { samples: 0, throughput: 0, avgRt: 0, p95: 0, errorRate: 0 },
    metrics: result?.metrics ?? [],
    samples: result?.samples ?? [],
  };
}
