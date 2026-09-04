<template>
  <a-modal
    :open="open"
    :title="`编辑章节：${title}`"
    width="860px"
    ok-text="保存章节"
    :confirm-loading="saving"
    @ok="handleOk"
    @cancel="$emit('update:open', false)"
  >
    <MdEditor v-model="draft" :style="{ height: '420px' }" language="zh-CN" />
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { MdEditor } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';

const props = defineProps<{ open: boolean; title: string; content: string }>();
const emit = defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'save', content: string): void }>();

const draft = ref('');
const saving = ref(false);

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
