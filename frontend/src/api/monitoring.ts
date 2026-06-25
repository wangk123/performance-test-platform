import type { MonitorDeployResult, MonitorItem, MonitorTarget, TargetMonitoringResult } from '../types';
import { request } from './http';

export type MonitorTargetPayload = {
  name: string;
  serviceName: string;
  host: string;
  sshUsername?: string | null;
  sshPassword?: string | null;
  sshPort?: number | null;
  pluginDir?: string | null;
  port: number;
  metricsPath: string;
  env: string;
  labels: Record<string, string>;
  items: MonitorItem[];
  enabled: boolean;
};

export function listMonitorTargetsApi(projectId: number) {
  return request<MonitorTarget[]>(`/api/projects/${projectId}/monitor-targets`);
}

export function createMonitorTargetApi(projectId: number, payload: MonitorTargetPayload) {
  return request<MonitorTarget>(`/api/projects/${projectId}/monitor-targets`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function updateMonitorTargetApi(targetId: number, payload: MonitorTargetPayload) {
  return request<MonitorTarget>(`/api/monitor-targets/${targetId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function deleteMonitorTargetApi(targetId: number) {
  return request<void>(`/api/monitor-targets/${targetId}`, { method: 'DELETE' });
}

export function checkMonitorTargetApi(targetId: number) {
  return request<MonitorTarget>(`/api/monitor-targets/${targetId}/check`, { method: 'POST' });
}

export function deployMonitorTargetApi(targetId: number) {
  return request<MonitorDeployResult>(`/api/monitor-targets/${targetId}/deploy`, { method: 'POST' });
}

export function getTargetMonitoringApi(taskId: number) {
  return request<TargetMonitoringResult>(`/api/tasks/${taskId}/target-monitoring`);
}
