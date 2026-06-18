import type {
  ApiConfig,
  FlatStepItem,
  HttpAdvancedConfig,
  HttpParamConfig,
  KeyValue,
  ScriptStep,
  ScriptStepType,
  StepDropMode,
  ThreadGroup,
  ThreadGroupSteppingConfig,
} from '../types';
import { MAX_SCRIPT_STEP_LEVEL } from '../constants';
import { stepTypeLabel } from './format';
import { createStepId } from './format';

type StepConfigValue = string | number | boolean | HttpParamConfig[] | HttpAdvancedConfig | ThreadGroupSteppingConfig;
type StepOverrides = {
  children?: ScriptStep[];
  [key: string]: StepConfigValue | ScriptStep[] | undefined;
};

export function getStepLevel(steps: ScriptStep[], stepId: string, level = 0): number | null {
  for (const step of steps) {
    if (step.id === stepId) {
      return level;
    }
    const nested = getStepLevel(step.children, stepId, level + 1);
    if (nested !== null) {
      return nested;
    }
  }
  return null;
}

export function getSubtreeDepth(step: ScriptStep): number {
  if (!step.children.length) {
    return 1;
  }
  return 1 + Math.max(...step.children.map(getSubtreeDepth));
}

export function canAddChildStep(steps: ScriptStep[], parentId: string, childStep?: ScriptStep | null): boolean {
  const parentLevel = getStepLevel(steps, parentId);
  if (parentLevel === null) {
    return false;
  }
  const height = childStep ? getSubtreeDepth(childStep) : 1;
  return parentLevel + height <= MAX_SCRIPT_STEP_LEVEL;
}

export function flattenScriptSteps(
  steps: ScriptStep[],
  collapsedIds: string[],
  level = 0,
  parentId: string | null = null,
): FlatStepItem[] {
  return steps.flatMap((step) => [
    { step, level, parentId },
    ...(collapsedIds.includes(step.id) ? [] : flattenScriptSteps(step.children, collapsedIds, level + 1, step.id)),
  ]);
}

export function findStepById(steps: ScriptStep[], stepId: string): ScriptStep | null {
  for (const step of steps) {
    if (step.id === stepId) {
      return step;
    }
    const child = findStepById(step.children, stepId);
    if (child) {
      return child;
    }
  }
  return null;
}

export function insertStepRelative(
  steps: ScriptStep[],
  targetId: string,
  newStep: ScriptStep,
  mode: 'before' | 'after',
): boolean {
  const index = steps.findIndex((step) => step.id === targetId);
  if (index >= 0) {
    steps.splice(mode === 'before' ? index : index + 1, 0, newStep);
    return true;
  }
  return steps.some((step) => insertStepRelative(step.children, targetId, newStep, mode));
}

export function appendChildStep(steps: ScriptStep[], targetId: string, newStep: ScriptStep): boolean {
  const target = findStepById(steps, targetId);
  if (!target) {
    return false;
  }
  target.children.push(newStep);
  return true;
}

export function takeStepById(steps: ScriptStep[], targetId: string): ScriptStep | null {
  const index = steps.findIndex((step) => step.id === targetId);
  if (index >= 0) {
    return steps.splice(index, 1)[0];
  }
  for (const step of steps) {
    const removed = takeStepById(step.children, targetId);
    if (removed) {
      return removed;
    }
  }
  return null;
}

export function removeStepById(steps: ScriptStep[], targetId: string): boolean {
  const index = steps.findIndex((step) => step.id === targetId);
  if (index >= 0) {
    steps.splice(index, 1);
    return true;
  }
  return steps.some((step) => removeStepById(step.children, targetId));
}

export function containsStep(steps: ScriptStep[], targetId: string): boolean {
  return steps.some((step) => step.id === targetId || containsStep(step.children, targetId));
}

export function findParentStepId(steps: ScriptStep[], targetId: string, parentId: string | null = null): string | null {
  for (const step of steps) {
    if (step.id === targetId) {
      return parentId;
    }
    const found = findParentStepId(step.children, targetId, step.id);
    if (found !== null) {
      return found;
    }
  }
  return null;
}

export function canMoveStep(steps: ScriptStep[], sourceId: string, targetId: string, mode: StepDropMode): boolean {
  if (sourceId === targetId) {
    return false;
  }
  const source = findStepById(steps, sourceId);
  const target = findStepById(steps, targetId);
  if (!source || !target || containsStep(source.children, targetId)) {
    return false;
  }
  const targetParentId = findParentStepId(steps, targetId);
  if (mode === 'child') {
    return source.type !== 'THREAD_GROUP' && canAddChildStep(steps, targetId, source);
  }
  if (source.type === 'THREAD_GROUP') {
    return targetParentId === null;
  }
  return targetParentId !== null;
}

export function createStepFromType(
  type: ScriptStepType,
  name = '',
  overrides: StepOverrides = {},
): ScriptStep {
  const children = overrides.children ?? [];
  const configOverrides: Record<string, StepConfigValue> = {};
  Object.entries(overrides).forEach(([key, value]) => {
    if (key !== 'children' && value !== undefined) {
      configOverrides[key] = value as StepConfigValue;
    }
  });
  const defaults: Record<ScriptStepType, Record<string, StepConfigValue>> = {
    THREAD_GROUP: {
      threads: 100,
      rampUp: 60,
      loops: 1,
      duration: 600,
      scheduler: false,
      mode: 'count',
      stepping: {
        initialDelay: 0,
        startUsersCount: 10,
        startUsersPeriod: 30,
        rampUp: 0,
        flightTime: 60,
        stopUsersCount: 10,
        stopUsersPeriod: 30,
        burst: false,
      },
    },
    HTTP_REQUEST: {
      method: 'GET',
      url: '${host}/api/example',
      params: [],
      headers: [],
      bodyType: 'none',
      rawBodyType: 'json',
      body: '',
      bodyParams: [],
      advanced: {
        connectTimeout: 30000,
        responseTimeout: 30000,
        followRedirects: true,
        keepAlive: true,
      },
    },
    ASSERTION: { target: 'body', match: 'contains', rule: 'success' },
    JSON_ASSERTION: { jsonPath: '$.code', validateValue: true, expectedValue: '0', useRegex: false },
    CSV_DATA: { fileName: 'data/default.csv', variableNames: 'userId,token' },
    USER_PARAMS: { paramsText: 'env=SIT\nchannel=APP' },
    HEADER_CONFIG: { headersText: 'Content-Type: application/json' },
  };
  return {
    id: createStepId(type.toLowerCase()),
    type,
    name: name || stepTypeLabel(type),
    config: {
      ...defaults[type],
      ...configOverrides,
    },
    children,
  };
}

export function createStepsFromParsed(
  scriptName: string,
  threadGroups: ThreadGroup[],
  apis: ApiConfig[],
  variables: KeyValue[],
): ScriptStep[] {
  return threadGroups.map((group, groupIndex) => ({
    id: createStepId('thread'),
    type: 'THREAD_GROUP',
    name: group.name,
    config: {
      threads: group.threads,
      rampUp: group.rampUp,
      loops: group.loops,
      duration: group.duration,
      scheduler: group.scheduler ?? false,
      mode: group.mode ?? (group.scheduler ? 'duration' : 'count'),
      stepping: group.stepping ?? {
        initialDelay: 0,
        startUsersCount: 10,
        startUsersPeriod: 30,
        rampUp: 0,
        flightTime: 60,
        stopUsersCount: 10,
        stopUsersPeriod: 30,
        burst: false,
      },
    },
    children: [
      ...(groupIndex === 0
        ? [
            createStepFromType('CSV_DATA', `${scriptName} 数据文件`, {
              fileName: 'data/default.csv',
              variableNames: variables.map((variable) => variable.key).join(','),
            }),
            createStepFromType('USER_PARAMS', '默认用户参数', {
              paramsText: variables.map((variable) => `${variable.key}=${variable.value}`).join('\n'),
            }),
            createStepFromType('HEADER_CONFIG', '公共 Header 配置', {
              headersText: 'Content-Type: application/json\nX-Env: ${env}',
            }),
          ]
        : []),
      ...apis
        .filter((_, apiIndex) => apiIndex % Math.max(threadGroups.length, 1) === groupIndex)
        .map((api, apiIndex) =>
          createStepFromType('HTTP_REQUEST', `${api.method} ${api.path}`, {
            method: api.method,
            domain: api.domain,
            path: api.path,
            url: `${api.domain}${api.path}`,
            children: [
              createStepFromType('JSON_ASSERTION', `JSON 断言 ${apiIndex + 1}`, {
                jsonPath: '$.code',
                validateValue: true,
                expectedValue: '0',
                useRegex: false,
              }),
            ],
          }),
        ),
    ],
  }));
}
