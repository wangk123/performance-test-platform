import type { ConfigTab, ParseStatus, ProjectRole, ProjectStatus, ProjectTab, ScriptStepType } from '../types';
import { configTabOptions, projectTabOptions, stepTypeMeta } from '../constants';

export function delay(ms: number) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

export function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

export function countMatches(value: string, pattern: RegExp) {
  return value.match(pattern)?.length ?? 0;
}

export function sanitizeFileName(fileName: string) {
  return fileName.replace(/[\\/]/g, '-').replace(/\s+/g, '-');
}

export function nextId(items: Array<{ id: number }>) {
  return items.reduce((max, item) => Math.max(max, item.id), 0) + 1;
}

export function createStepId(prefix: string) {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
}

export function mockHash(value: string) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash << 5) - hash + value.charCodeAt(index);
    hash |= 0;
  }
  return `sha256:${Math.abs(hash).toString(16).padStart(8, '0')}`;
}

export function parseParamNumber(value: unknown, fallback: number) {
  const parsed = Number(String(value ?? '').replace(/[^\d.]/g, ''));
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

export function formatFileSize(size: number) {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function tabLabel(tab: ProjectTab) {
  return projectTabOptions.find((item) => item.value === tab)?.label ?? '项目概览';
}

export function configLabel(tab: ConfigTab) {
  return configTabOptions.find((item) => item.value === tab)?.label ?? '用户管理';
}

export function moduleIndex(tab: ProjectTab) {
  return String(projectTabOptions.findIndex((item) => item.value === tab) + 1).padStart(2, '0');
}

export function configIndex(tab: ConfigTab) {
  return String(configTabOptions.findIndex((item) => item.value === tab) + 1).padStart(2, '0');
}

export function projectStatusText(status: ProjectStatus) {
  return status === 'ACTIVE' ? '活跃' : '已归档';
}

export function projectRoleText(role: ProjectRole) {
  return role === 'OWNER' ? '项目负责人' : '项目成员';
}

export function parseStatusText(status: ParseStatus) {
  return status === 'PARSED' ? '解析成功' : '解析失败';
}

export function stepTypeLabel(type: ScriptStepType) {
  return stepTypeMeta[type]?.label ?? type;
}

export function stepTypeHint(type: ScriptStepType) {
  return stepTypeMeta[type]?.hint ?? '';
}
