import type { ScriptAsset, TaskSample, TaskStatus, TestTask } from '../types';

export function baseTaskSamples(): TaskSample[] {
  return [
    {
      id: 1,
      statusCode: 500,
      success: false,
      label: 'POST /api/credit/apply',
      elapsed: 842,
      message: '断言失败',
      threadName: 'Thread Group 1-18',
      request:
        'POST https://sit.loan.local/api/credit/apply\nContent-Type: application/json\nAuthorization: Bearer ******\n\n{"customerId":"C102938","amount":50000,"term":12}',
      response:
        'HTTP/1.1 500 Internal Server Error\nContent-Type: application/json\n\n{"code":"CREDIT_RULE_TIMEOUT","message":"授信规则引擎响应超时","traceId":"e9b2-42af"}',
    },
    {
      id: 2,
      statusCode: 504,
      success: false,
      label: 'POST /api/contract/sign',
      elapsed: 1204,
      message: '超时',
      threadName: 'Thread Group 1-21',
      request:
        'POST https://sit.loan.local/api/contract/sign\nContent-Type: application/json\n\n{"contractNo":"CT202606050091","channel":"APP"}',
      response: 'HTTP/1.1 504 Gateway Timeout\n\nupstream request timeout after 1000ms',
    },
    {
      id: 3,
      statusCode: 200,
      success: true,
      label: 'GET /api/quota/detail',
      elapsed: 168,
      message: '成功',
      threadName: 'Thread Group 1-07',
      request: 'GET https://sit.loan.local/api/quota/detail?customerId=C102938\nAccept: application/json',
      response:
        'HTTP/1.1 200 OK\nContent-Type: application/json\n\n{"availableQuota":78000,"usedQuota":12000,"riskLevel":"LOW"}',
    },
    {
      id: 4,
      statusCode: 502,
      success: false,
      label: 'POST /api/credit/apply',
      elapsed: 938,
      message: '网关错误',
      threadName: 'Thread Group 1-32',
      request:
        'POST https://sit.loan.local/api/credit/apply\nContent-Type: application/json\n\n{"customerId":"C102940","amount":30000,"term":6}',
      response: 'HTTP/1.1 502 Bad Gateway\n\ncredit-service connection reset',
    },
    {
      id: 5,
      statusCode: 200,
      success: true,
      label: 'POST /api/login',
      elapsed: 95,
      message: '成功',
      threadName: 'Login Thread 1-03',
      request:
        'POST https://sit.loan.local/api/login\nContent-Type: application/json\n\n{"username":"perf_user_003","password":"******"}',
      response: 'HTTP/1.1 200 OK\n\n{"token":"******","expiresIn":7200}',
    },
    {
      id: 6,
      statusCode: 200,
      success: true,
      label: 'GET /api/product/list',
      elapsed: 142,
      message: '成功',
      threadName: 'Thread Group 1-10',
      request: 'GET https://sit.loan.local/api/product/list\nAccept: application/json',
      response: 'HTTP/1.1 200 OK\n\n{"items":[{"code":"LOAN_12M","name":"授信 12 期"}]}',
    },
    {
      id: 7,
      statusCode: 500,
      success: false,
      label: 'GET /api/quota/detail',
      elapsed: 692,
      message: '异常',
      threadName: 'Thread Group 1-15',
      request: 'GET https://sit.loan.local/api/quota/detail?customerId=C102941',
      response: 'HTTP/1.1 500 Internal Server Error\n\n{"code":"DB_POOL_EXHAUSTED","message":"数据库连接池耗尽"}',
    },
    {
      id: 8,
      statusCode: 200,
      success: true,
      label: 'POST /api/contract/preview',
      elapsed: 231,
      message: '成功',
      threadName: 'Thread Group 1-09',
      request: 'POST https://sit.loan.local/api/contract/preview\n\n{"contractNo":"CT202606050092"}',
      response: 'HTTP/1.1 200 OK\n\n{"previewUrl":"/files/contracts/preview/CT202606050092"}',
    },
  ];
}

export function createMockTask(script: ScriptAsset, index: number): TestTask {
  const statuses: TaskStatus[] = ['RUNNING', 'SUCCESS', 'PENDING', 'FAILED'];
  const status = statuses[index % statuses.length];
  return {
    id: script.id * 10 + index + 1,
    projectId: script.projectId,
    scriptId: script.id,
    name: `${script.name} / ${index === 0 ? '容量基线' : '回归验证'}`,
    status,
    environment: index % 2 === 0 ? 'SIT / 127.0.0.1' : 'UAT / 10.12.4.18',
    threads: script.threadGroups[0]?.threads ?? 60,
    rampUp: script.threadGroups[0]?.rampUp ?? 60,
    duration: script.threadGroups[0]?.duration ?? 600,
    loops: script.threadGroups[0]?.loops ?? 1,
    priority: '普通',
    remark: 'Mock 任务配置，后续接入后端任务接口。',
    createdAt: `2026-06-0${index + 1}T10:00:00.000Z`,
    lastRunAt: status === 'PENDING' ? null : `2026-06-0${index + 3}T16:30:00.000Z`,
    summary: {
      samples: 18420 - index * 1240,
      throughput: 486 - index * 42,
      avgRt: 182 + index * 28,
      p95: 438 + index * 36,
      errorRate: status === 'FAILED' ? 3.8 : 0.13,
    },
    metrics: [
      { time: '16:30', tps: 180, targetTps: 220, avgRt: 160, p90: 260, p95: 340 },
      { time: '16:32', tps: 260, targetTps: 300, avgRt: 172, p90: 288, p95: 370 },
      { time: '16:34', tps: 410, targetTps: 420, avgRt: 188, p90: 318, p95: 430 },
      { time: '16:36', tps: 512, targetTps: 480, avgRt: 210, p90: 360, p95: 520 },
      { time: '16:38', tps: 486, targetTps: 480, avgRt: 196, p90: 332, p95: 438 },
      { time: '16:40', tps: 432, targetTps: 480, avgRt: 182, p90: 310, p95: 420 },
    ],
    samples: baseTaskSamples(),
  };
}
