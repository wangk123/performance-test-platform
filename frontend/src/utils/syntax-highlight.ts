const PLACEHOLDER_RE = /\$\{[\w.-]+(?:\([^}]*\))?\}/g;

export function highlightVariables(value: string) {
  return escapeHtml(value).replace(PLACEHOLDER_RE, (match) => `<mark>${match}</mark>`);
}

export function highlightContent(value: string, mode = 'text') {
  if (mode === 'json') {
    return highlightJson(value);
  }
  if (mode === 'xml' || mode === 'html') {
    return highlightMarkup(value);
  }
  if (mode === 'javascript') {
    return highlightJavaScript(value);
  }
  return highlightVariables(value);
}

function highlightJson(value: string) {
  return value.replace(
    /(\$\{[\w.-]+(?:\([^}]*\))?\})|("(?:\\.|[^"\\])*"(?=\s*:))|("(?:\\.|[^"\\])*")|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|\b(true|false|null)\b|([{}[\],:])/g,
    (match, variable, key, string, number, literal, punct) => {
      if (variable) {
        return `<mark>${escapeHtml(variable)}</mark>`;
      }
      if (key) {
        return `<span class="syntax-key">${highlightVariables(key)}</span>`;
      }
      if (string) {
        return `<span class="syntax-string">${highlightVariables(string)}</span>`;
      }
      if (number) {
        return `<span class="syntax-number">${escapeHtml(number)}</span>`;
      }
      if (literal) {
        return `<span class="syntax-literal">${escapeHtml(literal)}</span>`;
      }
      if (punct) {
        return `<span class="syntax-punct">${escapeHtml(punct)}</span>`;
      }
      return escapeHtml(match);
    },
  );
}

function highlightMarkup(value: string) {
  return escapeHtml(value)
    .replace(/(&lt;\/?)([\w:-]+)([^&]*?)(&gt;)/g, (_match, open, tag, attrs, close) => {
      const highlightedAttrs = attrs.replace(
        /([\w:-]+)(=)(&quot;.*?&quot;|'.*?')/g,
        '<span class="syntax-attr">$1</span>$2<span class="syntax-string">$3</span>',
      );
      return `<span class="syntax-punct">${open}</span><span class="syntax-tag">${tag}</span>${highlightedAttrs}<span class="syntax-punct">${close}</span>`;
    })
    .replace(PLACEHOLDER_RE, (match) => `<mark>${match}</mark>`);
}

function highlightJavaScript(value: string) {
  return value.replace(
    /(\$\{[\w.-]+(?:\([^}]*\))?\})|(\/\/.*?$|\/\*[\s\S]*?\*\/)|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|(-?\d+(?:\.\d+)?)|\b(const|let|var|return|if|else|true|false|null|undefined|function|await|async)\b/gm,
    (match, variable, comment, string, number, keyword) => {
      if (variable) {
        return `<mark>${escapeHtml(variable)}</mark>`;
      }
      if (comment) {
        return `<span class="syntax-comment">${escapeHtml(comment)}</span>`;
      }
      if (string) {
        return `<span class="syntax-string">${highlightVariables(string)}</span>`;
      }
      if (number) {
        return `<span class="syntax-number">${escapeHtml(number)}</span>`;
      }
      if (keyword) {
        return `<span class="syntax-literal">${escapeHtml(keyword)}</span>`;
      }
      return escapeHtml(match);
    },
  );
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (char) => {
    const entity: Record<string, string> = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    };
    return entity[char];
  });
}
