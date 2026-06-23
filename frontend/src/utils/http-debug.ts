import { executeHttpDebugApi } from '../api/http-debug';
import type { HttpParamConfig, HttpRequestConfig } from '../types';
import type { VariableOption } from './http-request-config';

export type HttpDebugResult = {
  ok: boolean;
  url: string;
  method: string;
  status: number | null;
  statusText: string;
  durationMs: number;
  requestHeaders: Record<string, string>;
  responseHeaders: Record<string, string>;
  requestBody: string;
  responseBody: string;
  error: string;
};

export async function executeHttpDebug(config: HttpRequestConfig, variables: VariableOption[]): Promise<HttpDebugResult> {
  const method = config.method.toUpperCase();
  const requestHeaders = buildHeaders(config.headers, variables);
  const requestBody = buildBody(config, variables);
  const url = appendParams(replaceVariables(config.url, variables), config.params, variables);
  const body = serializeBody(requestBody);
  return executeHttpDebugApi({
    method,
    url,
    headers: requestHeaders,
    body,
    timeoutMs: config.advanced.responseTimeout || 30000,
  });
}

function appendParams(url: string, params: HttpParamConfig[], variables: VariableOption[]) {
  const enabledParams = params.filter((item) => item.enabled && item.key);
  if (!enabledParams.length) {
    return url;
  }
  const parsed = new URL(url, 'http://localhost');
  enabledParams.forEach((item) => {
    parsed.searchParams.set(replaceVariables(item.key, variables), replaceVariables(item.value, variables));
  });
  return parsed.toString();
}

function buildHeaders(headers: HttpParamConfig[], variables: VariableOption[]) {
  return headers
    .filter((item) => item.enabled && item.key)
    .reduce<Record<string, string>>((result, item) => {
      result[replaceVariables(item.key, variables)] = replaceVariables(item.value, variables);
      return result;
    }, {});
}

function buildBody(config: HttpRequestConfig, variables: VariableOption[]) {
  if (config.bodyType === 'raw') {
    const body = replaceVariables(config.body, variables);
    return { body, preview: body };
  }
  if (config.bodyType === 'form-urlencoded') {
    const params = new URLSearchParams();
    config.bodyParams.filter((item) => item.enabled && item.key).forEach((item) => {
      params.set(replaceVariables(item.key, variables), replaceVariables(item.value, variables));
    });
    return { body: params.toString(), preview: params.toString() };
  }
  if (config.bodyType === 'form-data') {
    const preview = new URLSearchParams();
    config.bodyParams.filter((item) => item.enabled && item.key).forEach((item) => {
      preview.set(replaceVariables(item.key, variables), replaceVariables(item.value, variables));
    });
    return { body: preview.toString(), preview: preview.toString() };
  }
  return { body: '', preview: '' };
}

function serializeBody(requestBody: { body: string; preview: string }) {
  return requestBody.body || requestBody.preview || '';
}

function replaceVariables(value: string, variables: VariableOption[]) {
  const map = new Map(variables.map((item) => [item.key, item.value]));
  return String(value).replace(/\$\{([\w.-]+)\}/g, (source, key) => {
    const replacement = map.get(key);
    if (replacement === undefined) {
      throw new Error(`变量未定义：${source}`);
    }
    return replacement;
  });
}
