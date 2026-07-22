import type { HttpBodyType, HttpParamConfig, HttpRawBodyType, HttpRequestConfig } from '../types';

export type VariableOption = {
  key: string;
  label: string;
  value: string;
  /** When set, insert this text as-is instead of `${key}`. */
  insertText?: string;
};

export type ActiveVariableField = {
  id: string;
  element: HTMLInputElement | HTMLTextAreaElement | null;
  triggerStart: number;
  query: string;
  suggesting: boolean;
  source?: 'input' | 'codemirror';
};

export const systemVariables: VariableOption[] = [
  { key: 'mobile', label: '随机手机号', value: '13800000000' },
  { key: 'serialNo', label: '随机流水号', value: '202606020001' },
  { key: 'randomString8', label: '8 位随机字符串', value: 'a1b2c3d4' },
  { key: 'name', label: '随机姓名', value: '张三' },
  { key: 'idCard', label: '随机身份证号', value: '110101199001011234' },
  { key: 'timestamp', label: '当前时间戳', value: '1780339200000' },
  { key: 'uuid', label: 'UUID', value: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx' },
];

export function createEmptyHttpParam(): HttpParamConfig {
  return { enabled: true, key: '', value: '', description: '' };
}

export function placeholderOf(key: string) {
  return `\${${key}}`;
}

export type HttpBodyLanguage = 'json' | 'xml' | 'html' | 'text';

export function detectHttpBodyLanguage(value: string): HttpBodyLanguage {
  const text = value.trim();
  if (!text) {
    return 'text';
  }
  if (text.startsWith('{') || text.startsWith('[')) {
    return 'json';
  }
  if (text.startsWith('<')) {
    if (/^<\s*(!DOCTYPE\s+html|html\b)/i.test(text)) {
      return 'html';
    }
    return 'xml';
  }
  return 'text';
}

export function formatHttpBodyAuto(value: string) {
  const text = value.trim();
  if (!text) {
    return value;
  }
  return formatBodyContent(detectHttpBodyLanguage(text), text);
}

export function formatBodyContent(type: string, value: string) {
  const text = value.trim();
  if (!text) {
    return value;
  }
  if (type === 'json') {
    return formatJson(text, value);
  }
  if (type === 'xml' || type === 'html') {
    return formatXml(text, value);
  }
  return value;
}

export function contentTypeOf(bodyType: string, rawBodyType: string) {
  if (bodyType === 'form-data') {
    return 'multipart/form-data';
  }
  if (bodyType === 'form-urlencoded') {
    return 'application/x-www-form-urlencoded';
  }
  if (bodyType !== 'raw') {
    return '';
  }
  const rawContentTypes: Record<string, string> = {
    text: 'text/plain',
    javascript: 'application/javascript',
    json: 'application/json',
    html: 'text/html',
    xml: 'application/xml',
  };
  return rawContentTypes[rawBodyType] ?? 'text/plain';
}

export function normalizeBodyType(value: string): { bodyType: HttpBodyType; rawBodyType: HttpRawBodyType } {
  if (value === 'json' || value === 'xml') {
    return { bodyType: 'raw', rawBodyType: value };
  }
  if (value === 'form') {
    return { bodyType: 'form-urlencoded', rawBodyType: 'json' };
  }
  if (value === 'form-data' || value === 'form-urlencoded' || value === 'raw') {
    return { bodyType: value, rawBodyType: 'json' };
  }
  return { bodyType: 'none', rawBodyType: 'json' };
}

export function normalizeRawBodyType(value: string): HttpRawBodyType {
  if (value === 'text' || value === 'javascript' || value === 'json' || value === 'html' || value === 'xml') {
    return value;
  }
  return 'json';
}

export function syncHeadersContentType(
  headers: HttpParamConfig[],
  bodyType: HttpBodyType,
  rawBodyType: HttpRawBodyType,
) {
  const contentType = contentTypeOf(bodyType, rawBodyType);
  const nextHeaders = [...headers];
  const index = nextHeaders.findIndex((item) => item.key.toLowerCase() === 'content-type');
  if (!contentType) {
    return index >= 0 ? nextHeaders.filter((_, itemIndex) => itemIndex !== index) : nextHeaders;
  }
  const nextHeader = { enabled: true, key: 'Content-Type', value: contentType, description: '请求体类型' };
  if (index >= 0) {
    nextHeaders[index] = { ...nextHeaders[index], ...nextHeader };
  } else {
    nextHeaders.unshift(nextHeader);
  }
  return nextHeaders;
}

export function preferredHttpConfigTab(httpConfig: HttpRequestConfig) {
  if (httpConfig.bodyType !== 'none') {
    return 'body';
  }
  if (httpConfig.headers.some((item) => item.key || item.value)) {
    return 'headers';
  }
  return 'params';
}

function formatJson(text: string, fallback: string) {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return fallback;
  }
}

function formatXml(text: string, fallback: string) {
  if (!text.startsWith('<')) {
    return fallback;
  }
  const compact = text.replace(/>\s+</g, '><');
  const lines = compact.replace(/(>)(<)(\/*)/g, '$1\n$2$3').split('\n');
  let depth = 0;
  return lines
    .map((line) => {
      if (/^<\/\w/.test(line)) {
        depth = Math.max(depth - 1, 0);
      }
      const formatted = `${'  '.repeat(depth)}${line}`;
      if (/^<[^!?/][^>]*[^/]>\s*$/.test(line) && !/^<[^>]+>[^<]+<\/[^>]+>$/.test(line)) {
        depth += 1;
      }
      return formatted;
    })
    .join('\n');
}
