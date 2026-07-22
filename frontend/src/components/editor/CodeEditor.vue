<template>
  <div ref="hostRef" class="code-editor" />
</template>

<script setup lang="ts">
import { Compartment, EditorState } from '@codemirror/state';
import { basicSetup } from 'codemirror';
import { Decoration, EditorView, MatchDecorator, ViewPlugin, placeholder, type DecorationSet, type ViewUpdate } from '@codemirror/view';
import { json } from '@codemirror/lang-json';
import { xml } from '@codemirror/lang-xml';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useTheme } from '../../composables/useTheme';

const PLACEHOLDER_RE = /\$\{[\w.-]+(?:\([^}]*\))?\}/g;

const placeholderMatcher = new MatchDecorator({
  regexp: PLACEHOLDER_RE,
  decoration: Decoration.mark({ class: 'cm-var-placeholder' }),
});

const placeholderHighlighter = ViewPlugin.fromClass(
  class {
    decorations: DecorationSet;

    constructor(view: EditorView) {
      this.decorations = placeholderMatcher.createDeco(view);
    }

    update(update: ViewUpdate) {
      this.decorations = placeholderMatcher.updateDeco(update, this.decorations);
    }
  },
  { decorations: (value) => value.decorations },
);

const props = withDefaults(defineProps<{
  modelValue: string;
  language?: 'json' | 'xml' | 'html' | 'javascript' | 'text';
  placeholder?: string;
  readonly?: boolean;
  fieldId?: string;
}>(), {
  language: 'text',
  placeholder: '',
  readonly: false,
  fieldId: 'body',
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
  blur: [];
  active: [id: string, caret: number, value: string];
}>();

const hostRef = ref<HTMLDivElement | null>(null);
const { resolvedTheme } = useTheme();
const languageCompartment = new Compartment();
const themeCompartment = new Compartment();
let editorView: EditorView | null = null;

onMounted(() => {
  if (!hostRef.value) {
    return;
  }
  editorView = new EditorView({
    parent: hostRef.value,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        basicSetup,
        EditorView.lineWrapping,
        placeholder(props.placeholder),
        languageCompartment.of(languageExtension()),
        themeCompartment.of(editorTheme()),
        placeholderHighlighter,
        EditorState.readOnly.of(props.readonly),
        EditorView.updateListener.of((update) => {
          if (!props.readonly && update.docChanged) {
            emit('update:modelValue', update.state.doc.toString());
          }
          if (update.focusChanged && !update.view.hasFocus) {
            emit('blur');
          }
          if (update.view.hasFocus && (update.focusChanged || update.selectionSet || update.docChanged)) {
            const caret = update.state.selection.main.head;
            emit('active', props.fieldId, caret, update.state.doc.toString());
          }
        }),
      ],
    }),
  });
});

watch(() => props.modelValue, (value) => {
  if (!editorView || value === editorView.state.doc.toString()) {
    return;
  }
  editorView.dispatch({
    changes: { from: 0, to: editorView.state.doc.length, insert: value },
  });
});

watch(() => props.language, () => {
  editorView?.dispatch({
    effects: languageCompartment.reconfigure(languageExtension()),
  });
});

watch(resolvedTheme, () => {
  editorView?.dispatch({
    effects: themeCompartment.reconfigure(editorTheme()),
  });
});

onBeforeUnmount(() => {
  editorView?.destroy();
  editorView = null;
});

function insertAtCursor(text: string, replaceFrom?: number) {
  if (!editorView || props.readonly) {
    return;
  }
  const caret = editorView.state.selection.main.head;
  const from = replaceFrom ?? caret;
  let to = caret;
  // basicSetup auto-closes `{` → consume the trailing `}` left after `${…`
  if (replaceFrom !== undefined && editorView.state.doc.sliceString(caret, caret + 1) === '}') {
    to = caret + 1;
  }
  editorView.dispatch({
    changes: { from, to, insert: text },
    selection: { anchor: from + text.length },
  });
  editorView.focus();
  emit('active', props.fieldId, from + text.length, editorView.state.doc.toString());
}

function languageExtension() {
  if (props.language === 'json') {
    return json();
  }
  if (props.language === 'xml' || props.language === 'html') {
    return xml();
  }
  return [];
}

function editorTheme() {
  return EditorView.theme({
    '&': {
      height: '100%',
      color: 'var(--code-text)',
      backgroundColor: 'var(--code-bg)',
      fontSize: '13px',
      borderRadius: 'var(--radius)',
    },
    '.cm-scroller': {
      fontFamily: '"SFMono-Regular", "Menlo", "Consolas", monospace',
      lineHeight: '1.65',
    },
    '.cm-content': {
      caretColor: 'var(--primary)',
      minHeight: '100%',
      padding: '14px 0',
    },
    '.cm-gutters': {
      color: 'var(--code-muted)',
      backgroundColor: 'var(--code-gutter-bg)',
      borderRightColor: 'var(--border)',
    },
    '.cm-activeLine, .cm-activeLineGutter': {
      backgroundColor: 'var(--code-active-line)',
    },
    '.cm-selectionBackground, &.cm-focused .cm-selectionBackground': {
      backgroundColor: 'var(--code-selection)',
    },
    '&.cm-focused': {
      outline: '1px solid var(--primary)',
    },
    '.cm-var-placeholder': {
      color: '#1d4ed8',
      backgroundColor: 'transparent',
    },
  }, { dark: resolvedTheme.value === 'dark' });
}

defineExpose({ insertAtCursor });
</script>
