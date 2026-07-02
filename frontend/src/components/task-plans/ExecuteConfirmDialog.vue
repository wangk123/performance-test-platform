<template>
  <a-modal
    v-model:open="visible"
    title="确认执行"
    width="480px"
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
      <a-form-item label="线程组配置（选填）">
        <a-radio-group v-model:value="selectedConfigId" class="execute-config-group">
          <a-radio :value="null">使用脚本默认配置</a-radio>
          <a-radio
            v-for="config in scenario?.threadGroupConfigs ?? []"
            :key="config.id"
            :value="config.id"
          >
            {{ threadGroupConfigLabel(config) }}
          </a-radio>
        </a-radio-group>
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
import { threadGroupConfigLabel } from '../../utils/scenario-thread-group';

const props = defineProps<{ modelValue: boolean; scenario: TaskScenario | null }>();
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'confirm', payload: { executionName?: string; threadGroupConfigId?: number | null }): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

const executionName = ref('');
const selectedConfigId = ref<number | null>(null);

watch(() => props.modelValue, (val) => {
  if (val) {
    executionName.value = '';
    selectedConfigId.value = null;
  }
});

function onConfirm() {
  emit('confirm', {
    executionName: executionName.value.trim() || undefined,
    threadGroupConfigId: selectedConfigId.value,
  });
  visible.value = false;
}
</script>

<style scoped>
.execute-config-group {
  display: grid;
  gap: 8px;
}
</style>
