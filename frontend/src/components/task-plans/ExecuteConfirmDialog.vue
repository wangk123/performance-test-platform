<template>
  <a-modal
    v-model:open="visible"
    title="确认执行"
    width="440px"
    :destroy-on-close="true"
  >
    <div class="execute-confirm-body">
      <div class="execute-confirm-info">
        <span class="execute-confirm-label">场景</span>
        <strong>{{ scenario?.name }}</strong>
      </div>
      <a-form-item label="执行名称（选填）">
        <a-input
          v-model:value="executionName"
          placeholder="用于标识本次执行，留空则使用执行时间"
          :maxlength="200"
          @keydown.enter="onConfirm"
        />
      </a-form-item>
    </div>
    <template #footer>
      <a-button @click="visible = false">取消</a-button>
      <a-button type="primary" @click="onConfirm">确认执行</a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { TaskScenario } from '../../types';

const props = defineProps<{ modelValue: boolean; scenario: TaskScenario | null }>();
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'confirm', executionName: string): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

const executionName = ref('');

watch(() => props.modelValue, (val) => {
  if (val) executionName.value = '';
});

function onConfirm() {
  emit('confirm', executionName.value.trim());
  visible.value = false;
}
</script>
