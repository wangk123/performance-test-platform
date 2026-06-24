export type User = {
  username: string;
  displayName: string;
  roles: string[];
};

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';
export type ProjectRole = 'OWNER' | 'MEMBER';
export type ParseStatus = 'PARSED' | 'PARSE_FAILED';
export type StatusFilter = 'ALL' | ProjectStatus;
export type ProjectTab = 'overview' | 'scripts' | 'tasks' | 'monitoring' | 'reports' | 'data' | 'functions' | 'members';
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

export type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
export type TaskStatusFilter = 'ALL' | TaskStatus;
export type ExecutionMode = 'LOCAL' | 'DISTRIBUTED';
export type ExecutionNodeRole = 'CONTROLLER' | 'WORKER' | 'BOTH';
export type ExecutionNodeStatus = 'UNKNOWN' | 'AVAILABLE' | 'OFFLINE';

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

export type TaskMetricPoint = {
  time: string;
  tps: number;
  targetTps: number;
  avgRt: number;
  p90: number;
  p95: number;
};

export type TaskMonitoringPoint = {
  time: string;
  interfaceName: string;
  tps: number;
  avgRt: number;
  p90: number;
  p95: number;
};

export type TaskMonitoringResult = {
  interfaces: string[];
  points: TaskMonitoringPoint[];
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
  request: string;
  response: string;
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
};

export type TestTask = {
  id: number;
  projectId: number;
  scriptId: number;
  name: string;
  status: TaskStatus;
  executionMode: ExecutionMode;
  controllerNodeId: number | null;
  workerNodeIds: number[];
  grafanaUrl: string | null;
  remark: string;
  createdAt: string;
  lastRunAt: string | null;
  endedAt: string | null;
  errorMessage: string | null;
  executionLogs: string;
  summary: TaskSummary;
  metrics: TaskMetricPoint[];
  monitoring: TaskMonitoringResult;
  aggregateRows: TaskAggregateRow[];
  samples: TaskSample[];
  sampleTotal: number;
};
