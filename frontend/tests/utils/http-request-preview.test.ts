import assert from 'node:assert/strict';
import test from 'node:test';
import { buildHttpRequestPreview } from '../../src/utils/http-request-preview.ts';
import type { HttpRequestConfig } from '../../src/types/index.ts';

function baseConfig(overrides: Partial<HttpRequestConfig> = {}): HttpRequestConfig {
  return {
    method: 'POST',
    url: 'https://api.example.com/${path}',
    params: [{ enabled: true, key: 'q', value: '${q}', description: '' }],
    headers: [{ enabled: true, key: 'X-Token', value: '${token}', description: '' }],
    bodyType: 'raw',
    rawBodyType: 'json',
    body: '{"id":"${__UUID()}","name":"${name}"}',
    bodyParams: [],
    advanced: { connectTimeout: 30000, responseTimeout: 30000, followRedirects: true, keepAlive: true },
    ...overrides,
  };
}

test('buildHttpRequestPreview substitutes variables and keeps function literals', () => {
  const preview = buildHttpRequestPreview(baseConfig(), [
    { key: 'path', label: 'path', value: 'users' },
    { key: 'q', label: 'q', value: 'abc' },
    { key: 'token', label: 'token', value: 't-1' },
    { key: 'name', label: 'name', value: '张三' },
  ]);
  assert.equal(preview.method, 'POST');
  assert.match(preview.url, /\/users/);
  assert.match(preview.url, /q=abc/);
  assert.equal(preview.headers['X-Token'], 't-1');
  assert.match(preview.body, /\$\{__UUID\(\)\}/);
  assert.match(preview.body, /张三/);
  assert.equal(preview.functionsUnevaluated, true);
});

test('buildHttpRequestPreview leaves unknown variables unresolved', () => {
  const preview = buildHttpRequestPreview(baseConfig({ body: '${missing}', params: [], headers: [] }), []);
  assert.equal(preview.body, '${missing}');
  assert.match(preview.url, /\$\{path\}/);
});
