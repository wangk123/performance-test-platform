export type User = {
  username: string;
  displayName: string;
  roles: string[];
};

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';
export type ProjectRole = 'OWNER' | 'MEMBER';
export type ParseStatus = 'PARSED' | 'PARSE_FAILED';
export type StatusFilter = 'ALL' | ProjectStatus;
export type ProjectTab = 'overview' | 'scripts' | 'task-plans' | 'monitoring' | 'reports' | 'data' | 'functions' | 'members';
export type MainNav = 'home' | 'projects' | 'executionNodes' | 'settings';
export type ConfigTab = 'users' | 'roles' | 'permissions' | 'nodes';
export type ScriptStepType =
  | 'THREAD_GROUP'
  | 'HTTP_REQUEST'
  | 'ASSERTION'
  | 'JSON_ASSERTION'
  | 'CSV_DATA'
  | 'USER_PARAMS'
  | 'HEADER_CONFIG';
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

export type ScriptParam = {
  key: string;
  label: string;
  value: string | number;
};

export type ScriptVersionRecord = {
  versionNo: number;
  fileName: string;
  fileSize: number;
  fileHash: string;
  importedAt: string;
  importedBy: string;
  remark: string;
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
  name: string;
  sourceFile: string;
  latestVersion: number;
  parseStatus: ParseStatus;
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
};

export type TaskScenario = {
  id: number;
  planId: number;
  scriptVersionId: number;
  name: string;
  sortOrder: number;
  threads: number;
  rampUp: number;
  duration: number;
  loops: number;
  jmeterProperties: Record<string, string>;
  controllerNodeId: number | null;
  workerNodeIds: number[] | null;
  monitorTargetIds: number[] | null;
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

