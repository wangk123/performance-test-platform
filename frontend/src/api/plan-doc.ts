import type {
  PlanComment,
  PlanDocumentResponse,
  PlanSnapshotView,
  PlanShareTokenView,
  PlanTemplate,
  PrecheckRunReport,
  PrecheckSettings,
  TaskPlan,
} from '../types';
import { request } from './http';

const json = { 'Content-Type': 'application/json' };

export function getPlanDocumentApi(planId: number) {
  return request<PlanDocumentResponse>(`/api/task-plans/${planId}`);
}

export function updatePlanDocumentApi(planId: number, baseRevision: number, markdown: string) {
  return request<TaskPlan>(`/api/task-plans/${planId}/document`, {
    method: 'PUT',
    headers: json,
    body: JSON.stringify({ baseRevision, markdown }),
  });
}

type TransitionAction =
  | 'submit' | 'start-review' | 'approve' | 'reject' | 'withdraw' | 'back-to-draft'
  | 'start-execution' | 'to-report' | 'generate-report' | 'publish' | 'new-revision';

export function transitionPlanApi(planId: number, action: TransitionAction, payload?: { comment?: string; conclusion?: string }) {
  return request<PlanDocumentResponse>(`/api/task-plans/${planId}/${action}`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify(payload ?? {}),
  });
}

export function precheckRunApi(planId: number) {
  return request<PrecheckRunReport>(`/api/task-plans/${planId}/precheck-run`, { method: 'POST' });
}

export function precheckSkipApi(planId: number) {
  return request<PlanDocumentResponse>(`/api/task-plans/${planId}/precheck-skip`, { method: 'POST' });
}

export function updatePrecheckSettingsApi(planId: number, settings: PrecheckSettings) {
  return request<PlanDocumentResponse>(`/api/task-plans/${planId}/precheck-settings`, {
    method: 'PUT',
    headers: json,
    body: JSON.stringify(settings),
  });
}

export function listCommentsApi(planId: number) {
  return request<PlanComment[]>(`/api/task-plans/${planId}/comments`);
}

export function addCommentApi(planId: number, content: string) {
  return request<PlanComment>(`/api/task-plans/${planId}/comments`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify({ content }),
  });
}

export function deleteCommentApi(planId: number, commentId: number) {
  return request<void>(`/api/task-plans/${planId}/comments/${commentId}`, { method: 'DELETE' });
}

export function listPlanTemplatesApi(projectId: number) {
  return request<PlanTemplate[]>(`/api/projects/${projectId}/plan-templates`);
}

export function createPlanTemplateApi(projectId: number, payload: { name: string; description?: string; content: string }) {
  return request<PlanTemplate>(`/api/projects/${projectId}/plan-templates`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify(payload),
  });
}

export function updatePlanTemplateApi(templateId: number, payload: { name: string; description?: string; content: string }) {
  return request<PlanTemplate>(`/api/plan-templates/${templateId}`, {
    method: 'PUT',
    headers: json,
    body: JSON.stringify(payload),
  });
}

export function deletePlanTemplateApi(templateId: number) {
  return request<void>(`/api/plan-templates/${templateId}`, { method: 'DELETE' });
}

export function listSnapshotsApi(planId: number) {
  return request<PlanSnapshotView[]>(`/api/task-plans/${planId}/snapshots`);
}

export function createShareApi(planId: number, expiresInDays?: number) {
  return request<PlanShareTokenView>(`/api/task-plans/${planId}/shares`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify(expiresInDays ? { expiresInDays } : {}),
  });
}

export function listSharesApi(planId: number) {
  return request<PlanShareTokenView[]>(`/api/task-plans/${planId}/shares`);
}

export function revokeShareApi(planId: number, tokenId: number) {
  return request<void>(`/api/task-plans/${planId}/shares/${tokenId}`, { method: 'DELETE' });
}

export function getSharedPlanApi(token: string) {
  return request<{ name: string; body: string | null; publishedAt: string | null }>(`/api/share/plans/${token}`);
}

export function quickExecuteApi(scriptVersionId: number) {
  return request<{ planId: number; scenarioId: number; executionId: number }>(
    `/api/scripts/${scriptVersionId}/quick-execute`, { method: 'POST' });
}

export function bindScenarioScriptApi(scenarioId: number, scriptVersionId: number) {
  return request<unknown>(`/api/scenarios/${scenarioId}/script`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify({ scriptVersionId }),
  });
}
