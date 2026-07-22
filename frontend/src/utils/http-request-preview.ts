import type { HttpParamConfig, HttpRequestConfig } from '../types';
import type { VariableOption } from './http-request-config';

export type HttpRequestPreview = {
  method: string;
  url: string;
  headers: Record<string, string>;
  body: string;
  functionsUnevaluated: true;
};

export function buildHttpRequestPreview(
  config: HttpRequestConfig,
  variables: VariableOption[],
): HttpRequestPreview {
  const method = config.method.toUpperCase();
  const headers = buildHeaders(config.headers, variables);
  const body = buildBody(config, variables);
  const url = appendParams(replaceVariablesSoft(config.url, variables), config.params, variables);
  return {
    method,
    url,
    headers,
    body,
    functionsUnevaluated: true,
  };
}

function appendParams(url: string, params: HttpParamConfig[], variables: VariableOption[]) {
  const enabledParams = params.filter((item) => item.enabled && item.key);
  if (!enabledParams.length) {
    return url;
  }
  try {
    const parsed = new URL(url, 'http://localhost');
    enabledParams.forEach((item) => {
      parsed.searchParams.set(
        replaceVariablesSoft(item.key, variables),
        replaceVariablesSoft(item.value, variables),
      );
    });
    return parsed.toString();
  } catch {
    const query = enabledParams
      .map(
        (item) =>
          `${encodeURIComponent(replaceVariablesSoft(item.key, variables))}=${encodeURIComponent(replaceVariablesSoft(item.value, variables))}`,
      )
      .join('&');
    return `${url}${url.includes('?') ? '&' : '?'}${query}`;
  }
}

function buildHeaders(headers: HttpParamConfig[], variables: VariableOption[]) {
  return headers
    .filter((item) => item.enabled && item.key)
    .reduce<Record<string, string>>((result, item) => {
      result[replaceVariablesSoft(item.key, variables)] = replaceVariablesSoft(item.value, variables);
      return result;
    }, {});
}

function buildBody(config: HttpRequestConfig, variables: VariableOption[]) {
  if (config.bodyType === 'raw') {
    return replaceVariablesSoft(config.body, variables);
  }
  if (config.bodyType === 'form-urlencoded' || config.bodyType === 'form-data') {
    const params = new URLSearchParams();
    config.bodyParams
      .filter((item) => item.enabled && item.key)
      .forEach((item) => {
        params.set(replaceVariablesSoft(item.key, variables), replaceVariablesSoft(item.value, variables));
      });
    return params.toString();
  }
  return '';
}

/** Preview-only: replace known ${var}; leave unknown and ${__fn(...)} literal. */
function replaceVariablesSoft(value: string, variables: VariableOption[]) {
  const map = new Map(variables.map((item) => [item.key, item.value]));
  return String(value).replace(/\$\{([\w.-]+)\}/g, (source, key) => {
    const replacement = map.get(key);
    return replacement === undefined ? source : replacement;
  });
}
