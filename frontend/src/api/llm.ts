import { request } from './http';
import type {
  LlmApiType,
  LlmAvailableProviderGroup,
  LlmCallRecordPage,
  LlmCallScene,
  LlmCallStatus,
  LlmModel,
  LlmProvider,
} from '../types';

export type CreateLlmProviderPayload = {
  name: string;
  baseUrl: string;
  baseUrlAnthropic?: string | null;
  apiKey: string;
  enabled?: boolean;
  storeBodyDefault?: boolean;
};

export type UpdateLlmProviderPayload = {
  name: string;
  baseUrl: string;
  baseUrlAnthropic?: string | null;
  apiKey?: string | null;
  enabled?: boolean;
  storeBodyDefault?: boolean;
};

export function listLlmProvidersApi() {
  return request<LlmProvider[]>('/api/llm/providers');
}

export function createLlmProviderApi(payload: CreateLlmProviderPayload) {
  return request<LlmProvider>('/api/llm/providers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function updateLlmProviderApi(id: number, payload: UpdateLlmProviderPayload) {
  return request<LlmProvider>(`/api/llm/providers/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function deleteLlmProviderApi(id: number, cascade = false) {
  return request<void>(`/api/llm/providers/${id}?cascade=${cascade}`, { method: 'DELETE' });
}

export function fetchLlmModelsApi(providerId: number, apiType: LlmApiType = 'OPENAI') {
  return request<{ apiType: LlmApiType; models: string[] }>(`/api/llm/providers/${providerId}/fetch-models`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ apiType }),
  });
}

export function importLlmModelsApi(
  providerId: number,
  apiType: LlmApiType,
  models: Array<{ modelName: string; displayName?: string }>,
) {
  return request<LlmModel[]>(`/api/llm/providers/${providerId}/import-models`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ apiType, models }),
  });
}

export function testLlmProviderApi(providerId: number, modelId?: number) {
  return request<{
    success: boolean;
    latencyMs: number;
    callRecordId: number;
    content: string;
    errorMessage: string;
  }>(`/api/llm/providers/${providerId}/test`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': 'admin' },
    body: JSON.stringify({ modelId }),
  });
}

export function listLlmModelsApi(providerId?: number) {
  const query = providerId == null ? '' : `?providerId=${providerId}`;
  return request<LlmModel[]>(`/api/llm/models${query}`);
}

export function createLlmModelApi(payload: {
  providerId: number;
  modelName: string;
  displayName?: string;
  apiType?: LlmApiType;
  apiTypes?: LlmApiType[];
  enabled?: boolean;
}) {
  return request<LlmModel>('/api/llm/models', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function updateLlmModelApi(
  id: number,
  payload: { displayName?: string; apiType?: LlmApiType; apiTypes?: LlmApiType[]; enabled?: boolean },
) {
  return request<LlmModel>(`/api/llm/models/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function deleteLlmModelApi(id: number) {
  return request<void>(`/api/llm/models/${id}`, { method: 'DELETE' });
}

export function setDefaultLlmModelApi(id: number) {
  return request<LlmModel>(`/api/llm/models/${id}/default`, { method: 'PUT' });
}

export function listAvailableLlmModelsApi() {
  return request<LlmAvailableProviderGroup[]>('/api/llm/available-models');
}

export function listLlmCallRecordsApi(params: {
  providerId?: number;
  modelId?: number;
  scene?: LlmCallScene;
  status?: LlmCallStatus;
  page?: number;
  size?: number;
}) {
  const search = new URLSearchParams();
  if (params.providerId != null) search.set('providerId', String(params.providerId));
  if (params.modelId != null) search.set('modelId', String(params.modelId));
  if (params.scene) search.set('scene', params.scene);
  if (params.status) search.set('status', params.status);
  search.set('page', String(params.page ?? 0));
  search.set('size', String(params.size ?? 20));
  return request<LlmCallRecordPage>(`/api/llm/call-records?${search.toString()}`);
}
