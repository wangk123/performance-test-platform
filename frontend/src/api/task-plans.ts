import type {
  ExecutionDetail,
  ScenarioExecution,
  ScenarioThreadGroupConfig,
  TaskPlan,
  TaskScenario,
  TaskAggregateRow,
  TaskMetricSeries,
  TaskSamplePage,
  TaskSummary,
  TargetMonitoringResult,
} from '../types';
import { request } from './http';

export type BackendExecutionResult = {
  summary: TaskSummary;
  aggregateRows: TaskAggregateRow[];
  samples: unknown[];
};

export function listTaskPlansApi(projectId: number) {
  return request<TaskPlan[]>(`/api/projects/${projectId}/task-plans`);
}

export function getTaskPlanApi(planId: number) {
  return request<TaskPlan>(`/api/task-plans/${planId}`);
}

export function createTaskPlanApi(
  projectId: number,
  payload: {
    name: string;
    remark?: string;
    controllerNodeId?: number | null;
    workerNodeIds?: number[];
    monitorTargetIds?: number[];
  },
  username: string,
) {
  return request<TaskPlan>(`/api/projects/${projectId}/task-plans`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify(payload),
  });
}

export function updateTaskPlanApi(
  planId: number,
  payload: {
    name: string;
    remark?: string;
    controllerNodeId?: number | null;
    workerNodeIds?: number[];
    monitorTargetIds?: number[];
  },
) {
  return request<TaskPlan>(`/api/task-plans/${planId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function deleteTaskPlanApi(planId: number) {
  return request<void>(`/api/task-plans/${planId}`, { method: 'DELETE' });
}

export function listScenariosApi(planId: number) {
  return request<TaskScenario[]>(`/api/task-plans/${planId}/scenarios`);
}

export function getScenarioApi(scenarioId: number) {
  return request<TaskScenario>(`/api/scenarios/${scenarioId}`);
}

export function createScenarioApi(
  planId: number,
  payload: {
    scriptVersionId: number;
    name: string;
    jmeterProperties?: Record<string, string>;
    threadGroupConfigs?: ScenarioThreadGroupConfig[];
    overridePlanDefaults?: boolean;
    controllerNodeId?: number | null;
    workerNodeIds?: number[];
    monitorTargetIds?: number[];
  },
) {
  return request<TaskScenario>(`/api/task-plans/${planId}/scenarios`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function updateScenarioApi(
  scenarioId: number,
  payload: {
    name: string;
    scriptVersionId?: number;
    jmeterProperties?: Record<string, string>;
    threadGroupConfigs?: ScenarioThreadGroupConfig[];
    overridePlanDefaults?: boolean;
    controllerNodeId?: number | null;
    workerNodeIds?: number[];
    monitorTargetIds?: number[];
  },
) {
  return request<TaskScenario>(`/api/scenarios/${scenarioId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function deleteScenarioApi(scenarioId: number) {
  return request<void>(`/api/scenarios/${scenarioId}`, { method: 'DELETE' });
}

export function listExecutionsApi(scenarioId: number) {
  return request<ScenarioExecution[]>(`/api/scenarios/${scenarioId}/executions`);
}

export function triggerExecutionApi(
  scenarioId: number,
  options?: {
    executionName?: string;
    threadGroupConfigId?: number | null;
    threadGroupPresetSortOrder?: number | null;
  },
) {
  const body: {
    executionName?: string;
    threadGroupConfigId?: number;
    threadGroupPresetSortOrder?: number;
  } = {};
  if (options?.executionName) body.executionName = options.executionName;
  if (options?.threadGroupConfigId != null) body.threadGroupConfigId = options.threadGroupConfigId;
  if (options?.threadGroupPresetSortOrder != null) body.threadGroupPresetSortOrder = options.threadGroupPresetSortOrder;
  return request<ScenarioExecution>(`/api/scenarios/${scenarioId}/executions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export function deleteExecutionsApi(executionIds: number[]) {
  return request<void>('/api/executions/batch', {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(executionIds),
  });
}

export function getExecutionApi(executionId: number) {
  return request<ScenarioExecution>(`/api/executions/${executionId}`);
}

export function stopExecutionApi(executionId: number) {
  return request<ScenarioExecution>(`/api/executions/${executionId}/stop`, { method: 'POST' });
}

export function deleteExecutionApi(executionId: number) {
  return request<void>(`/api/executions/${executionId}`, { method: 'DELETE' });
}

export function getExecutionLogsApi(executionId: number) {
  return request<string>(`/api/executions/${executionId}/logs`);
}

export function getExecutionResultApi(executionId: number) {
  return request<BackendExecutionResult>(`/api/executions/${executionId}/result`);
}

export function getExecutionMonitoringApi(executionId: number) {
  return request<TaskMetricSeries>(`/api/executions/${executionId}/monitoring`);
}

export function getExecutionTargetMonitoringApi(executionId: number) {
  return request<TargetMonitoringResult>(`/api/executions/${executionId}/target-monitoring`);
}

export function getExecutionSamplesApi(
  executionId: number,
  page: number,
  pageSize: number,
  filters?: { label?: string; code?: string; success?: boolean },
) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  });
  if (filters?.label) params.set('label', filters.label);
  if (filters?.code) params.set('code', filters.code);
  if (filters?.success !== undefined) params.set('success', String(filters.success));
  return request<TaskSamplePage>(`/api/executions/${executionId}/samples?${params.toString()}`);
}

export function getExecutionSampleDetailApi(executionId: number, sampleId: number) {
  return request<import('../types').TaskSample>(`/api/executions/${executionId}/samples/${sampleId}`);
}

export function mapExecutionDetail(
  execution: ScenarioExecution,
  result?: BackendExecutionResult,
  monitoring?: TaskMetricSeries,
  targetMonitoring?: TargetMonitoringResult | null,
): ExecutionDetail {
  return {
    ...execution,
    executionLogs: '',
    summary: result?.summary ?? { samples: 0, throughput: 0, avgRt: 0, p95: 0, errorRate: 0, accuracy: null },
    monitoring: monitoring ?? { ticks: [] },
    targetMonitoring: targetMonitoring ?? null,
    aggregateRows: result?.aggregateRows ?? [],
    samples: [],
    sampleTotal: 0,
  };
}

export function toUiStatus(status: ScenarioExecution['status']): import('../types').ExecutionUiStatus {
  if (status === 'QUEUED') return 'PENDING';
  if (status === 'STOPPING') return 'STOPPING';
  if (status === 'INTERRUPTED' || status === 'CANCELLED') return 'INTERRUPTED';
  return status;
}
