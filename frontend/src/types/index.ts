export type User = {
  username: string;
  displayName: string;
  roles: string[];
};

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';
export type ProjectRole = 'OWNER' | 'MEMBER';
export type StatusFilter = 'ALL' | ProjectStatus;
export type ProjectTab = 'overview' | 'scripts' | 'task-plans' | 'monitoring' | 'env-check' | 'reports' | 'data' | 'functions' | 'members';
export type MainNav = 'home' | 'projects' | 'executionNodes' | 'mcpTools' | 'settings' | 'llmConfig';
export type ConfigTab = 'users' | 'roles' | 'permissions' | 'agent-api-keys';
export type LlmConfigTab = 'llm-providers' | 'llm-models' | 'llm-call-records';

export type LlmApiType = 'OPENAI' | 'ANTHROPIC';
export type LlmCallScene = 'TEST_CONNECTION';
export type LlmCallStatus = 'SUCCESS' | 'FAILED';

export type LlmProvider = {
  id: number;
  name: string;
  baseUrl: string;
  baseUrlAnthropic: string | null;
  apiKeyConfigured: boolean;
  enabled: boolean;
  storeBodyDefault: boolean;
  createdAt: string;
  updatedAt: string;
};

export type LlmModel = {
  id: number;
  providerId: number;
  modelName: string;
  displayName: string | null;
  apiTypes: LlmApiType[];
  apiType: LlmApiType;
  enabled: boolean;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
};

export type LlmAvailableProviderGroup = {
  providerId: number;
  providerName: string;
  models: Array<{
    modelId: number;
    modelName: string;
    displayName: string | null;
    apiTypes: LlmApiType[];
    apiType: LlmApiType;
    isDefault: boolean;
  }>;
};

export type LlmCallRecord = {
  id: number;
  providerId: number | null;
  modelId: number | null;
  providerNameSnapshot: string | null;
  modelNameSnapshot: string | null;
  apiType: LlmApiType;
  scene: LlmCallScene;
  status: LlmCallStatus;
  latencyMs: number | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  errorMessage: string | null;
  requestBody: string | null;
  responseBody: string | null;
  triggeredBy: string | null;
  createdAt: string;
};

export type LlmCallRecordPage = {
  content: LlmCallRecord[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
export type ScriptStepType =
  | 'THREAD_GROUP'
  | 'HTTP_REQUEST'
  | 'ASSERTION'
  | 'JSON_ASSERTION'
  | 'CSV_DATA'
  | 'USER_PARAMS'
  | 'HEADER_CONFIG'
  | 'JSR223_PRE_PROCESSOR'
  | 'JSR223_POST_PROCESSOR';
export type Jsr223Config = {
  scriptLanguage: string;
  script: string;
  parameters: string;
  cacheKey: boolean;
};
export type StepRelation = 'root' | 'child';
export type StepDropMode = 'before' | 'after' | 'child';
export type ThreadGroupMode = 'count' | 'duration' | 'stepping';
export type AssertionTarget = 'body' | 'statusCode' | 'headers';
export type AssertionMatch = 'contains' | 'equals' | 'regex';

export type Project = {
  id: number;
  code: string;
  name: string;
  description: string;
  ownerUsername: string;
  status: ProjectStatus;
  createdAt: string;
  updatedAt: string;
};

export type ProjectMember = {
  id: number;
  projectId: number;
  username: string;
  displayName: string;
  role: ProjectRole;
};

export type ThreadGroup = {
  name: string;
  threads: number;
  rampUp: number;
  loops: number;
  duration: number;
  scheduler?: boolean;
  mode?: ThreadGroupMode;
  stepping?: ThreadGroupSteppingConfig;
};

export type ThreadGroupSteppingConfig = {
  initialDelay: number;
  startUsersCount: number;
  startUsersPeriod: number;
  rampUp: number;
  flightTime: number;
  stopUsersCount: number;
  stopUsersPeriod: number;
  burst: boolean;
};

export type ApiConfig = {
  method: string;
  path: string;
  domain: string;
};

export type MonitorConfig = {
  target: string;
  metrics: string[];
};

export type KeyValue = {
  key: string;
  value: string;
};

export type HttpParamConfig = {
  enabled: boolean;
  key: string;
  value: string;
  description: string;
};

export type HttpBodyType = 'none' | 'form-data' | 'form-urlencoded' | 'raw';
export type HttpRawBodyType = 'text' | 'javascript' | 'json' | 'html' | 'xml';

export type HttpAdvancedConfig = {
  connectTimeout: number;
  responseTimeout: number;
  followRedirects: boolean;
  keepAlive: boolean;
};

export type HttpRequestConfig = {
  method: string;
  url: string;
  params: HttpParamConfig[];
  headers: HttpParamConfig[];
  bodyType: HttpBodyType;
  rawBodyType: HttpRawBodyType;
  body: string;
  bodyParams: HttpParamConfig[];
  advanced: HttpAdvancedConfig;
};

export type ResponseAssertionConfig = {
  target: AssertionTarget;
  match: AssertionMatch;
  rule: string;
};

export type JsonAssertionConfig = {
  jsonPath: string;
  validateValue: boolean;
  expectedValue: string;
  useRegex: boolean;
};

export type CsvDataConfig = {
  fileName: string;
  variableNames: string;
  delimiter?: string;
  fileEncoding?: string;
  ignoreFirstLine?: boolean;
  recycle?: boolean;
  stopThread?: boolean;
  shareMode?: string;
};

export type ScriptParam = {
  key: string;
  label: string;
  value: string | number;
};

export type ScriptVersionRecord = {
  id: number;
  status: 'DRAFT' | 'PUBLISHED';
  remark: string;
  versionNo: number;
  versionLabel: string;
  fileName: string;
  fileSize: number;
  fileHash: string;
  importedAt: string;
  importedBy: string;
  referencedScenarioNames?: string[];
};

export type ScriptStep = {
  id: string;
  type: ScriptStepType;
  name: string;
  config: Record<string, string | number | boolean | HttpParamConfig[] | HttpAdvancedConfig | ThreadGroupSteppingConfig>;
  children: ScriptStep[];
};

export type ScriptAsset = {
  id: number;
  projectId: number;
  scriptId: number;
  latestVersionLabel: string;
  hasDraft: boolean;
  draftVersionId: number | null;
  currentScenarioCount: number;
  outdatedScenarioCount: number;
  name: string;
  sourceFile: string;
  latestVersion: number;
  status: 'DRAFT' | 'PUBLISHED';
  remark: string;
  updatedAt: string;
  steppingThreadGroupSupported: boolean;
  apis: ApiConfig[];
  monitors: MonitorConfig[];
  variables: KeyValue[];
  params: ScriptParam[];
  versions: ScriptVersionRecord[];
  steps: ScriptStep[];
};

export type FlatStepItem = {
  step: ScriptStep;
  level: number;
  parentId: string | null;
};

export type ExecutionStatus = 'QUEUED' | 'RUNNING' | 'STOPPING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'INTERRUPTED';
export type ExecutionUiStatus = 'PENDING' | 'RUNNING' | 'STOPPING' | 'SUCCESS' | 'FAILED' | 'INTERRUPTED';
export type ExecutionStatusFilter = 'ALL' | ExecutionUiStatus;
export type ExecutionMode = 'LOCAL' | 'DISTRIBUTED';
export type ExecutionNodeRole = 'CONTROLLER' | 'WORKER' | 'BOTH';
export type ExecutionNodeStatus = 'UNKNOWN' | 'AVAILABLE' | 'OFFLINE';
export type MonitorTargetType = 'SERVER';
export type MonitorTargetCheckStatus = 'UNKNOWN' | 'SUCCESS' | 'FAILED';
export type MonitorItemType = 'JAVA_JMX_AGENT' | 'MYSQL_EXPORTER' | 'REDIS_EXPORTER' | 'NGINX_EXPORTER' | 'KAFKA_EXPORTER';

export type ExecutionNode = {
  id: number;
  name: string;
  host: string;
  sshPort: number;
  sshUsername: string;
  sshKeyPath: string;
  role: ExecutionNodeRole;
  status: ExecutionNodeStatus;
  remoteWorkDir: string;
  lastCheckedAt: string | null;
  lastMessage: string;
  createdAt: string;
  updatedAt: string;
};

export type MonitorDeployStartResult = {
  title: string;
  success: boolean;
  output: string;
};

export type MonitorDeployCommand = {
  title: string;
  command: string;
};

export type MonitorDeployResult = {
  success: boolean;
  message: string;
  remoteDir: string;
  uploadedFiles: string[];
  startResults: MonitorDeployStartResult[];
  agentCommands: MonitorDeployCommand[];
};

export type MonitorTarget = {
  id: number;
  projectId: number;
  type: MonitorTargetType;
  name: string;
  serviceName: string;
  host: string;
  sshUsername: string | null;
  sshPort: number | null;
  pluginDir: string | null;
  sshPasswordConfigured: boolean;
  port: number;
  metricsPath: string;
  env: string;
  labels: Record<string, string>;
  items: MonitorItem[];
  enabled: boolean;
  lastCheckStatus: MonitorTargetCheckStatus;
  lastCheckMessage: string | null;
  lastCheckedAt: string | null;
  createdAt: string;
  updatedAt: string;
  address: string;
};

export type MonitorItem = {
  id: string;
  type: MonitorItemType;
  name: string;
  port: number;
  metricsPath: string;
  serviceName: string | null;
  processKeyword: string | null;
  instanceName: string | null;
  databaseName: string | null;
  labels: Record<string, string>;
};

export type MetricKind =
  | 'SERVER_CPU'
  | 'SERVER_LOAD'
  | 'SERVER_MEM'
  | 'SERVER_DISK_IO'
  | 'SERVER_NET'
  | 'SERVER_TCP'
  | 'JVM_HEAP_PCT'
  | 'JVM_MEMORY_BYTES'
  | 'JVM_GC'
  | 'JVM_THREADS'
  | 'JVM_CPU';

export type MetricSeriesPoint = {
  timestamp: number;
  value: number;
};

export type MetricSeries = {
  displayName: string;
  labels: Record<string, string>;
  points: MetricSeriesPoint[];
  yAxisIndex: number;
};

export type TargetMetricsQueryResult = {
  kind: MetricKind;
  unit: string;
  series: MetricSeries[];
};

export type ServerSelectable = {
  id: number;
  name: string;
  host: string;
};

export type JvmInstanceSelectable = {
  targetId: number;
  itemId: string;
  serviceName: string;
  host: string;
  processKeyword: string | null;
};

export type TargetMonitoringResult = {
  taskId: number;
  executionId: number;
  startTime: string | null;
  endTime: string | null;
  serverTargets: ServerSelectable[];
  jvmInstances: JvmInstanceSelectable[];
  targets: MonitorTarget[];
};

export type ThreadGroupConfigSummary = {
  samples: number;
  throughput: number;
  avgRt: number;
  errorRate: number;
};

export type ScenarioThreadGroupConfig = {
  id: number;
  stepId: string;
  stepName: string;
  threads: number;
  rampUp: number;
  duration: number;
  sortOrder: number;
  latestSummary?: ThreadGroupConfigSummary | null;
};

export type ScenarioDataFileBinding = {
  stepId: string;
  stepName: string;
  dataFileId: number | null;
};

// ===== 数据文件（P1-5）：字段对照后端 DataFile / DataFileVersion / DataFileVersionDetail record，时间为 LocalDateTime ISO 字符串 =====

export type DataFile = {
  id: number;
  projectId: number;
  name: string;
  remark?: string;
  createdBy: string;
  createdAt: string;
  latestVersion?: DataFileVersion;
};

export type DataFileVersion = {
  id: number;
  dataFileId: number;
  versionNo: number;
  originalFilename: string;
  sizeBytes: number;
  rowCount: number | null;
  headerColumns: string[] | null;
  sha256: string;
  uploadedBy: string;
  uploadedAt: string;
  remark?: string;
};

export type DataFileVersionDetail = {
  version: DataFileVersion;
  previewRows: string[][];
};

export type ExecutionConfig = {
  threads: number;
  rampUp: number;
  duration: number;
  loops: number;
  mode?: ExecutionMode;
  controllerNodeId?: number | null;
  workerNodeIds?: number[];
  monitorTargetIds?: number[];
  jmeterProperties?: Record<string, string>;
  threadGroupConfigId?: number | null;
  stepId?: string | null;
  stepName?: string | null;
};

/** 计划单一状态（spec 2026-09-11 §3.1）：单行道，无回退。 */
export type PlanStatus = 'PLANNING' | 'IN_REVIEW' | 'EXECUTING' | 'REPORTING' | 'PUBLISHED';
export type PlanCommentKind = 'REVIEW' | 'SYSTEM';

export interface PlanCommentAnchor {
  line: number;
  text: string;
  section: string;
}

export interface PlanComment {
  id: number;
  planId: number;
  author: string;
  content: string;
  kind: PlanCommentKind;
  createdAt: string;
  parentId: number | null;
  anchorLine: number | null;
  anchorText: string | null;
  sectionTitle: string | null;
  bodyRevision: number | null;
  resolved: boolean;
  resolvedBy: string | null;
  resolvedAt: string | null;
  canResolve: boolean;
  canDelete: boolean;
  canEdit: boolean;
}

/** 根批注 + 一层回复（spec §2 线程）。 */
export interface PlanCommentThread {
  root: PlanComment;
  replies: PlanComment[];
}

export interface PlanTemplate {
  id: number;
  projectId: number | null;
  name: string;
  description: string | null;
  content: string;
  builtin: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface PlanShareTokenView {
  id: number;
  planId: number;
  token: string;
  expiresAt: string | null;
  revokedAt: string | null;
  createdBy: string;
  createdAt: string;
}

export interface PlanSnapshotView {
  id: number;
  revision: number;
  publishedBy: string;
  publishedAt: string;
}

export interface PlanVersionView {
  id: number;
  versionNo: string;
  changeNote: string;
  createdBy: string;
  author: string;
  planPhase: string;
  planRevision: number;
  /** 来源：MANUAL 手动发版 / PUBLISH 报告发布登记。 */
  kind: string;
  createdAt: string;
  updatedAt: string;
}

export interface PlanVersionListResponse {
  versions: PlanVersionView[];
  bodyDiffersFromLatest: boolean;
}

export interface PlanVersionDetail extends PlanVersionView {
  snapshotBody: string;
}

export interface PrecheckSettings {
  enabled: boolean;
  items: string[];
}

export type PlanPermissions = Record<string, boolean>;

export interface PlanDocumentResponse {
  plan: TaskPlan;
  permissions: PlanPermissions;
  /** 软门禁数据：QUEUED/RUNNING/STOPPING 执行数，执行完成/发布前二次确认用。 */
  activeExecutions: number;
}

export interface PrecheckRunReport {
  ok: boolean;
  failures: string[];
  autoPassed: string[];
}

export type PlanVerdictRow = {
  objectName: string;
  metricRaw: string;
  metricType: string;
  targetRaw: string;
  targetValue: number | null;
  actualValue: string | null;
  status: 'ACHIEVED' | 'MISSED' | 'INDETERMINATE';
  reason: string | null;
  scenarioId: number | null;
  executionId: number | null;
};

export type PlanVerdict = {
  present: boolean;
  available: boolean;
  overall: 'PASSED' | 'FAILED' | 'INDETERMINATE' | 'NONE';
  prefillConclusion: string | null;
  rows: PlanVerdictRow[];
};

export type TaskPlan = {
  id: number;
  projectId: number;
  name: string;
  remark: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  defaultControllerNodeId: number | null;
  defaultWorkerNodeIds: number[];
  defaultMonitorTargetIds: number[];
  scenarioCount: number;
  status: PlanStatus;
  body: string | null;
  revision: number;
  publishedAt: string | null;
  precheckJson: string | null;
  precheckExecutedAt: string | null;
};

export type TaskScenario = {
  id: number;
  planId: number;
  scriptVersionId: number | null;
  name: string;
  purpose: string | null;
  testType: string | null;
  sortOrder: number;
  threads: number;
  rampUp: number;
  duration: number;
  loops: number;
  jmeterProperties: Record<string, string>;
  controllerNodeId: number | null;
  workerNodeIds: number[] | null;
  monitorTargetIds: number[] | null;
  threadGroupConfigs: ScenarioThreadGroupConfig[];
  dataFileBindings: ScenarioDataFileBinding[];
  latestExecutionStatus: ExecutionStatus | null;
  latestExecutionAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ScenarioExecution = {
  id: number;
  scenarioId: number;
  planId: number;
  projectId: number;
  scriptVersionId: number;
  scenarioName: string;
  status: ExecutionStatus;
  config: ExecutionConfig;
  createdAt: string;
  startedAt: string | null;
  endedAt: string | null;
  durationMs: number | null;
  resultFilePath: string | null;
  logFilePath: string | null;
  errorMessage: string | null;
  executionName: string | null;
};

export type ExecutionDetail = ScenarioExecution & {
  executionLogs: string;
  summary: TaskSummary;
  monitoring: TaskMetricSeries;
  targetMonitoring: TargetMonitoringResult | null;
  aggregateRows: TaskAggregateRow[];
  samples: TaskSample[];
  sampleTotal: number;
};

export type MetricLabelPoint = {
  label: string;
  samples: number;
  errorSamples: number;
  throughput: number;
  avgRtMs: number;
  p95RtMs: number;
};

export type MetricTick = {
  bucketTimeMs: number;
  labels: MetricLabelPoint[];
  overall: MetricLabelPoint;
};

export type TaskMetricSeries = {
  ticks: MetricTick[];
};

export type TaskAggregateRow = {
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
};

export type TaskSample = {
  id: number;
  time: string;
  statusCode: string | number;
  success: boolean;
  label: string;
  elapsed: number;
  message: string;
  threadName: string;
  requestLine?: string;
  requestHeaders?: string;
  requestBody?: string;
  responseHeaders?: string;
  responseBody?: string;
  failureMessage?: string;
};

export type TaskSamplePage = {
  page: number;
  pageSize: number;
  total: number;
  samples: TaskSample[];
};

export type TaskSummary = {
  samples: number;
  throughput: number;
  avgRt: number;
  p95: number;
  errorRate: number;
  accuracy?: 'final' | 'final_partial' | 'live' | null;
};

export type McpToolStatus = 'ENABLED' | 'DISABLED';

export type McpToolSummary = {
  name: string;
  title: string;
  stage: string;
  requiresWriteScope: boolean;
  status: McpToolStatus;
  description: string;
  usageExample: string;
  inputSchema: Record<string, unknown>;
};

export type McpDirectory = {
  server: { name: string; endpoint: string; toolCount: number };
  stages: string[];
  tools: McpToolSummary[];
};

/** 测试方法章节（spec 2026-09-15）：截图证据文件流。 */
export interface EvidenceImage { id: number; executionId: number | null; caption: string; sortOrder: number; contentType: string; sizeBytes: number; }
export interface MethodExecutionRow { executionId: number; executionName: string; threads: number; rampUpSec: number; durationSec: number; status: string; samples: number | null; successRate: number | null; avgRtMs: number | null; p95Ms: number | null; tps: number | null; startedAtText: string; hidden: boolean; }
export interface MethodScenarioData { scenarioId: number; name: string; testType: string; sortOrder: number; scriptVersionId: number | null; scriptName: string | null; executions: MethodExecutionRow[]; hiddenCount: number; images: EvidenceImage[]; }
export interface MethodSectionData { planId: number; scenarios: MethodScenarioData[]; }

// ===== 环境检查（spec 2026-09-15 P1-1）：字段对照后端 EnvCheckController / EnvCheckRunService / EnvCheckCredentialService / EnvCheckFixService =====

export interface EnvCheckItemMeta {
  key: string;
  label: string;
  description: string;
  category: 'DOC' | 'OS' | 'JVM' | 'MIDDLEWARE';
  kind: 'LOCAL' | 'REMOTE';
  appliesTo: string[];
  sortOrder: number;
  fixable: boolean;
  risk: 'LOW' | 'MEDIUM' | 'HIGH' | null;
}

/** 检查项目录响应：fixEnabled 为平台修复总闸，关闭时前端不渲染修复操作。 */
export interface EnvCheckItemsResponse {
  items: EnvCheckItemMeta[];
  fixEnabled: boolean;
}

/** 检查总览目标行：credential 为凭据三态（项目池 / 计划覆盖 / 缺失），对照后端 TargetsPreview.TargetView。 */
export type EnvCheckCredentialState = 'POOL' | 'PLAN_OVERRIDE' | 'MISSING';
export interface EnvCheckTargetView {
  host: string;
  module: string;
  credential: EnvCheckCredentialState;
  applicableRemoteItems: number;
}
export interface EnvCheckTargetsPreview {
  targets: EnvCheckTargetView[];
  total: number;
  ready: number;
  missing: string[];
}

export interface EnvCheckCredential {
  id: number;
  host: string;
  sshPort: number;
  username: string;
  authType: 'PASSWORD' | 'KEY';
  remark: string | null;
  planId: number | null;
}

export interface EnvCheckCredentialInput {
  host: string;
  sshPort?: number;
  username: string;
  password?: string;
  keyMaterial?: string;
  remark?: string;
  planId?: number | null;
}

export interface EnvCheckRunRow {
  host: string | null;
  itemKey: string;
  state: 'OK' | 'WARNING' | 'FIXED' | 'NA';
  detail: string | null;
  suggestion: string | null;
  method: string | null;
  risk: 'LOW' | 'MEDIUM' | 'HIGH' | null;
  fixable: boolean;
}

/** 触发与历史列表返回的摘要（RunSummary），不含结果矩阵。 */
export interface EnvCheckRunSummary {
  id: number;
  planId: number;
  triggeredBy: string;
  startedAt: string;
  finishedAt: string | null;
  passed: number;
  warned: number;
}

/** run.detailJson 反序列化后的完整矩阵：targets + results。 */
export interface EnvCheckRunDetailJson {
  targets: Array<{ host: string; module: string }>;
  results: EnvCheckRunRow[];
}

/** 详情接口返回的 RunDetail：run 记录（含 detailJson 原文）+ 结果矩阵行。 */
export interface EnvCheckRunDetail {
  run: EnvCheckRunSummary & { detailJson: string | null };
  rows: EnvCheckRunRow[];
}

export interface EnvCheckFixRecord {
  id: number;
  runId: number;
  host: string;
  itemKey: string;
  riskLevel: string;
  backupRef: string;
  diffText: string | null;
  summary: string | null;
  appliedBy: string;
  appliedAt: string;
  rolledBackAt: string | null;
}

