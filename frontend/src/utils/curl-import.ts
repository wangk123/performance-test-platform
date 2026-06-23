import type { HttpBodyType, HttpParamConfig, HttpRawBodyType } from '../types';

export type CurlImportResult = {
  method: string;
  url: string;
  headers: HttpParamConfig[];
  bodyType: HttpBodyType;
  rawBodyType: HttpRawBodyType;
  body: string;
  bodyParams: HttpParamConfig[];
};

export function parseCurlCommand(raw: string): CurlImportResult {
  const input = raw.trim().replace(/\\\r?\n/g, ' ');
  if (!/^curl\b/i.test(input)) {
    throw new Error('请输入以 curl 开头的命令');
  }

  let method = 'GET';
  let url = '';
  const headers: HttpParamConfig[] = [];
  const bodyParams: HttpParamConfig[] = [];
  let body = '';
  let bodyType: HttpBodyType = 'none';
  let hasBody = false;
  let hasForm = false;

  const methodMatch = input.match(/(?:^|\s)(?:-X|--request)\s+(['"]?)(\w+)\1/i);
  if (methodMatch) {
    method = methodMatch[2].toUpperCase();
  }

  for (const match of input.matchAll(/(?:^|\s)(?:-H|--header)\s+(['"])(.*?)\1/gi)) {
    const separator = match[2].indexOf(':');
    if (separator < 0) {
      continue;
    }
    headers.push(param(match[2].slice(0, separator).trim(), match[2].slice(separator + 1).trim()));
  }

  for (const match of input.matchAll(/(?:^|\s)(?:-b|--cookie)\s+(?:'([^']*)'|"([^"]*)"|([^\s]+))/gi)) {
    appendCookie(headers, match[1] ?? match[2] ?? match[3] ?? '');
  }

  for (const match of input.matchAll(/(?:^|\s)(?:-F|--form)\s+(['"])(.*?)\1/gi)) {
    const separator = match[2].indexOf('=');
    const key = separator >= 0 ? match[2].slice(0, separator) : match[2];
    const value = separator >= 0 ? match[2].slice(separator + 1) : '';
    bodyParams.push(param(key, value));
    hasForm = true;
    hasBody = true;
  }

  const dataMatch =
    input.match(/(?:^|\s)(?:-d|--data|--data-raw|--data-binary|--data-urlencode)\s+(['"])([\s\S]*?)\1/i) ??
    input.match(/(?:^|\s)(?:-d|--data|--data-raw|--data-binary|--data-urlencode)\s+(\S+)/i);
  if (dataMatch) {
    body = dataMatch[2] ?? dataMatch[1];
    hasBody = true;
    if (!hasForm) {
      bodyType = 'raw';
    }
    if (!methodMatch) {
      method = 'POST';
    }
  }

  const urlFlagMatch = input.match(/(?:^|\s)--url\s+(['"]?)([^\s'"]+)\1/i);
  if (urlFlagMatch) {
    url = urlFlagMatch[2];
  } else {
    const urlMatch = input.match(/(?:^|\s)(['"])(https?:\/\/[^\s'"]+)\1/i) ?? input.match(/(?:^|\s)(https?:\/\/[^\s'"]+)/i);
    url = urlMatch?.[2] ?? urlMatch?.[1] ?? '';
  }

  if (!url) {
    throw new Error('未识别到请求 URL');
  }

  if (hasForm) {
    bodyType = 'form-data';
    body = '';
  } else if (hasBody && bodyType === 'raw') {
    const contentType = headers.find((item) => item.key.toLowerCase() === 'content-type')?.value ?? '';
    if (contentType.includes('application/x-www-form-urlencoded')) {
      bodyType = 'form-urlencoded';
      for (const pair of body.split('&')) {
        const separator = pair.indexOf('=');
        const key = separator >= 0 ? decodeURIComponent(pair.slice(0, separator)) : pair;
        const value = separator >= 0 ? decodeURIComponent(pair.slice(separator + 1)) : '';
        bodyParams.push(param(key, value));
      }
      body = '';
    }
  }

  if (!methodMatch && hasBody && method === 'GET') {
    method = 'POST';
  }

  return {
    method,
    url,
    headers,
    bodyType,
    rawBodyType: guessRawBodyType(body, headers),
    body,
    bodyParams,
  };
}

function guessRawBodyType(body: string, headers: HttpParamConfig[]): HttpRawBodyType {
  const contentType = headers.find((item) => item.key.toLowerCase() === 'content-type')?.value.toLowerCase() ?? '';
  if (contentType.includes('json') || looksLikeJson(body)) {
    return 'json';
  }
  if (contentType.includes('xml') || body.trim().startsWith('<')) {
    return 'xml';
  }
  if (contentType.includes('html')) {
    return 'html';
  }
  if (contentType.includes('javascript')) {
    return 'javascript';
  }
  return 'text';
}

function looksLikeJson(body: string) {
  const text = body.trim();
  return (text.startsWith('{') && text.endsWith('}')) || (text.startsWith('[') && text.endsWith(']'));
}

function param(key: string, value: string): HttpParamConfig {
  return { enabled: true, key, value, description: '' };
}

function appendCookie(headers: HttpParamConfig[], cookie: string) {
  const value = cookie.trim();
  if (!value) {
    return;
  }
  const existing = headers.find((item) => item.key.toLowerCase() === 'cookie');
  if (existing) {
    existing.value = `${existing.value}; ${value}`;
    return;
  }
  headers.push(param('Cookie', value));
}
