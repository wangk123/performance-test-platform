import type { HttpDebugResult } from '../utils/http-debug';
import { request } from './http';

export type HttpDebugApiRequest = {
  method: string;
  url: string;
  headers: Record<string, string>;
  body: string;
  timeoutMs: number;
};

export function executeHttpDebugApi(payload: HttpDebugApiRequest) {
  return request<HttpDebugResult>('/api/http-debug', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}
