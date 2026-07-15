import { request } from './http';

export interface SeedDatasource {
  id: number;
  projectId: number;
  name: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  passwordConfigured: boolean;
}

export interface SeedTemplateColumn {
  name: string;
  role: string;
  confidence: string;
  rationale: string;
  refTable?: string | null;
  refColumn?: string | null;
  generator?: string | null;
  lowAccepted: boolean;
}

export interface SeedTemplateOperation {
  type: string;
  table: string;
  riskyNoPk: boolean;
  columns: SeedTemplateColumn[];
}

export interface SeedTemplateDraft {
  operations: SeedTemplateOperation[];
}

export interface SeedTemplateDetail {
  id: number;
  status: string;
  versionNo: number;
  body: SeedTemplateDraft;
  confirmedBy: string;
  confirmedAt: string;
}

export interface SeedCloneJob {
  id: number;
  templateId: number;
  datasourceId: number;
  cloneCount: number;
  failurePolicy: string;
  status: string;
  successBatches: number;
  failedBatches: number;
  errors: string[];
  createdBy: string;
  createdAt: string;
  finishedAt: string;
}

export function listSeedDatasources(projectId: number) {
  return request<SeedDatasource[]>(`/api/projects/${projectId}/seed/datasources`);
}

export function createSeedDatasource(projectId: number, body: Record<string, unknown>) {
  return request<SeedDatasource>(`/api/projects/${projectId}/seed/datasources`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export function updateSeedDatasource(projectId: number, id: number, body: Record<string, unknown>) {
  return request<SeedDatasource>(`/api/projects/${projectId}/seed/datasources/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export function deleteSeedDatasource(projectId: number, id: number) {
  return request<void>(`/api/projects/${projectId}/seed/datasources/${id}`, { method: 'DELETE' });
}

export function testSeedDatasource(projectId: number, id: number) {
  return request<{ ok: boolean; message: string }>(`/api/projects/${projectId}/seed/datasources/${id}/test`, {
    method: 'POST',
  });
}

export function startSeedCapture(projectId: number, body: Record<string, unknown>) {
  return request<Record<string, unknown>>(`/api/projects/${projectId}/seed/captures`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export function endSeedSample(projectId: number, sessionId: number) {
  return request<Record<string, unknown>>(`/api/projects/${projectId}/seed/captures/${sessionId}/samples`, {
    method: 'POST',
  });
}

export function finishSeedCapture(projectId: number, sessionId: number) {
  return request<{ templateId: number; draft: SeedTemplateDraft }>(
    `/api/projects/${projectId}/seed/captures/${sessionId}/finish`,
    { method: 'POST' },
  );
}

export function listSeedTemplates(projectId: number) {
  return request<Array<Record<string, unknown>>>(`/api/projects/${projectId}/seed/templates`);
}

export function getSeedTemplate(projectId: number, templateId: number) {
  return request<SeedTemplateDetail>(`/api/projects/${projectId}/seed/templates/${templateId}`);
}

export function updateSeedTemplate(projectId: number, templateId: number, draft: SeedTemplateDraft) {
  return request<SeedTemplateDetail>(`/api/projects/${projectId}/seed/templates/${templateId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(draft),
  });
}

export function confirmSeedTemplate(projectId: number, templateId: number, operator?: string) {
  return request<SeedTemplateDetail>(`/api/projects/${projectId}/seed/templates/${templateId}/confirm`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ operator: operator || 'web' }),
  });
}

export function listSeedCloneJobs(projectId: number) {
  return request<SeedCloneJob[]>(`/api/projects/${projectId}/seed/clone-jobs`);
}

export function createSeedCloneJob(projectId: number, body: Record<string, unknown>) {
  return request<SeedCloneJob>(`/api/projects/${projectId}/seed/clone-jobs`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}
