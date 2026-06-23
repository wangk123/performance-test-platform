import type { HttpBodyType, HttpParamConfig, ScriptStep } from '../types';
import { createStepFromType } from './script-steps';
import { createStepId } from './format';

export function parseJmeterXmlFragment(xml: string): ScriptStep[] {
  const document = parseXml(`<import-fragment>${xml.trim()}</import-fragment>`);
  const steps: ScriptStep[] = [];
  const children = Array.from(document.documentElement.children);
  for (let index = 0; index < children.length; index += 1) {
    const element = children[index];
    if (element.tagName === 'hashTree') {
      continue;
    }
    const hashTree = children[index + 1]?.tagName === 'hashTree' ? children[index + 1] : null;
    const step = parseElement(element, hashTree);
    if (step) {
      steps.push(step);
    }
    if (hashTree) {
      index += 1;
    }
  }
  if (!steps.length) {
    throw new Error('XML 中未识别到可导入的 JMeter 组件');
  }
  return steps.map(reassignStepIds);
}

function parseElement(element: Element, hashTree: Element | null): ScriptStep | null {
  switch (element.tagName) {
    case 'HTTPSamplerProxy':
      return parseHttpSampler(element, hashTree);
    case 'CSVDataSet':
      return createStepFromType('CSV_DATA', element.getAttribute('testname') || 'CSV 数据', {
        fileName: stringValue(element, 'filename', ''),
        variableNames: stringValue(element, 'variableNames', ''),
      });
    case 'Arguments':
      return createStepFromType('USER_PARAMS', element.getAttribute('testname') || '用户参数', {
        paramsText: parseUserParamsText(element),
      });
    case 'HeaderManager':
      return createStepFromType('HEADER_CONFIG', element.getAttribute('testname') || 'Header 配置', {
        headersText: textLines(parseHeaderItems(element), ': '),
      });
    case 'ResponseAssertion':
      return createStepFromType('ASSERTION', element.getAttribute('testname') || '响应断言', {
        target: assertionTarget(stringValue(element, 'Assertion.test_field', 'Assertion.response_data')),
        match: assertionMatch(intPropValue(element, 'Assertion.test_type', 2)),
        rule: assertionRule(element),
      });
    case 'JSONPathAssertion':
      return createStepFromType('JSON_ASSERTION', element.getAttribute('testname') || 'JSON 断言', {
        jsonPath: stringValue(element, 'JSON_PATH', ''),
        validateValue: boolValue(element, 'JSONVALIDATION', false),
        expectedValue: stringValue(element, 'EXPECTED_VALUE', ''),
        useRegex: boolValue(element, 'ISREGEX', false),
      });
    case 'ThreadGroup':
    case 'kg.apc.jmeter.threads.SteppingThreadGroup':
      return parseThreadGroup(element, hashTree);
    default:
      return null;
  }
}

function parseHttpSampler(sampler: Element, hashTree: Element | null): ScriptStep {
  const method = stringValue(sampler, 'HTTPSampler.method', 'GET');
  const domain = stringValue(sampler, 'HTTPSampler.domain', '');
  const port = stringValue(sampler, 'HTTPSampler.port', '');
  const protocol = stringValue(sampler, 'HTTPSampler.protocol', '');
  const path = stringValue(sampler, 'HTTPSampler.path', '');
  const postBodyRaw = boolValue(sampler, 'HTTPSampler.postBodyRaw', false);
  const argumentsList = parseHttpArguments(sampler);
  const queryParams = parseQueryParams(path);
  const cleanPath = path.replace(/\?.*$/, '');
  const authority = port ? `${domain}:${port}` : domain;
  const url = `${protocol ? `${protocol}://` : ''}${authority}${cleanPath}`;

  let bodyType: HttpBodyType = 'none';
  let body = '';
  let bodyParams: HttpParamConfig[] = [];
  let params = [...queryParams];

  if (postBodyRaw) {
    bodyType = 'raw';
    body = argumentsList[0]?.value ?? '';
  } else if (isFormMethod(method) && argumentsList.length) {
    bodyType = 'form-urlencoded';
    bodyParams = argumentsList;
  } else if (argumentsList.length) {
    params = [...queryParams, ...argumentsList];
  }

  const children = parseChildren(hashTree).filter((step) => step.type !== 'HEADER_CONFIG');
  return createStepFromType('HTTP_REQUEST', `${method} ${cleanPath || url}`, {
    method,
    domain,
    path: cleanPath,
    url: url || cleanPath,
    params,
    headers: parseHeaderItemsFromTree(hashTree),
    bodyType,
    rawBodyType: 'json',
    body,
    bodyParams,
    advanced: {
      connectTimeout: intValue(sampler, 'HTTPSampler.connect_timeout', 30000),
      responseTimeout: intValue(sampler, 'HTTPSampler.response_timeout', 30000),
      followRedirects: boolValue(sampler, 'HTTPSampler.follow_redirects', true),
      keepAlive: boolValue(sampler, 'HTTPSampler.use_keepalive', true),
    },
    children,
  });
}

function parseThreadGroup(element: Element, hashTree: Element | null): ScriptStep {
  const stepping = element.tagName === 'kg.apc.jmeter.threads.SteppingThreadGroup';
  const config: Record<string, unknown> = {
    threads: intValue(element, 'ThreadGroup.num_threads', 1),
    rampUp: stepping ? 0 : intValue(element, 'ThreadGroup.ramp_time', 0),
    loops: intValue(element, 'LoopController.loops', 1),
    duration: stepping ? intValue(element, 'flighttime', 60) : intValue(element, 'ThreadGroup.duration', 0),
    scheduler: stepping ? false : boolValue(element, 'ThreadGroup.scheduler', false),
    mode: stepping ? 'stepping' : 'count',
  };
  if (stepping) {
    config.stepping = {
      initialDelay: intValue(element, 'Threads initial delay', 0),
      startUsersCount: intValue(element, 'Start users count', 10),
      startUsersPeriod: intValue(element, 'Start users period', 30),
      rampUp: intValue(element, 'rampUp', 0),
      flightTime: intValue(element, 'flighttime', 60),
      stopUsersCount: intValue(element, 'Stop users count', 10),
      stopUsersPeriod: intValue(element, 'Stop users period', 30),
      burst: boolStringValue(element, 'Start users count burst', false),
    };
  }
  return createStepFromType('THREAD_GROUP', element.getAttribute('testname') || '线程组', {
    ...config,
    children: parseChildren(hashTree),
  });
}

function parseChildren(hashTree: Element | null): ScriptStep[] {
  if (!hashTree) {
    return [];
  }
  const steps: ScriptStep[] = [];
  const children = Array.from(hashTree.children);
  for (let index = 0; index < children.length; index += 1) {
    const element = children[index];
    if (element.tagName === 'hashTree') {
      continue;
    }
    const childTree = children[index + 1]?.tagName === 'hashTree' ? children[index + 1] : null;
    const step = parseElement(element, childTree);
    if (step) {
      steps.push(step);
    }
    if (childTree) {
      index += 1;
    }
  }
  return steps;
}

function reassignStepIds(step: ScriptStep): ScriptStep {
  return {
    ...step,
    id: createStepId(step.type.toLowerCase()),
    children: step.children.map(reassignStepIds),
  };
}

function parseXml(xml: string) {
  const document = new DOMParser().parseFromString(xml, 'application/xml');
  if (document.getElementsByTagName('parsererror').length > 0) {
    throw new Error('XML 格式不正确');
  }
  return document;
}

function stringValue(root: Element, name: string, fallback: string) {
  for (const element of root.getElementsByTagName('stringProp')) {
    if (element.getAttribute('name') === name) {
      return element.textContent ?? fallback;
    }
  }
  return fallback;
}

function boolValue(root: Element, name: string, fallback: boolean) {
  for (const element of root.getElementsByTagName('boolProp')) {
    if (element.getAttribute('name') === name) {
      return element.textContent === 'true';
    }
  }
  return fallback;
}

function boolStringValue(root: Element, name: string, fallback: boolean) {
  const value = stringValue(root, name, String(fallback));
  return value ? value === 'true' : fallback;
}

function intValue(root: Element, name: string, fallback: number) {
  const value = stringValue(root, name, String(fallback));
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function intPropValue(root: Element, name: string, fallback: number) {
  for (const element of root.getElementsByTagName('intProp')) {
    if (element.getAttribute('name') === name) {
      const parsed = Number.parseInt(element.textContent ?? '', 10);
      return Number.isFinite(parsed) ? parsed : fallback;
    }
  }
  return fallback;
}

function parseHttpArguments(sampler: Element) {
  const params: HttpParamConfig[] = [];
  for (const element of sampler.getElementsByTagName('elementProp')) {
    if (element.getAttribute('elementType') !== 'HTTPArgument') {
      continue;
    }
    params.push(param(stringValue(element, 'Argument.name', ''), stringValue(element, 'Argument.value', '')));
  }
  return params;
}

function parseHeaderItems(root: Element) {
  const headers: HttpParamConfig[] = [];
  for (const element of root.getElementsByTagName('elementProp')) {
    if (element.getAttribute('elementType') !== 'Header') {
      continue;
    }
    headers.push(param(stringValue(element, 'Header.name', ''), stringValue(element, 'Header.value', '')));
  }
  return headers;
}

function parseHeaderItemsFromTree(hashTree: Element | null) {
  if (!hashTree) {
    return [];
  }
  for (const child of hashTree.children) {
    if (child.tagName === 'HeaderManager') {
      return parseHeaderItems(child);
    }
  }
  return [];
}

function parseUserParamsText(element: Element) {
  const lines: string[] = [];
  for (const item of element.getElementsByTagName('elementProp')) {
    if (item.getAttribute('elementType') !== 'Argument') {
      continue;
    }
    const key = stringValue(item, 'Argument.name', '');
    const value = stringValue(item, 'Argument.value', '');
    if (key) {
      lines.push(`${key}=${value}`);
    }
  }
  return lines.join('\n');
}

function parseQueryParams(path: string) {
  const queryIndex = path.indexOf('?');
  if (queryIndex < 0) {
    return [];
  }
  return path
    .slice(queryIndex + 1)
    .split('&')
    .filter(Boolean)
    .map((pair) => {
      const separator = pair.indexOf('=');
      const key = separator >= 0 ? decodeURIComponent(pair.slice(0, separator)) : pair;
      const value = separator >= 0 ? decodeURIComponent(pair.slice(separator + 1)) : '';
      return param(key, value);
    });
}

function assertionTarget(field: string) {
  if (field === 'Assertion.response_code') {
    return 'statusCode';
  }
  if (field === 'Assertion.response_headers') {
    return 'headers';
  }
  return 'body';
}

function assertionMatch(matchType: number) {
  if (matchType === 8) {
    return 'equals';
  }
  if (matchType === 1) {
    return 'regex';
  }
  return 'contains';
}

function assertionRule(element: Element) {
  for (const collection of element.getElementsByTagName('collectionProp')) {
    if (collection.getAttribute('name') !== 'Assertion.test_strings') {
      continue;
    }
    const first = collection.getElementsByTagName('stringProp')[0];
    return first?.textContent ?? '';
  }
  return '';
}

function textLines(items: HttpParamConfig[], separator: string) {
  return items.map((item) => `${item.key}${separator}${item.value}`).join('\n');
}

function param(key: string, value: string): HttpParamConfig {
  return { enabled: true, key, value, description: '' };
}

function isFormMethod(method: string) {
  return ['POST', 'PUT', 'PATCH'].includes(method.toUpperCase());
}
