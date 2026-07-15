import { request } from './http';

export interface SeedDatasource {
  id: number;
  projectId: number;
  name: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  passwordConfigured: boolean;
}

export interface SeedTemplateColumn {
  name: string;
  role: string;
  confidence: string;
  rationale: string;
  refTable?: string | null;
  refColumn?: string | null;
  generator?: string | null;
  lowAccepted: boolean;
}

export interface SeedTemplateOperation {
  type: string;
  table: string;
  riskyNoPk: boolean;
  columns: SeedTemplateColumn[];
}

export interface SeedTemplateDraft {
  operations: SeedTemplateOperation[];
}

export interface SeedTemplateDetail {
  id: number;
  status: string;
  versionNo: number;
  body: SeedTemplateDraft;
  confirmedBy: string;
  confirmedAt: string;
}

export interface SeedCloneJob {
  id: number;
  templateId: number;
  datasourceId: number;
  cloneCount: number;
  failurePolicy: string;
  status: string;
  successBatches: number;
  failedBatches: number;
  errors: string[];
  createdBy: string;
  createdAt: string;
  finishedAt: string;
}

export type SeedJsonObject = Record<string, unknown>;
export interface SeedCaptureStrategy {
  id: number;
  projectId: number;
  name: string;
  datasourceId: number;
  includes: string[];
  excludes: string[];
  threadCount: number;
  batchRows: number;
  configVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSeedCaptureStrategyRequest {
  name: string;
  datasourceId: number;
  includes: string[];
  excludes: string[];
  threadCount: number;
  batchRows: number;
}

export interface SeedCaptureSample {
  id: number;
  strategyId: number;
  datasourceId: number;
  sampleSeq: number;
  status: string;
  phase: string;
  captureStartedAt: string;
  captureFinishedAt: string | null;
  configVersion: number;
  completedTables: number;
  totalTables: number;
  currentTables: string[];
  capturedRows: number;
  writtenBytes: number;
  activeWorkers: number;
  heartbeatAt: string | null;
  errorMessage: string | null;
  incomplete: boolean;
  configSnapshot: SeedJsonObject;
}

export interface SeedCaptureSamplePage {
  content: SeedCaptureSample[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SeedCaptureChunk {
  id?: number;
  chunkSeq: number;
  rowCount: number;
  contentHash: string | null;
  relativePath: string | null;
  fileChecksum: string | null;
  status: string;
  byteSize: number | null;
  checksumValid?: boolean;
  incomplete?: boolean;
}

export interface SeedCaptureSampleTable {
  id: number;
  sampleId: number;
  tableName: string;
  schema: SeedJsonObject;
  schemaHash: string | null;
  rowCount: number;
  contentHash: string | null;
  riskyNoPk: boolean;
  status: string;
  incomplete: boolean;
  errorMessage: string | null;
  chunkCount: number;
  chunks: SeedCaptureChunk[];
}

export interface SeedCaptureSampleTables {
  sampleId: number;
  tableCount: number;
  incomplete: boolean;
  tables: SeedCaptureSampleTable[];
}

export interface SeedCaptureRowsPage {
  sampleId: number;
  tableName: string;
  rows: SeedJsonObject[];
  nextCursor: string | null;
  schema: SeedJsonObject;
  incomplete: boolean;
  checksumValid: boolean;
  chunks: SeedCaptureChunk[];
}

export interface SeedCaptureDiffIntervalSummary {
  status: string;
  insertCount: number;
  updateCount: number;
  deleteCount: number;
  comparedRows: number;
  skippedRows: number;
  riskyNoPk: boolean;
  diagnostics: string[];
  screeningEvidence: Array<SeedJsonObject>;
}

export interface SeedCaptureAnalysisTableSummary {
  tableName: string;
  intervals: Array<SeedCaptureDiffIntervalSummary | SeedJsonObject>;
}

export interface SeedCaptureAnalysisSummary extends SeedJsonObject {
  intervalCount?: number;
  tableCount?: number;
  comparedRows?: number;
  skippedTables?: number;
  fineScreenedChunks?: number;
  candidateOperationCount?: number;
  warnings?: Array<string | SeedJsonObject>;
  risks?: Array<string | SeedJsonObject>;
  tables?: SeedCaptureAnalysisTableSummary[];
}

export interface SeedCaptureAnalysis {
  id: number;
  analysisId: number;
  projectId: number;
  strategyId: number;
  status: string;
  phase: string;
  completedTables: number;
  totalTables: number;
  currentTables: string[];
  comparedRows: number;
  skippedTables: number;
  fineScreenedChunks: number;
  candidateOperationCount: number;
  inputSampleIds: number[];
  inputManifest: SeedJsonObject;
  summary: SeedCaptureAnalysisSummary;
  templateId: number | null;
  heartbeatAt: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
}

export interface CreateSeedCaptureAnalysisRequest {
  strategyId: number;
  sampleIds: number[];
  confirmIncomplete: boolean;
}

export interface SeedCaptureAnalysisResult {
  id: number;
  resultType: string;
  chunkSeq: number | null;
  rowCount: number;
  relativePath: string | null;
  summary: SeedJsonObject;
  checksumValid?: boolean;
  incomplete?: boolean;
}

export interface SeedCaptureAnalysisDiffPage {
  analysisId: number;
  tableName: string;
  rows: SeedJsonObject[];
  nextCursor: string | null;
  results: SeedCaptureAnalysisResult[];
  incomplete: boolean;
  checksumValid: boolean;
}
function withQuery(
  path: string,
  params: Record<string, string | number | undefined | null>,
) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `${path}?${query}` : path;
}

function jsonRequest(method: 'POST' | 'PUT', body: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}

export function listSeedDatasources(projectId: number) {
  return request<SeedDatasource[]>(`/api/projects/${projectId}/seed/datasources`);
}

export function createSeedDatasource(projectId: number, body: Record<string, unknown>) {
  return request<SeedDatasource>(`/api/projects/${projectId}/seed/datasources`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export function updateSeedDatasource(projectId: number, id: number, body: Record<string, unknown>) {
  return request<SeedDatasource>(`/api/projects/${projectId}/seed/datasources/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export function deleteSeedDatasource(projectId: number, id: number) {
  return request<void>(`/api/projects/${projectId}/seed/datasources/${id}`, { method: 'DELETE' });
}

export function testSeedDatasource(projectId: number, id: number) {
  return request<{ ok: boolean; message: string }>(`/api/projects/${projectId}/seed/datasources/${id}/test`, {
    method: 'POST',
  });
}

export function listSeedCaptureStrategies(projectId: number) {
  return request<SeedCaptureStrategy[]>(`/api/projects/${projectId}/seed/capture-strategies`);
}

export function createSeedCaptureStrategy(projectId: number, body: CreateSeedCaptureStrategyRequest) {
  return request<SeedCaptureStrategy>(
    `/api/projects/${projectId}/seed/capture-strategies`,
    jsonRequest('POST', body),
  );
}

export function getSeedCaptureStrategy(projectId: number, strategyId: number) {
  return request<SeedCaptureStrategy>(
    `/api/projects/${projectId}/seed/capture-strategies/${strategyId}`,
  );
}

export function updateSeedCaptureStrategy(
  projectId: number,
  strategyId: number,
  body: CreateSeedCaptureStrategyRequest,
) {
  return request<SeedCaptureStrategy>(
    `/api/projects/${projectId}/seed/capture-strategies/${strategyId}`,
    jsonRequest('PUT', body),
  );
}

export function deleteSeedCaptureStrategy(projectId: number, strategyId: number) {
  return request<void>(
    `/api/projects/${projectId}/seed/capture-strategies/${strategyId}`,
    { method: 'DELETE' },
  );
}

export function executeSeedCaptureStrategy(projectId: number, strategyId: number) {
  return request<SeedCaptureSample>(
    `/api/projects/${projectId}/seed/capture-strategies/${strategyId}/execute`,
    { method: 'POST' },
  );
}

export function listSeedCaptureSamples(
  projectId: number,
  strategyId: number,
  params: {
    status?: string[] | string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  } = {},
) {
  const status = Array.isArray(params.status) ? params.status.join(',') : params.status;
  return request<SeedCaptureSamplePage>(
    withQuery(`/api/projects/${projectId}/seed/capture-strategies/${strategyId}/samples`, {
      status,
      from: params.from,
      to: params.to,
      page: params.page ?? 0,
      size: params.size ?? 20,
    }),
  );
}

export function getSeedCaptureSample(projectId: number, sampleId: number) {
  return request<SeedCaptureSample>(`/api/projects/${projectId}/seed/samples/${sampleId}`);
}

export function cancelSeedCaptureSample(projectId: number, sampleId: number) {
  return request<SeedCaptureSample>(
    `/api/projects/${projectId}/seed/samples/${sampleId}/cancel`,
    { method: 'POST' },
  );
}

export function deleteSeedCaptureSample(projectId: number, sampleId: number) {
  return request<SeedCaptureSample | void>(
    `/api/projects/${projectId}/seed/capture-samples/${sampleId}`,
    { method: 'DELETE' },
  );
}

export function listSeedCaptureSampleTables(projectId: number, sampleId: number) {
  return request<SeedCaptureSampleTables>(
    `/api/projects/${projectId}/seed/capture-samples/${sampleId}/tables`,
  );
}

export function listSeedCaptureSampleRows(
  projectId: number,
  sampleId: number,
  tableName: string,
  cursor?: string,
  limit = 100,
) {
  return request<SeedCaptureRowsPage>(
    withQuery(
      `/api/projects/${projectId}/seed/capture-samples/${sampleId}/tables/${encodeURIComponent(tableName)}/rows`,
      { cursor, limit },
    ),
  );
}

export function createSeedCaptureAnalysis(projectId: number, body: CreateSeedCaptureAnalysisRequest) {
  return request<SeedCaptureAnalysis>(
    `/api/projects/${projectId}/seed/capture-analyses`,
    jsonRequest('POST', body),
  );
}

export function listSeedCaptureAnalyses(projectId: number) {
  return request<SeedCaptureAnalysis[]>(`/api/projects/${projectId}/seed/capture-analyses`);
}

export function getSeedCaptureAnalysis(projectId: number, analysisId: number) {
  return request<SeedCaptureAnalysis>(
    `/api/projects/${projectId}/seed/capture-analyses/${analysisId}`,
  );
}

export function cancelSeedCaptureAnalysis(projectId: number, analysisId: number) {
  return request<SeedCaptureAnalysis>(
    `/api/projects/${projectId}/seed/capture-analyses/${analysisId}/cancel`,
    { method: 'POST' },
  );
}

export function deleteSeedCaptureAnalysis(projectId: number, analysisId: number) {
  return request<SeedCaptureAnalysis | void>(
    `/api/projects/${projectId}/seed/capture-analyses/${analysisId}`,
    { method: 'DELETE' },
  );
}

export function listSeedCaptureAnalysisDiffs(
  projectId: number,
  analysisId: number,
  tableName: string,
  cursor?: string,
  limit = 100,
) {
  return request<SeedCaptureAnalysisDiffPage>(
    withQuery(
      `/api/projects/${projectId}/seed/capture-analyses/${analysisId}/tables/${encodeURIComponent(tableName)}/diffs`,
      { cursor, limit },
    ),
  );
}

export function listSeedTemplates(projectId: number) {
  return request<Array<Record<string, unknown>>>(`/api/projects/${projectId}/seed/templates`);
}

export function getSeedTemplate(projectId: number, templateId: number) {
  return request<SeedTemplateDetail>(`/api/projects/${projectId}/seed/templates/${templateId}`);
}

export function updateSeedTemplate(projectId: number, templateId: number, draft: SeedTemplateDraft) {
  return request<SeedTemplateDetail>(`/api/projects/${projectId}/seed/templates/${templateId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(draft),
  });
}

export function confirmSeedTemplate(projectId: number, templateId: number, operator?: string) {
  return request<SeedTemplateDetail>(`/api/projects/${projectId}/seed/templates/${templateId}/confirm`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ operator: operator || 'web' }),
  });
}

export function listSeedCloneJobs(projectId: number) {
  return request<SeedCloneJob[]>(`/api/projects/${projectId}/seed/clone-jobs`);
}

export function createSeedCloneJob(projectId: number, body: Record<string, unknown>) {
  return request<SeedCloneJob>(`/api/projects/${projectId}/seed/clone-jobs`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}
