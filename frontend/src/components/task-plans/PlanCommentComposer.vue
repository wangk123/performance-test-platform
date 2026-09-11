<template>
  <div class="anno-composer" @keydown.esc.stop="emit('cancel')">
    <textarea
      ref="inputRef"
      v-model="draft"
      class="anno-composer-input"
      :placeholder="placeholder"
      rows="3"
      @keydown.enter.exact.prevent="submit"
      @keydown.ctrl.enter.prevent="submit"
      @keydown.meta.enter.prevent="submit"
    />
    <div class="anno-composer-actions">
      <a-tooltip title="取消（Esc）">
        <button type="button" class="anno-ibtn" aria-label="取消" @click="emit('cancel')">
          <CloseOutlined />
        </button>
      </a-tooltip>
      <a-tooltip title="发送（Ctrl/⌘+Enter）">
        <button
          type="button" class="anno-ibtn anno-ibtn-send" aria-label="发送"
          :disabled="!draft.trim() || busy"
          @click="submit"
        >
          <SendOutlined />
        </button>
      </a-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';
import { CloseOutlined, SendOutlined } from '@ant-design/icons-vue';

const props = withDefaults(defineProps<{
  placeholder?: string;
  busy?: boolean;
  /** 预填内容（编辑批注时传入原文）。 */
  initial?: string;
}>(), { placeholder: '针对此行添加批注（评审中全员可见）' });
const emit = defineEmits<{ (e: 'submit', content: string): void; (e: 'cancel'): void }>();

const draft = ref(props.initial ?? '');
const inputRef = ref<HTMLTextAreaElement | null>(null);

function submit() {
  const content = draft.value.trim();
  if (!content || props.busy) return;
  emit('submit', content);
}

onMounted(() => void nextTick(() => inputRef.value?.focus()));
</script>
