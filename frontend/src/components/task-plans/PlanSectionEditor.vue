<template>
  <a-modal
    :open="open"
    :title="`编辑章节：${title}`"
    :width="editorWidth"
    ok-text="保存章节"
    cancel-text="取消"
    :confirm-loading="saving"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <template #footer>
      <template v-if="compareMode">
        <a-button @click="discardPolish">放弃润色</a-button>
        <a-button type="primary" @click="adoptPolish">采纳润色</a-button>
      </template>
      <template v-else>
        <a-button @click="handleCancel">取消</a-button>
        <a-button type="primary" :loading="saving" @click="handleOk">保存章节</a-button>
      </template>
    </template>

    <div v-if="!compareMode" class="polish-bar">
      <span class="polish-hint">
        {{ polishing ? 'AI 润色中，通常需要十几秒到一分钟…' : 'AI 仅优化表达，结构与数字保持不变；采纳后仍需「保存章节」才生效' }}
      </span>
      <a-button
        size="small"
        :loading="polishing"
        :disabled="saving"
        @click="handlePolish"
      >
        ✨ AI 润色
      </a-button>
    </div>

    <MdEditor
      v-if="!compareMode"
      v-model="draft"
      class="plan-md section-md-editor"
      :theme="mdTheme"
      :preview="false"
      :toolbars="TOOLBARS"
      :footers="['markdownTotal']"
      :placeholder="`输入「${title}」章节内容（Markdown）…`"
      :style="{ height: editorHeight }"
      :disabled="polishing"
      language="zh-CN"
    />

    <div v-else class="polish-compare" :style="{ height: editorHeight }">
      <div class="compare-pane">
        <div class="pane-tag">当前草稿</div>
        <div class="pane-body">
          <MdPreview
            editor-id="polish-base-preview"
            class="plan-md"
            :model-value="draft"
            :theme="mdTheme"
            language="zh-CN"
          />
        </div>
      </div>
      <div class="compare-pane">
        <div class="pane-tag ai">AI 润色稿</div>
        <div class="pane-body">
          <MdPreview
            editor-id="polish-ai-preview"
            class="plan-md"
            :model-value="polishedContent"
            :theme="mdTheme"
            language="zh-CN"
          />
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { MdEditor, MdPreview } from 'md-editor-v3';
import type { ToolbarNames } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { useTheme } from '../../composables/useTheme';
import { polishPlanSectionApi } from '../../api/plan-doc';

/**
 * 单章正文编辑：弹窗内只保留编辑单栏（Pretty 视图本身即实时预览），
 * 工具栏精简为一行可完整显示；保存走全文链路（revision 冲突保护在父级）。
 * AI 润色：整章发给后端（平台默认模型），结果在弹窗内左右对比，采纳后才写入草稿。
 */
const TOOLBARS: ToolbarNames[] = [
  'bold', 'italic', 'underline', 'strikeThrough', '-',
  'title', '-',
  'quote', 'unorderedList', 'orderedList', 'task', '-',
  'codeRow', 'code', 'link', 'table', 'image', '-',
  'revoke', 'next',
];

const props = defineProps<{ open: boolean; planId: number; title: string; content: string }>();
const emit = defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'save', content: string): void }>();

const { themeMode } = useTheme();
const mdTheme = computed(() => (themeMode.value === 'dark' ? 'dark' : 'light'));

const draft = ref('');
const saving = ref(false);
const polishing = ref(false);
const compareMode = ref(false);
const polishedContent = ref('');

const editorWidth = computed(() => `${Math.min(1120, Math.round(window.innerWidth * 0.92))}px`);
const editorHeight = computed(() => `${Math.round(Math.min(window.innerHeight * 0.62, 580))}px`);

watch(() => props.open, (open) => {
  if (open) {
    draft.value = props.content;
    polishing.value = false;
    compareMode.value = false;
    polishedContent.value = '';
  }
});

function resetPolishState() {
  polishing.value = false;
  compareMode.value = false;
  polishedContent.value = '';
}

function handleCancel() {
  // 润色等待中允许关闭；过期响应在返回时被丢弃
  emit('update:open', false);
  resetPolishState();
}

function handleOk() {
  saving.value = true;
  emit('save', draft.value);
  saving.value = false;
  emit('update:open', false);
}

async function handlePolish() {
  if (polishing.value) return;
  polishing.value = true;
  try {
    const result = await polishPlanSectionApi(props.planId, props.title, draft.value);
    if (!props.open) return; // 弹窗已关闭，丢弃过期结果
    polishedContent.value = result.content;
    compareMode.value = true;
  } catch (error) {
    message.error(error instanceof Error ? error.message : 'AI 润色失败，请稍后重试');
  } finally {
    polishing.value = false;
  }
}

function adoptPolish() {
  draft.value = polishedContent.value;
  resetPolishState();
  message.success('已采纳润色结果，点击「保存章节」后生效');
}

function discardPolish() {
  resetPolishState();
}
</script>

<style scoped>
.polish-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.polish-hint {
  font-size: 12px;
  color: var(--muted, #8a8f99);
}

.polish-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  min-height: 320px;
}

.compare-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid var(--line, #e2e6ee);
  border-radius: 8px;
  overflow: hidden;
}

.pane-tag {
  flex: none;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--muted, #8a8f99);
  border-bottom: 1px solid var(--line, #e2e6ee);
  background: var(--surface-2, #f7f8fa);
}

.pane-tag.ai {
  color: #1677ff;
}

.pane-body {
  flex: 1;
  overflow: auto;
  padding: 4px 12px;
}

/* 编辑单栏铺满弹窗内容区，去默认边框（弹窗自身已有边界） */
.section-md-editor {
  border: 0;
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px var(--line);
}
</style>
