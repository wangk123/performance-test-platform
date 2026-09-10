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
      <span class="anno-composer-hint">Ctrl/⌘+Enter 提交 · Esc 取消</span>
      <a-button size="small" @click="emit('cancel')">取消</a-button>
      <a-button size="small" type="primary" :disabled="!draft.trim()" :loading="busy" @click="submit">提交</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';

const props = withDefaults(defineProps<{
  placeholder?: string;
  busy?: boolean;
}>(), { placeholder: '针对此行添加批注（评审中全员可见）' });
const emit = defineEmits<{ (e: 'submit', content: string): void; (e: 'cancel'): void }>();

const draft = ref('');
const inputRef = ref<HTMLTextAreaElement | null>(null);

function submit() {
  const content = draft.value.trim();
  if (!content || props.busy) return;
  emit('submit', content);
}

onMounted(() => void nextTick(() => inputRef.value?.focus()));
</script>
