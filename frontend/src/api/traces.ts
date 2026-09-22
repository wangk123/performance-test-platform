import type { ExecutionTraceDetail, ExecutionTracesPage } from '../types';
import { request } from './http';

/** 执行链路列表：OAP 不可达时 200 + available=false，不抛 5xx（对照 ExecutionTraceController）。 */
export function getExecutionTracesApi(
  executionId: number,
  params: {
    service?: string;
    endpoint?: string;
    onlyError?: boolean;
    minDurationMs?: number;
    sort?: 'duration' | 'time';
    page?: number;
    size?: number;
  } = {},
) {
  const search = new URLSearchParams();
  if (params.service) search.set('service', params.service);
  if (params.endpoint) search.set('endpoint', params.endpoint);
  if (params.onlyError) search.set('onlyError', 'true');
  if (params.minDurationMs) search.set('minDurationMs', String(params.minDurationMs));
  if (params.sort) search.set('sort', params.sort);
  if (params.page) search.set('page', String(params.page));
  if (params.size) search.set('size', String(params.size));
  return request<ExecutionTracesPage>(`/api/executions/${executionId}/traces?${search.toString()}`);
}

/** 单条链路详情：available=false 时 trace 为 null（retention-expired 仅出现在此端点）。 */
export function getExecutionTraceDetailApi(executionId: number, traceId: string) {
  return request<ExecutionTraceDetail>(`/api/executions/${executionId}/traces/${encodeURIComponent(traceId)}`);
}
