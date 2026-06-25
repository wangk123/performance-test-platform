import type { MetricKind, TargetMetricsQueryResult } from '../types';
import { request } from './http';

export function getTargetMonitoringSeriesApi(
  executionId: number,
  kind: MetricKind,
  params: { targetIds?: number[]; itemId?: string | null; step?: number },
) {
  const search = new URLSearchParams({ kind });
  params.targetIds?.forEach((id) => search.append('targetIds', String(id)));
  if (params.itemId) {
    search.set('itemId', params.itemId);
  }
  if (params.step) {
    search.set('step', String(params.step));
  }
  return request<TargetMetricsQueryResult>(`/api/executions/${executionId}/target-monitoring/series?${search.toString()}`);
}
