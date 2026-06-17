import type { ScriptAsset, ScriptStep } from '../types';
import { useThreadGroups } from '../composables/useThreadGroups';

export type ScriptExecutableStatus = {
  executable: boolean;
  label: string;
  reason: string;
  tone: 'ready' | 'blocked';
};

export function scriptExecutableStatus(script: ScriptAsset): ScriptExecutableStatus {
  if (script.parseStatus !== 'PARSED') {
    return blocked('解析失败', '脚本解析失败');
  }
  const { threadGroups } = useThreadGroups(() => script.steps);
  if (!threadGroups.value.length) {
    return blocked('无线程组', '脚本缺少线程组');
  }
  if (!flattenSteps(script.steps).some((step) => step.type === 'HTTP_REQUEST')) {
    return blocked('无请求', '脚本缺少 HTTP 请求');
  }
  if (threadGroups.value.some((group) => group.mode === 'stepping') && !script.steppingThreadGroupSupported) {
    return blocked('缺少插件', '当前 JMeter 缺少阶梯加压插件');
  }
  return { executable: true, label: '可执行', reason: '脚本可直接提交任务执行', tone: 'ready' };
}

export function flattenSteps(steps: ScriptStep[]): ScriptStep[] {
  return steps.flatMap((step) => [step, ...flattenSteps(step.children)]);
}

function blocked(label: string, reason: string): ScriptExecutableStatus {
  return { executable: false, label, reason, tone: 'blocked' };
}
