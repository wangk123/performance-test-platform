import type { EvidenceImage, MethodExecutionRow, MethodScenarioData, MethodSectionData } from '../types';
import { request } from './http';

export type { EvidenceImage, MethodExecutionRow, MethodScenarioData, MethodSectionData };

export function getPlanMethodApi(planId: number): Promise<MethodSectionData> {
  return request<MethodSectionData>(`/api/task-plans/${planId}/method`);
}

export function setExecutionVisibilityApi(executionId: number, hidden: boolean): Promise<void> {
  return request<void>(`/api/executions/${executionId}/method-visibility`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ hidden }),
  });
}

export function uploadEvidenceImageApi(
  planId: number,
  scenarioId: number,
  file: File,
  caption?: string,
  executionId?: number,
): Promise<EvidenceImage> {
  const form = new FormData();
  form.append('file', file);
  if (caption) form.append('caption', caption);
  if (executionId != null) form.append('executionId', String(executionId));
  return request<EvidenceImage>(`/api/task-plans/${planId}/scenarios/${scenarioId}/evidence-images`, {
    method: 'POST',
    body: form,
  });
}

export function updateEvidenceImageApi(imageId: number, patch: { caption?: string; sortOrder?: number }): Promise<EvidenceImage> {
  return request<EvidenceImage>(`/api/images/${imageId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch),
  });
}

export function deleteEvidenceImageApi(imageId: number): Promise<void> {
  return request<void>(`/api/images/${imageId}`, { method: 'DELETE' });
}
