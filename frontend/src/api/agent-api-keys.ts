import { request } from './http';

export type AgentApiKeyView = {
  id: number;
  prefix: string;
  scope: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
  createdAt: string;
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
};

export type IssuedAgentApiKey = {
  id: number;
  plainKey: string;
  prefix: string;
  scope: string | null;
  expiresAt: string | null;
  createdAt: string;
};

export function listAgentApiKeysApi() {
  return request<AgentApiKeyView[]>('/api/agent-api-keys');
}

export function issueAgentApiKeyApi(payload?: { scope?: string; expiresAt?: string | null }) {
  return request<IssuedAgentApiKey>('/api/agent-api-keys', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload ?? {}),
  });
}

export function revokeAgentApiKeyApi(id: number) {
  return request<void>(`/api/agent-api-keys/${id}`, {
    method: 'DELETE',
  });
}
