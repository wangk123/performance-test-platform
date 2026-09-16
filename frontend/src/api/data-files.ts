import type { DataFile, DataFileVersion, DataFileVersionDetail } from '../types';
import { request } from './http';

export function listDataFilesApi(projectId: number) {
  return request<DataFile[]>(`/api/projects/${projectId}/data-files`);
}

export function uploadDataFileApi(
  projectId: number,
  file: File,
  name: string,
  hasHeader: boolean,
  encoding: string,
  remark: string,
  username: string,
) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('name', name);
  formData.append('hasHeader', String(hasHeader));
  formData.append('encoding', encoding);
  formData.append('remark', remark);
  return request<DataFileVersion>(`/api/projects/${projectId}/data-files`, {
    method: 'POST',
    headers: { 'X-User': username },
    body: formData,
  });
}

export function listDataFileVersionsApi(projectId: number, dataFileId: number) {
  return request<DataFileVersion[]>(`/api/projects/${projectId}/data-files/${dataFileId}/versions`);
}

export function getDataFileVersionApi(projectId: number, dataFileId: number, versionNo: number) {
  return request<DataFileVersionDetail>(`/api/projects/${projectId}/data-files/${dataFileId}/versions/${versionNo}`);
}

export function dataFileDownloadUrl(projectId: number, dataFileId: number, versionNo: number): string {
  return `/api/projects/${projectId}/data-files/${dataFileId}/versions/${versionNo}/download`;
}

export function deleteDataFileApi(projectId: number, dataFileId: number) {
  return request<void>(`/api/projects/${projectId}/data-files/${dataFileId}`, {
    method: 'DELETE',
  });
}
