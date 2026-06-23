import type { ScriptAsset, ScriptStep, ScriptVersionRecord } from '../types';
import { defaultParams } from '../utils/jmeter';
import { request } from './http';

export type BackendScriptVersion = {
  id: number;
  projectId: number;
  versionNo: number;
  originalFilename: string;
  uploadedBy: string;
  uploadedAt: string;
};

export type BackendScriptDefinition = {
  id: number;
  projectId: number;
  name: string;
  sourceFile: string;
  latestVersion: number;
  parseStatus: 'PARSED' | 'PARSE_FAILED';
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
  return request<BackendScriptVersion>(`/api/projects/${projectId}/scripts/${versionId}`, {
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
  return request<BackendScriptDefinition>(`/api/projects/${projectId}/scripts/${versionId}/definition`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({ filename, steps }),
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
