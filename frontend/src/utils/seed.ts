import type { Project, ProjectMember, ScriptAsset, ScriptVersionRecord } from '../types';
import { mockHash } from './format';
import { defaultParams, mockApiPath } from './jmeter';
import { createStepsFromParsed } from './script-steps';

export function createVersionRecord(
  versionNo: number,
  fileName: string,
  file: { size: number },
  importedAt: string,
  remark: string,
  importedBy: string,
): ScriptVersionRecord {
  return {
    versionNo,
    fileName,
    fileSize: file.size,
    fileHash: mockHash(`${fileName}-${file.size}-${importedAt}-${versionNo}`),
    importedAt,
    importedBy,
    remark,
  };
}

function createMockAsset(
  id: number,
  projectId: number,
  name: string,
  latestVersion: number,
  sourceFile: string,
): ScriptAsset {
  const now = `2026-05-${22 + id}T10:32:00.000Z`;
  const threadGroups = [
    { name: '主业务线程组', threads: name.includes('查询') ? 60 : 120, rampUp: 60, loops: 1, duration: 600 },
    ...(name.includes('查询') ? [] : [{ name: '登录前置线程组', threads: 30, rampUp: 20, loops: 1, duration: 300 }]),
  ];
  const apis = [
    { method: 'POST', path: mockApiPath(name, 0), domain: '${host}' },
    { method: 'GET', path: mockApiPath(name, 1), domain: '${host}' },
    { method: 'POST', path: mockApiPath(name, 2), domain: '${host}' },
  ];
  const variables = [
    { key: 'host', value: '127.0.0.1' },
    { key: 'protocol', value: 'https' },
    { key: 'env', value: 'SIT' },
  ];
  return {
    id,
    projectId,
    name,
    sourceFile,
    latestVersion,
    parseStatus: 'PARSED',
    remark: 'Mock 解析结果，可按需求继续调整字段。',
    updatedAt: now,
    steppingThreadGroupSupported: true,
    apis,
    monitors: [
      { target: '应用服务', metrics: ['TPS', 'RT', '错误率'] },
      { target: 'JVM', metrics: ['Heap', 'GC', 'Thread'] },
      {
        target: name.includes('支付') ? 'Redis' : '数据库',
        metrics: name.includes('支付') ? ['QPS', '命中率'] : ['连接数', '慢 SQL'],
      },
    ],
    variables,
    params: defaultParams(),
    steps: createStepsFromParsed(name, threadGroups, apis, variables),
    versions: Array.from({ length: latestVersion }, (_, index) =>
      createVersionRecord(
        latestVersion - index,
        sourceFile,
        { size: 142000 + id * 4200 },
        `2026-05-${22 + id - index}T10:32:00.000Z`,
        index === 0 ? '当前解析版本' : '历史版本',
        'admin',
      ),
    ),
  };
}

export function createSeedData() {
  const projectsSeed: Project[] = [
    {
      id: 1,
      code: 'loan-core',
      name: '信贷核心压测',
      description: '覆盖授信申请、额度查询、合同签署链路，当前重点验证高峰并发下游依赖稳定性。',
      ownerUsername: 'admin',
      status: 'ACTIVE',
      createdAt: '2026-05-02T09:24:00.000Z',
      updatedAt: '2026-05-28T16:10:00.000Z',
    },
    {
      id: 2,
      code: 'payment-gateway',
      name: '支付网关容量验证',
      description: '面向支付路由和回调通知的容量摸底，后续接入分布式执行节点。',
      ownerUsername: 'tester',
      status: 'ACTIVE',
      createdAt: '2026-05-08T11:20:00.000Z',
      updatedAt: '2026-05-30T10:18:00.000Z',
    },
    {
      id: 3,
      code: 'crm-archive',
      name: 'CRM 历史压测项目',
      description: '已完成验收，仅保留历史脚本、任务和报告查看入口。',
      ownerUsername: 'qa-lead',
      status: 'ARCHIVED',
      createdAt: '2026-04-12T08:00:00.000Z',
      updatedAt: '2026-05-16T18:42:00.000Z',
    },
  ];

  const membersSeed: ProjectMember[] = [
    { id: 1, projectId: 1, username: 'admin', displayName: '平台管理员', role: 'OWNER' },
    { id: 2, projectId: 1, username: 'tester', displayName: '性能测试工程师', role: 'MEMBER' },
    { id: 3, projectId: 2, username: 'tester', displayName: '性能测试工程师', role: 'OWNER' },
    { id: 4, projectId: 2, username: 'devops', displayName: '运维同学', role: 'MEMBER' },
    { id: 5, projectId: 3, username: 'qa-lead', displayName: '测试负责人', role: 'OWNER' },
  ];

  const scriptSeed: ScriptAsset[] = [
    createMockAsset(1, 1, '授信申请主链路', 3, 'credit-apply-main.jmx'),
    createMockAsset(2, 1, '额度查询基准脚本', 1, 'quota-query.jmx'),
    createMockAsset(3, 2, '支付回调通知', 1, 'payment-callback.jmx'),
  ];

  return {
    projects: projectsSeed,
    members: membersSeed,
    scriptAssets: scriptSeed,
  };
}

export function normalizeScriptAsset(script: ScriptAsset): ScriptAsset {
  return {
    ...script,
    steppingThreadGroupSupported: script.steppingThreadGroupSupported ?? false,
    steps: script.steps?.length
      ? script.steps
      : createStepsFromParsed(
          script.name,
          script.steps
            .filter((step) => step.type === 'THREAD_GROUP')
            .map((step) => ({
              name: step.name,
              threads: Number(step.config.threads ?? 1),
              rampUp: Number(step.config.rampUp ?? 0),
              loops: Number(step.config.loops ?? 1),
              duration: Number(step.config.duration ?? 0),
            })),
          script.apis,
          script.variables,
        ),
  };
}
