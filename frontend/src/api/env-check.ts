import type {
  EnvCheckCredential,
  EnvCheckCredentialInput,
  EnvCheckFixRecord,
  EnvCheckItemsResponse,
  EnvCheckRunDetail,
  EnvCheckRunSummary,
  EnvCheckTargetsPreview,
} from '../types';
import { request } from './http';

const json = { 'Content-Type': 'application/json' };

/** 检查项目录（含 risk：LOCAL 项为 null）；fixEnabled 为平台修复总闸。 */
export function fetchEnvCheckItemsApi() {
  return request<EnvCheckItemsResponse>('/api/env-check/items', { method: 'GET' });
}

/** 检查总览目标预览：实时解析计划文档「环境部署信息」，只读。 */
export function fetchEnvCheckTargetsApi(planId: number) {
  return request<EnvCheckTargetsPreview>(`/api/task-plans/${planId}/env-check/targets`, { method: 'GET' });
}

export function fetchCredentialsApi(projectId: number) {
  return request<EnvCheckCredential[]>(`/api/projects/${projectId}/env-check/credentials`, { method: 'GET' });
}

/** 保存（新增或按 project+plan+host 覆盖）：密码/密钥只写不回显。 */
export function saveCredentialApi(projectId: number, input: EnvCheckCredentialInput) {
  return request<EnvCheckCredential>(`/api/projects/${projectId}/env-check/credentials`, {
    method: 'PUT',
    headers: json,
    body: JSON.stringify(input),
  });
}

export function deleteCredentialApi(projectId: number, id: number) {
  return request<void>(`/api/projects/${projectId}/env-check/credentials/${id}`, { method: 'DELETE' });
}

export function testCredentialApi(projectId: number, id: number) {
  return request<{ ok: boolean; message: string }>(`/api/projects/${projectId}/env-check/credentials/${id}/test`, { method: 'POST' });
}

/** 触发一次环境检查，返回摘要（RunSummary）；矩阵见详情接口。 */
export function triggerEnvCheckApi(planId: number) {
  return request<EnvCheckRunSummary>(`/api/task-plans/${planId}/env-check/runs`, { method: 'POST' });
}

export function fetchEnvCheckRunsApi(planId: number) {
  return request<EnvCheckRunSummary[]>(`/api/task-plans/${planId}/env-check/runs`, { method: 'GET' });
}

/** 运行详情（RunDetail）：run 含 detailJson 原文（解析得 targets/results），rows 为结果矩阵。 */
export function fetchEnvCheckRunDetailApi(runId: number) {
  return request<EnvCheckRunDetail>(`/api/env-check/runs/${runId}`, { method: 'GET' });
}

/** 本 run 的修复记录（含 diff），按 id 倒序。 */
export function fetchEnvCheckFixesApi(runId: number) {
  return request<EnvCheckFixRecord[]>(`/api/env-check/runs/${runId}/fixes`, { method: 'GET' });
}

/** 批量修复（spec §4.5）：返回 fixed/skipped/failed 分流，元素格式 itemKey@host。 */
export function applyEnvCheckFixesApi(runId: number, requests: Array<{ host: string; itemKey: string }>) {
  return request<{ fixed: string[]; skipped: string[]; failed: string[] }>(`/api/env-check/runs/${runId}/fixes`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify({ requests }),
  });
}

export function rollbackEnvCheckFixApi(fixId: number) {
  return request<void>(`/api/env-check/fixes/${fixId}/rollback`, { method: 'POST' });
}
