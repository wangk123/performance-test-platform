import { request } from './http';

export interface PlanReportResponse {
  plan: PlanInfo;
  scenarios: ScenarioReport[];
}

export interface PlanInfo {
  planId: number;
  projectId: number;
  planName: string;
  remark: string;
}

export interface ScenarioReport {
  scenarioId: number;
  scriptVersionId: number;
  scenarioName: string;
  scriptName: string;
  rounds: RoundReport[];
}

export interface RoundReport {
  executionId: number;
  executionName: string | null;
  status: string;
  startedAt: string | null;
  endedAt: string | null;
  durationMs: number | null;
  threads: number;
  rampUp: number;
  duration: number;
  loops: number;
  summary: AggregateSummary;
  aggregateRows: AggregateRow[];
  metricSeries: MetricSeriesData;
  failures: FailureSummary;
}

export interface AggregateSummary {
  samples: number;
  throughput: number;
  avgRt: number;
  p95: number;
  errorRate: number;
  accuracy: string;
}

export interface AggregateRow {
  label: string;
  threadName: string;
  samples: number;
  average: number;
  median: number;
  p90: number;
  p95: number;
  p99: number;
  min: number;
  max: number;
  errorRate: number;
  throughput: number;
}

export interface MetricSeriesData {
  ticks: MetricTick[];
}

export interface MetricTick {
  bucketTimeMs: number;
  overall: LabelMetric;
}

export interface LabelMetric {
  label: string;
  samples: number;
  errorSamples: number;
  throughput: number;
  avgRtMs: number;
  p95RtMs: number;
}

export interface FailureSummary {
  errorCount: number;
  truncated: boolean;
  samples: FailureSample[];
}

export interface FailureSample {
  id: number;
  time: string;
  statusCode: string;
  success: boolean;
  label: string;
  elapsed: number;
  message: string;
  threadName: string;
}

export interface ReportExportRequest {
  chartImages: Record<string, string>;
  editorContent: string;
}

export function fetchPlanReport(planId: number) {
  return request<PlanReportResponse>(`/api/reports/plans/${planId}/data`);
}

export function exportWordReport(planId: number, payload: ReportExportRequest) {
  return fetch(`/api/reports/plans/${planId}/export/word`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '导出失败' }));
      throw new Error(error.message || '导出失败');
    }
    return response.blob();
  });
}
