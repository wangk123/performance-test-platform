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
  config: Record<string, string | number>;
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
