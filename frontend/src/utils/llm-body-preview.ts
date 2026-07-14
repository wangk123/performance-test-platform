export type LlmBodyFormatMode = 'auto' | 'json' | 'markdown';

export type LlmBodyPreview = {
  text: string;
  mode: 'json' | 'markdown' | 'raw';
};

export function extractLlmContent(raw: string): string | null {
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (typeof parsed === 'string') {
      return parsed;
    }
    if (!parsed || typeof parsed !== 'object') {
      return null;
    }
    const obj = parsed as Record<string, unknown>;
    const choices = obj.choices;
    if (Array.isArray(choices) && choices[0] && typeof choices[0] === 'object') {
      const message = (choices[0] as { message?: { content?: unknown } }).message;
      if (message && typeof message.content === 'string') {
        return message.content;
      }
    }
    const messages = obj.messages;
    if (Array.isArray(messages)) {
      const texts = messages
        .map((item) => {
          if (!item || typeof item !== 'object') return '';
          const content = (item as { content?: unknown }).content;
          return typeof content === 'string' ? content : '';
        })
        .filter(Boolean);
      if (texts.length) {
        return texts.join('\n\n');
      }
    }
    const content = obj.content;
    if (typeof content === 'string') {
      return content;
    }
    if (Array.isArray(content)) {
      const texts = content
        .map((block) => {
          if (!block || typeof block !== 'object') return '';
          const item = block as { type?: string; text?: string };
          return item.type === 'text' && typeof item.text === 'string' ? item.text : '';
        })
        .filter(Boolean);
      if (texts.length) {
        return texts.join('\n\n');
      }
    }
    return null;
  } catch {
    return null;
  }
}

export function formatLlmBody(raw: string | null | undefined, mode: LlmBodyFormatMode): LlmBodyPreview {
  if (raw == null || raw === '') {
    return { text: '—', mode: 'raw' };
  }
  if (mode === 'markdown') {
    const content = extractLlmContent(raw);
    return { text: content ?? raw, mode: 'markdown' };
  }
  if (mode === 'json') {
    try {
      return { text: JSON.stringify(JSON.parse(raw), null, 2), mode: 'json' };
    } catch {
      return { text: raw, mode: 'raw' };
    }
  }
  try {
    return { text: JSON.stringify(JSON.parse(raw), null, 2), mode: 'json' };
  } catch {
    return { text: raw, mode: 'raw' };
  }
}
