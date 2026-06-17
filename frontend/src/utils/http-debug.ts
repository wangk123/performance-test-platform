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
  const startedAt = performance.now();
  const method = config.method.toUpperCase();
  const requestHeaders = buildHeaders(config.headers, variables);
  const requestBody = buildBody(config, variables);
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), config.advanced.responseTimeout || 30000);
  const url = appendParams(replaceVariables(config.url, variables), config.params, variables);
  try {
    const response = await fetch(url, {
      method,
      headers: requestHeaders,
      body: method === 'GET' || method === 'HEAD' ? undefined : requestBody.body,
      signal: controller.signal,
    });
    return {
      ok: response.ok,
      url,
      method,
      status: response.status,
      statusText: response.statusText,
      durationMs: Math.round(performance.now() - startedAt),
      requestHeaders,
      responseHeaders: headersToRecord(response.headers),
      requestBody: requestBody.preview,
      responseBody: await response.text(),
      error: '',
    };
  } catch (error) {
    return {
      ok: false,
      url,
      method,
      status: null,
      statusText: '',
      durationMs: Math.round(performance.now() - startedAt),
      requestHeaders,
      responseHeaders: {},
      requestBody: requestBody.preview,
      responseBody: '',
      error: error instanceof Error ? error.message : '调试请求失败',
    };
  } finally {
    window.clearTimeout(timeout);
  }
}

function appendParams(url: string, params: HttpParamConfig[], variables: VariableOption[]) {
  const enabledParams = params.filter((item) => item.enabled && item.key);
  if (!enabledParams.length) {
    return url;
  }
  const parsed = new URL(url, window.location.origin);
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
    const formData = new FormData();
    const preview = new URLSearchParams();
    config.bodyParams.filter((item) => item.enabled && item.key).forEach((item) => {
      const key = replaceVariables(item.key, variables);
      const value = replaceVariables(item.value, variables);
      formData.set(key, value);
      preview.set(key, value);
    });
    return { body: formData, preview: preview.toString() };
  }
  return { body: undefined, preview: '' };
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

function headersToRecord(headers: Headers) {
  const result: Record<string, string> = {};
  headers.forEach((value, key) => {
    result[key] = value;
  });
  return result;
}
