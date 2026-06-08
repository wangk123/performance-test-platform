export type User = {
  username: string;
  displayName: string;
  roles: string[];
};

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';
export type ProjectRole = 'OWNER' | 'MEMBER';
export type ParseStatus = 'PARSED' | 'PARSE_FAILED';
export type StatusFilter = 'ALL' | ProjectStatus;
export type ProjectTab = 'overview' | 'scripts' | 'tasks' | 'monitoring' | 'reports' | 'members';
export type MainNav = 'home' | 'projects' | 'settings';
export type ConfigTab = 'users' | 'roles' | 'permissions';
export type ScriptStepType =
  | 'THREAD_GROUP'
  | 'HTTP_REQUEST'
  | 'ASSERTION'
  | 'CSV_DATA'
  | 'USER_PARAMS'
  | 'HEADER_CONFIG';
export type StepRelation = 'root' | 'child';
export type StepDropMode = 'before' | 'after' | 'child';

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
  config: Record<string, string | number | boolean | HttpParamConfig[] | HttpAdvancedConfig>;
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
  threadGroups: ThreadGroup[];
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
export type TaskResultFilter = 'ALL' | 'SUCCESS' | 'ERROR';

export type TaskMetricPoint = {
  time: string;
  tps: number;
  targetTps: number;
  avgRt: number;
  p90: number;
  p95: number;
};

export type TaskSample = {
  id: number;
  statusCode: string | number;
  success: boolean;
  label: string;
  elapsed: number;
  message: string;
  threadName: string;
  request: string;
  response: string;
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
  environment: string;
  threads: number;
  rampUp: number;
  duration: number;
  loops: number;
  priority: string;
  remark: string;
  createdAt: string;
  lastRunAt: string | null;
  summary: TaskSummary;
  metrics: TaskMetricPoint[];
  samples: TaskSample[];
};
