import type { ExecutionNode, ExecutionNodeRole } from '../types';
import { request } from './http';

export type RegisterExecutionNodePayload = {
  name: string;
  host: string;
  sshPort: number;
  sshUsername: string;
  sshKeyPath: string;
  role: ExecutionNodeRole;
  remoteWorkDir: string;
  sshPassword?: string;
};

export type InitializeExecutionNodesPayload = {
  hosts: string[];
  sshPort: number;
  sshUsername: string;
  sshPassword: string;
  remoteWorkDir: string;
};

export function listExecutionNodesApi() {
  return request<ExecutionNode[]>('/api/execution-nodes');
}

export function registerExecutionNodeApi(payload: RegisterExecutionNodePayload) {
  return request<ExecutionNode>('/api/execution-nodes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function updateExecutionNodeApi(nodeId: number, payload: RegisterExecutionNodePayload) {
  return request<ExecutionNode>(`/api/execution-nodes/${nodeId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function deleteExecutionNodeApi(nodeId: number) {
  return request<void>(`/api/execution-nodes/${nodeId}`, { method: 'DELETE' });
}

export function initializeExecutionNodesApi(payload: InitializeExecutionNodesPayload) {
  return request<ExecutionNode[]>('/api/execution-nodes/initialize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function checkExecutionNodeApi(nodeId: number) {
  return request<ExecutionNode>(`/api/execution-nodes/${nodeId}/check`, { method: 'POST' });
}
