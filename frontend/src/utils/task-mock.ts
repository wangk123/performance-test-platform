import type { ScriptAsset, TaskStatus, TestTask } from '../types';

export function createMockTask(script: ScriptAsset, index: number): TestTask {
  const statuses: TaskStatus[] = ['RUNNING', 'SUCCESS', 'PENDING', 'FAILED'];
  const status = statuses[index % statuses.length];
  return {
    id: script.id * 10 + index + 1,
    projectId: script.projectId,
    scriptId: script.id,
    name: `${script.name} / ${index === 0 ? '容量基线' : '回归验证'}`,
    status,
    executionMode: 'LOCAL',
    controllerNodeId: null,
    workerNodeIds: [],
    grafanaUrl: null,
    environment: index % 2 === 0 ? 'SIT / 127.0.0.1' : 'UAT / 10.12.4.18',
    priority: '普通',
    remark: 'Mock 任务配置，后续接入后端任务接口。',
    createdAt: `2026-06-0${index + 1}T10:00:00.000Z`,
    lastRunAt: status === 'PENDING' ? null : `2026-06-0${index + 3}T16:30:00.000Z`,
    summary: {
      samples: 0,
      throughput: 0,
      avgRt: 0,
      p95: 0,
      errorRate: 0,
    },
    metrics: [],
    monitoring: { interfaces: [], points: [] },
    aggregateRows: [],
    samples: [],
  };
}
