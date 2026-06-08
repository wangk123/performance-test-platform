import type {
  Project,
  ProjectMember,
  ScriptAsset,
  ScriptStep,
  ScriptVersionRecord,
  TaskMetricPoint,
  TaskSample,
  TaskSummary,
  TestTask,
} from '../types';
import { defaultParams } from '../utils/jmeter';

type BackendScriptVersion = {
  id: number;
  projectId: number;
  versionNo: number;
  originalFilename: string;
  uploadedBy: string;
  uploadedAt: string;
};

type BackendScriptDefinition = {
  id: number;
  projectId: number;
  name: string;
  sourceFile: string;
  latestVersion: number;
  parseStatus: 'PARSED' | 'PARSE_FAILED';
  remark: string;
  updatedAt: string;
  steps: ScriptStep[];
  versions: BackendScriptVersion[];
};

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

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(path, options);
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: '请求失败' }));
    throw new Error(error.message ?? '请求失败');
  }
  return response.json() as Promise<T>;
}

export function loginApi(username: string, password: string) {
  return request<{ username: string; displayName: string; roles: string[] }>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
}

export function listProjectsApi() {
  return request<Project[]>('/api/projects?includeArchived=true');
}

export function createProjectApi(project: Pick<Project, 'code' | 'name' | 'description'>, username: string) {
  return request<Project>('/api/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify(project),
  });
}

export function listMembersApi(projectId: number) {
  return request<ProjectMember[]>(`/api/projects/${projectId}/members`);
}

export function listScriptDefinitionsApi(projectId: number) {
  return request<BackendScriptDefinition[]>(`/api/projects/${projectId}/scripts/definitions`);
}

export async function uploadScriptApi(projectId: number, file: File, username: string) {
  const formData = new FormData();
  formData.append('file', file);
  const version = await request<BackendScriptVersion>(`/api/projects/${projectId}/scripts`, {
    method: 'POST',
    headers: { 'X-User': username },
    body: formData,
  });
  return getScriptDefinitionApi(projectId, version.id);
}

export function getScriptDefinitionApi(projectId: number, versionId: number) {
  return request<BackendScriptDefinition>(`/api/projects/${projectId}/scripts/${versionId}/definition`);
}

export function saveScriptDefinitionApi(projectId: number, versionId: number, filename: string, steps: ScriptStep[], username: string) {
  return request<BackendScriptDefinition>(`/api/projects/${projectId}/scripts/${versionId}/definition`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({ filename, steps }),
  });
}

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

export function mapScriptDefinition(definition: BackendScriptDefinition): ScriptAsset {
  return {
    id: definition.id,
    projectId: definition.projectId,
    name: definition.name,
    sourceFile: definition.sourceFile,
    latestVersion: definition.latestVersion,
    parseStatus: definition.parseStatus,
    remark: definition.remark,
    updatedAt: definition.updatedAt,
    threadGroups: definition.steps.filter((step) => step.type === 'THREAD_GROUP').map((step) => ({
      name: step.name,
      threads: Number(step.config.threads ?? 1),
      rampUp: Number(step.config.rampUp ?? 0),
      loops: Number(step.config.loops ?? 1),
      duration: Number(step.config.duration ?? 0),
    })),
    apis: flattenSteps(definition.steps).filter((step) => step.type === 'HTTP_REQUEST').map((step) => ({
      method: String(step.config.method ?? 'GET'),
      path: String(step.config.path ?? step.config.url ?? '/'),
      domain: String(step.config.domain ?? ''),
    })),
    monitors: [],
    variables: [],
    params: defaultParams(),
    versions: definition.versions.map(mapVersion),
    steps: definition.steps,
  };
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

function mapVersion(version: BackendScriptVersion): ScriptVersionRecord {
  return {
    versionNo: version.versionNo,
    fileName: version.originalFilename,
    fileSize: 0,
    fileHash: '',
    importedAt: version.uploadedAt,
    importedBy: version.uploadedBy,
    remark: '',
  };
}

function flattenSteps(steps: ScriptStep[]): ScriptStep[] {
  return steps.flatMap((step) => [step, ...flattenSteps(step.children)]);
}
