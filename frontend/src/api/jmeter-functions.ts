import { request } from './http';

export interface JmeterFunctionParameter {
  name: string;
  description: string;
  required: boolean;
}

export interface JmeterFunctionDefinition {
  key: string;
  displayName: string;
  category: string;
  description: string;
  parameters: JmeterFunctionParameter[];
  example: string;
}

export function fetchJmeterFunctions() {
  return request<JmeterFunctionDefinition[]>('/api/jmeter-functions');
}

export function downloadJmeterFunctionPackage() {
  return fetch('/api/jmeter-functions/download').then(async (response) => {
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '下载失败' }));
      throw new Error(error.message || '下载失败');
    }
    return response.blob();
  });
}
