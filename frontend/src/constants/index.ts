import type { ConfigTab, ProjectTab, ScriptStepType } from '../types';

export const STORAGE_KEY = 'perftest.frontend.prototype.v3';
export const CURRENT_USER_KEY = 'perftest.currentUser';

export const projectStatusOptions = [
  { label: '活跃', value: 'ACTIVE' },
  { label: '归档', value: 'ARCHIVED' },
  { label: '全部', value: 'ALL' },
];

export const projectTabOptions: Array<{ label: string; value: ProjectTab }> = [
  { label: '项目概览', value: 'overview' },
  { label: '脚本管理', value: 'scripts' },
  { label: '任务计划', value: 'task-plans' },
  { label: '监控配置', value: 'monitoring' },
  { label: '报告管理', value: 'reports' },
  { label: '造数工厂', value: 'data' },
  { label: '函数库', value: 'functions' },
  { label: '成员权限', value: 'members' },
];

export const configTabOptions: Array<{ label: string; value: ConfigTab }> = [
  { label: '用户管理', value: 'users' },
  { label: '角色管理', value: 'roles' },
  { label: '权限配置', value: 'permissions' },
  { label: '执行节点', value: 'nodes' },
];

export const MAX_SCRIPT_STEP_LEVEL = 2;

export const stepTypeOptions: Array<{ label: string; value: ScriptStepType }> = [
  { label: '线程组配置', value: 'THREAD_GROUP' },
  { label: 'HTTP 请求', value: 'HTTP_REQUEST' },
  { label: '响应断言', value: 'ASSERTION' },
  { label: 'JSON 断言', value: 'JSON_ASSERTION' },
  { label: 'CSV 数据文件', value: 'CSV_DATA' },
  { label: '用户参数', value: 'USER_PARAMS' },
  { label: 'Header 头配置', value: 'HEADER_CONFIG' },
];

export type StepTypeMeta = {
  label: string;
  shortLabel: string;
  hint: string;
  tone: string;
};

export const stepTypeMeta: Record<ScriptStepType, StepTypeMeta> = {
  THREAD_GROUP: {
    label: '线程组配置',
    shortLabel: 'Thread',
    hint: '最外层执行配置',
    tone: 'thread',
  },
  HTTP_REQUEST: {
    label: 'HTTP 请求',
    shortLabel: 'HTTP',
    hint: '接口方法、域名与路径',
    tone: 'http',
  },
  ASSERTION: {
    label: '响应断言',
    shortLabel: 'Assert',
    hint: '响应码、文本或 Header 校验',
    tone: 'assert',
  },
  JSON_ASSERTION: {
    label: 'JSON 断言',
    shortLabel: 'JSON',
    hint: 'JSONPath 路径与期望值校验',
    tone: 'json-assert',
  },
  CSV_DATA: {
    label: 'CSV 数据文件',
    shortLabel: 'CSV',
    hint: '外部测试数据读取',
    tone: 'csv',
  },
  USER_PARAMS: {
    label: '用户参数',
    shortLabel: 'Vars',
    hint: '线程内变量与默认值',
    tone: 'vars',
  },
  HEADER_CONFIG: {
    label: 'Header 头配置',
    shortLabel: 'Header',
    hint: '公共请求头配置',
    tone: 'header',
  },
};

export const systemUsers = [
  { username: 'admin', displayName: '平台管理员', role: '系统管理员', status: '启用', lastLogin: '06/01 09:12' },
  { username: 'tester', displayName: '性能测试工程师', role: '测试负责人', status: '启用', lastLogin: '05/31 18:42' },
  { username: 'devops', displayName: '运维同学', role: '监控维护', status: '启用', lastLogin: '05/30 21:06' },
  { username: 'auditor', displayName: '审计查看人', role: '只读审计', status: '停用', lastLogin: '05/18 10:24' },
];

export const systemRoles = [
  { name: '系统管理员', description: '管理全部平台配置和所有项目资产。', permissions: ['用户管理', '角色授权', '项目维护', '报告查看'] },
  { name: '测试负责人', description: '创建项目，维护项目成员和压测资产。', permissions: ['项目维护', '脚本导入', '任务执行', '报告查看'] },
  { name: '监控维护', description: '维护监控目标、指标模板和执行绑定。', permissions: ['监控配置', '报告查看'] },
  { name: '只读审计', description: '查看项目、执行记录和报告，不允许修改。', permissions: ['只读查看'] },
];

export const systemPermissions = [
  { module: '项目管理', name: '创建和编辑项目', code: 'project:write' },
  { module: '项目管理', name: '归档和恢复项目', code: 'project:archive' },
  { module: '脚本管理', name: '导入和解析 JMX', code: 'script:import' },
  { module: '任务计划', name: '创建执行任务', code: 'task:create' },
  { module: '监控配置', name: '维护监控目标', code: 'monitor:write' },
  { module: '报告管理', name: '查看和导出报告', code: 'report:read' },
  { module: '系统配置', name: '维护用户角色权限', code: 'system:admin' },
];
