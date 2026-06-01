import type { ParseStatus, ScriptParam } from '../types';
import { clamp, countMatches } from './format';

export function defaultParams(): ScriptParam[] {
  return [
    { key: 'threads', label: '线程数', value: 100 },
    { key: 'loops', label: '循环次数', value: 1 },
    { key: 'duration', label: '持续时间', value: '600s' },
    { key: 'rampUp', label: 'Ramp-Up', value: '60s' },
    { key: 'environment', label: '目标环境', value: 'SIT' },
  ];
}

export function mockApiPath(scriptName: string, index: number) {
  const prefix = scriptName.includes('支付')
    ? '/api/payment'
    : scriptName.includes('查询')
      ? '/api/quota'
      : '/api/credit';
  const paths = ['/login', '/prepare', '/submit', '/query', '/confirm', '/callback', '/status', '/report'];
  return `${prefix}${paths[index % paths.length]}`;
}

export async function parseJmeterFile(file: File, scriptName: string) {
  const text = await file.text().catch(() => '');
  const threadCount = clamp(
    countMatches(text, /<ThreadGroup\b/g) || (scriptName.includes('查询') ? 1 : 2),
    1,
    4,
  );
  const apiCount = clamp(
    countMatches(text, /HTTPSamplerProxy/g) || (scriptName.includes('支付') ? 5 : 8),
    2,
    12,
  );
  const hasMonitor = /BackendListener|ResultCollector|PerfMon|kg\.apc/i.test(text);

  return {
    parseStatus: 'PARSED' as ParseStatus,
    threadGroups: Array.from({ length: threadCount }, (_, index) => ({
      name: index === 0 ? '主业务线程组' : `辅助链路线程组 ${index}`,
      threads: index === 0 ? 100 : 40 + index * 20,
      rampUp: index === 0 ? 60 : 30,
      loops: 1,
      duration: index === 0 ? 600 : 300,
    })),
    apis: Array.from({ length: apiCount }, (_, index) => ({
      method: index % 3 === 0 ? 'POST' : 'GET',
      path: mockApiPath(scriptName, index),
      domain: '${host}',
    })),
    monitors: [
      { target: '应用服务', metrics: ['TPS', 'RT', '错误率'] },
      { target: 'JVM', metrics: ['Heap', 'GC', 'Thread'] },
      ...(hasMonitor
        ? [{ target: '主机资源', metrics: ['CPU', 'Memory', 'Disk IO'] }]
        : [{ target: '数据库', metrics: ['连接数', '慢 SQL'] }]),
    ],
    variables: [
      { key: 'host', value: '127.0.0.1' },
      { key: 'protocol', value: 'https' },
      { key: 'env', value: 'SIT' },
    ],
    params: defaultParams(),
  };
}
