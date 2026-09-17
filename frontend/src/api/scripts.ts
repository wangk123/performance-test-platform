import type { ScriptAsset, ScriptStep, ScriptVersionRecord } from '../types';
import { defaultParams } from '../utils/jmeter';
import { request } from './http';

export type BackendScriptVersion = {
  id: number;
  projectId: number;
  scriptId: number | null;
  versionNo: number;
  originalFilename: string;
  uploadedBy: string;
  uploadedAt: string;
  status: 'DRAFT' | 'PUBLISHED';
  remark: string | null;
};

export type BackendScriptDefinition = {
  id: number;
  projectId: number;
  name: string;
  sourceFile: string;
  latestVersion: number;
  status: 'DRAFT' | 'PUBLISHED';
  scriptId?: number;
  remark: string;
  updatedAt: string;
  steppingThreadGroupSupported?: boolean;
  steps: ScriptStep[];
  versions: BackendScriptVersion[];
};

export type BackendScriptContent = {
  version: BackendScriptVersion;
  content: string;
};

// 保存响应（Task 13 起）：仅回新版本号 + 明文密钥扫描 warnings，不再回全量 definition
export type SaveScriptResult = {
  version: BackendScriptVersion;
  warnings: string[];
};

export function listScriptDefinitionsApi(projectId: number) {
  return request<BackendScriptDefinition[]>(`/api/projects/${projectId}/scripts/definitions`);
}

export type BackendScriptAssetSummary = {
  id: number;
  projectId: number;
  name: string;
  latestVersionNo: number;
  latestPublished: BackendScriptVersion | null;
  hasDraft: boolean;
  draftUpdatedAt: string | null;
  currentScenarioCount: number;
  outdatedScenarioCount: number;
};

export type BackendScriptVersionWithRefs = {
  version: BackendScriptVersion;
  referencedScenarioNames: string[];
};

export function listScriptAssetsApi(projectId: number) {
  return request<BackendScriptAssetSummary[]>(`/api/projects/${projectId}/scripts/assets`);
}

export function listScriptVersionsApi(projectId: number, scriptId: number) {
  return request<BackendScriptVersionWithRefs[]>(
    `/api/projects/${projectId}/scripts/${scriptId}/versions`,
  );
}

export function saveDraftApi(projectId: number, scriptId: number, filename: string, steps: ScriptStep[], username: string) {
  return request<SaveScriptResult>(`/api/projects/${projectId}/scripts/${scriptId}/draft`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({ filename, steps }),
  });
}

export function publishScriptApi(projectId: number, scriptId: number, versionNo: number, remark: string, username: string) {
  return request<BackendScriptVersion>(`/api/projects/${projectId}/scripts/${scriptId}/publish`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({ versionNo, remark }),
  });
}

export function forkDraftApi(projectId: number, scriptId: number, sourceVersionId: number, username: string) {
  return request<BackendScriptVersion>(`/api/projects/${projectId}/scripts/${scriptId}/fork-draft`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({ sourceVersionId }),
  });
}

export function deleteScriptVersionApi(projectId: number, scriptId: number, versionId: number) {
  return request<void>(`/api/projects/${projectId}/scripts/${scriptId}/versions/${versionId}`, {
    method: 'DELETE',
  });
}

export function deleteScriptAssetApi(projectId: number, scriptId: number) {
  return request<void>(`/api/projects/${projectId}/scripts/${scriptId}`, { method: 'DELETE' });
}

/** 脚本维度 summary + 版本历史 → 前端 ScriptAsset（id = 当前生效版本 id，兼容绑定/编辑器入口）。 */
export function mapScriptAsset(
  summary: BackendScriptAssetSummary,
  versionRows: BackendScriptVersionWithRefs[],
): ScriptAsset {
  const latestPublished = versionRows.find((row) => row.version.status === 'PUBLISHED') ?? null;
  const draftRow = versionRows.find((row) => row.version.status === 'DRAFT') ?? null;
  const active = latestPublished ?? draftRow;
  return {
    id: active?.version.id ?? summary.id,
    projectId: summary.projectId,
    scriptId: summary.id,
    hasDraft: summary.hasDraft,
    draftVersionId: draftRow?.version.id ?? null,
    currentScenarioCount: summary.currentScenarioCount,
    outdatedScenarioCount: summary.outdatedScenarioCount,
    name: summary.name,
    sourceFile: active?.version.originalFilename ?? `${summary.name}.jmx`,
    latestVersion: latestPublished?.version.versionNo ?? 0,
    status: latestPublished ? 'PUBLISHED' : 'DRAFT',
    remark: latestPublished?.version.remark ?? '',
    updatedAt: active?.version.uploadedAt ?? '',
    steppingThreadGroupSupported: false,
    apis: [],
    monitors: [],
    variables: [],
    params: defaultParams(),
    versions: versionRows.map((row) => ({
      id: row.version.id,
      status: row.version.status,
      remark: row.version.remark ?? '',
      versionNo: row.version.versionNo,
      fileName: row.version.originalFilename,
      fileSize: 0,
      fileHash: '',
      importedAt: row.version.uploadedAt,
      importedBy: row.version.uploadedBy,
      referencedScenarioNames: row.referencedScenarioNames,
    })),
    steps: [],
  };
}

export async function uploadScriptApi(projectId: number, file: File, username: string, remark = '') {
  const formData = new FormData();
  formData.append('file', file);
  if (remark) {
    formData.append('remark', remark);
  }
  const version = await request<BackendScriptVersion>(`/api/projects/${projectId}/scripts`, {
    method: 'POST',
    headers: { 'X-User': username },
    body: formData,
  });
  return getScriptDefinitionApi(projectId, version.id);
}

export function createScriptApi(projectId: number, name: string, username: string) {
  return request<BackendScriptDefinition>(`/api/projects/${projectId}/scripts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({ name }),
  });
}

export function getScriptDefinitionApi(projectId: number, versionId: number) {
  return request<BackendScriptDefinition>(`/api/projects/${projectId}/scripts/${versionId}/definition`);
}

export function deleteScriptApi(projectId: number, versionId: number) {
  return request<void>(`/api/projects/${projectId}/scripts/${versionId}`, {
    method: 'DELETE',
  });
}

export function getScriptContentApi(projectId: number, versionId: number) {
  return request<BackendScriptContent>(`/api/projects/${projectId}/scripts/${versionId}`);
}

export function saveScriptContentApi(projectId: number, versionId: number, filename: string, content: string, username: string) {
  return request<SaveScriptResult>(`/api/projects/${projectId}/scripts/${versionId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({ filename, content }),
  });
}

export function saveScriptDefinitionApi(
  projectId: number,
  versionId: number,
  filename: string,
  steps: ScriptStep[],
  username: string,
) {
  return request<SaveScriptResult>(`/api/projects/${projectId}/scripts/${versionId}/definition`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({ filename, steps }),
  });
}

export function mapScriptDefinition(definition: BackendScriptDefinition): ScriptAsset {
  return {
    id: definition.id,
    projectId: definition.projectId,
    scriptId: definition.scriptId ?? definition.id,
    hasDraft: definition.status === 'DRAFT',
    draftVersionId: definition.status === 'DRAFT' ? definition.id : null,
    currentScenarioCount: 0,
    outdatedScenarioCount: 0,
    name: definition.name,
    sourceFile: definition.sourceFile,
    latestVersion: definition.latestVersion,
    status: definition.status ?? 'PUBLISHED',
    remark: definition.remark,
    updatedAt: definition.updatedAt,
    steppingThreadGroupSupported: definition.steppingThreadGroupSupported ?? false,
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

function mapVersion(version: BackendScriptVersion): ScriptVersionRecord {
  return {
    id: version.id,
    status: version.status ?? 'PUBLISHED',
    remark: version.remark ?? '',
    versionNo: version.versionNo,
    fileName: version.originalFilename,
    fileSize: 0,
    fileHash: '',
    importedAt: version.uploadedAt,
    importedBy: version.uploadedBy,
  };
}

function flattenSteps(steps: ScriptStep[]): ScriptStep[] {
  return steps.flatMap((step) => [step, ...flattenSteps(step.children)]);
}
