<template>
  <a-modal
    :open="open"
    :title="`编辑章节：${title}`"
    :width="editorWidth"
    ok-text="保存章节"
    cancel-text="取消"
    :confirm-loading="saving"
    @ok="handleOk"
    @cancel="$emit('update:open', false)"
  >
    <MdEditor
      v-model="draft"
      class="plan-md section-md-editor"
      :theme="mdTheme"
      :preview="false"
      :toolbars="TOOLBARS"
      :footers="['markdownTotal']"
      :placeholder="`输入「${title}」章节内容（Markdown）…`"
      :style="{ height: editorHeight }"
      language="zh-CN"
    />
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { MdEditor } from 'md-editor-v3';
import type { ToolbarNames } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { useTheme } from '../../composables/useTheme';

/**
 * 单章正文编辑：弹窗内只保留编辑单栏（Pretty 视图本身即实时预览），
 * 工具栏精简为一行可完整显示；保存走全文链路（revision 冲突保护在父级）。
 */
const TOOLBARS: ToolbarNames[] = [
  'bold', 'italic', 'underline', 'strikeThrough', '-',
  'title', '-',
  'quote', 'unorderedList', 'orderedList', 'task', '-',
  'codeRow', 'code', 'link', 'table', 'image', '-',
  'revoke', 'next',
];

const props = defineProps<{ open: boolean; title: string; content: string }>();
const emit = defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'save', content: string): void }>();

const { themeMode } = useTheme();
const mdTheme = computed(() => (themeMode.value === 'dark' ? 'dark' : 'light'));

const draft = ref('');
const saving = ref(false);

const editorWidth = computed(() => `${Math.min(1120, Math.round(window.innerWidth * 0.92))}px`);
const editorHeight = computed(() => `${Math.round(Math.min(window.innerHeight * 0.62, 580))}px`);

watch(() => props.open, (open) => {
  if (open) draft.value = props.content;
});

function handleOk() {
  saving.value = true;
  emit('save', draft.value);
  saving.value = false;
  emit('update:open', false);
}
</script>

<style scoped>
/* 编辑单栏铺满弹窗内容区，去默认边框（弹窗自身已有边界） */
.section-md-editor {
  border: 0;
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px var(--line);
}
</style>
