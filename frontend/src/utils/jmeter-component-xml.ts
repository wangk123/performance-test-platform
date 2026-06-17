const XML_PARSE_ERROR = 'XML 片段格式不正确';

export function extractStepXml(scriptXml: string, stepId: string) {
  const document = parseXml(scriptXml);
  const element = findElementByStepId(document, stepId);
  if (!element) {
    throw new Error('未找到当前组件 XML');
  }
  const hashTree = nextHashTree(element);
  return formatXml([serialize(element), hashTree ? serialize(hashTree) : '<hashTree/>'].join('\n'));
}

export function patchStepXml(scriptXml: string, stepId: string, componentXml: string) {
  const document = parseXml(scriptXml);
  const element = findElementByStepId(document, stepId);
  if (!element || !element.parentNode) {
    throw new Error('未找到当前组件 XML');
  }
  const fragment = parseComponentFragment(componentXml);
  const next = nextHashTree(element);
  const parent = element.parentNode;
  const importedElement = document.importNode(fragment.element, true);
  const importedHashTree = document.importNode(fragment.hashTree, true);

  parent.replaceChild(importedElement, element);
  if (next && next.parentNode === parent) {
    parent.replaceChild(importedHashTree, next);
  } else if (importedElement.nextSibling) {
    parent.insertBefore(importedHashTree, importedElement.nextSibling);
  } else {
    parent.appendChild(importedHashTree);
  }
  return serialize(document);
}

export function formatXml(xml: string) {
  const document = parseComponentDocument(xml);
  const root = document.documentElement;
  const source = root.tagName === 'component-fragment'
    ? elementChildren(root).map(serialize).join('\n')
    : serialize(document);
  return indentXml(source);
}

function parseComponentFragment(xml: string) {
  const document = parseComponentDocument(xml);
  const children = elementChildren(document.documentElement);
  const element = children.find((child) => child.tagName !== 'hashTree');
  if (!element) {
    throw new Error('XML 片段缺少组件节点');
  }
  const pair = nextHashTree(element);
  return {
    element,
    hashTree: pair ?? document.createElement('hashTree'),
  };
}

function parseComponentDocument(xml: string) {
  return parseXml(`<component-fragment>${xml}</component-fragment>`);
}

function parseXml(xml: string) {
  const document = new DOMParser().parseFromString(xml, 'application/xml');
  if (document.getElementsByTagName('parsererror').length > 0) {
    throw new Error(XML_PARSE_ERROR);
  }
  return document;
}

function findElementByStepId(document: Document, stepId: string) {
  const path = stepId.replace(/^[^-]+-/, '').split('-');
  if (path.length === 0 || path.some((item) => !/^\d+$/.test(item))) {
    return null;
  }
  let current: Element | null = document.documentElement;
  for (const item of path) {
    current = elementChildren(current)[Number(item)] ?? null;
    if (!current) {
      return null;
    }
  }
  return current;
}

function nextHashTree(element: Element) {
  let sibling = element.nextElementSibling;
  while (sibling) {
    if (sibling.tagName === 'hashTree') {
      return sibling;
    }
    return null;
  }
  return null;
}

function elementChildren(element: Element) {
  return Array.from(element.children);
}

function serialize(node: Node) {
  return new XMLSerializer().serializeToString(node);
}

function indentXml(xml: string) {
  const normalized = xml
    .replace(/>\s+</g, '><')
    .replace(/(>)(<)(\/*)/g, '$1\n$2$3');
  let level = 0;
  return normalized
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      if (/^<\//.test(line)) {
        level = Math.max(level - 1, 0);
      }
      const output = `${'  '.repeat(level)}${line}`;
      if (/^<[^!?/][^>]*[^/]>\s*$/.test(line) && !/^<[^>]+><\/[^>]+>$/.test(line)) {
        level += 1;
      }
      return output;
    })
    .join('\n');
}
