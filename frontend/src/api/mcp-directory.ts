import { request } from './http';
import type { McpDirectory } from '../types';

export function fetchMcpDirectoryApi() {
  return request<McpDirectory>('/api/mcp/tools');
}
