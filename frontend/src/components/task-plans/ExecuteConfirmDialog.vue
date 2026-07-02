<template>
  <a-modal
    v-model:open="visible"
    title="确认执行"
    width="560px"
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
        <a-radio-group v-model:value="selectedPresetSortOrder" class="execute-config-group">
          <a-radio :value="null" class="execute-preset-option">
            <span class="execute-preset-title">使用脚本默认配置</span>
          </a-radio>
          <a-radio
            v-for="(group, index) in presetGroups"
            :key="group.sortOrder"
            :value="group.sortOrder"
            class="execute-preset-option"
          >
            <span class="execute-preset-content">
              <span class="execute-preset-title">配置 {{ index + 1 }}</span>
              <span class="execute-preset-detail">{{ executePresetDetail(group) }}</span>
              <span class="execute-preset-total">共 {{ sumPresetThreads(group.rows) }} 线程</span>
            </span>
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
import {
  executePresetDetail,
  groupStoredThreadGroupConfigs,
  presetRepresentativeConfigId,
  sumPresetThreads,
} from '../../utils/scenario-thread-group';

const props = defineProps<{ modelValue: boolean; scenario: TaskScenario | null }>();
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'confirm', payload: {
    executionName?: string;
    threadGroupConfigId?: number | null;
    threadGroupPresetSortOrder?: number | null;
  }): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

const executionName = ref('');
const selectedPresetSortOrder = ref<number | null>(null);

const presetGroups = computed(() => groupStoredThreadGroupConfigs(props.scenario?.threadGroupConfigs ?? []));

watch(() => props.modelValue, (val) => {
  if (val) {
    executionName.value = '';
    selectedPresetSortOrder.value = null;
  }
});

function onConfirm() {
  const selectedGroup = presetGroups.value.find((group) => group.sortOrder === selectedPresetSortOrder.value);
  emit('confirm', {
    executionName: executionName.value.trim() || undefined,
    threadGroupPresetSortOrder: selectedPresetSortOrder.value,
    threadGroupConfigId: selectedGroup ? presetRepresentativeConfigId(selectedGroup) : null,
  });
  visible.value = false;
}
</script>

<style scoped>
.execute-config-group {
  display: grid;
  gap: 8px;
  width: 100%;
}

.execute-config-group :deep(.execute-preset-option) {
  display: flex;
  align-items: flex-start;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
}

.execute-config-group :deep(.execute-preset-option.ant-radio-wrapper-checked) {
  border-color: var(--primary);
  background: var(--active-bg);
}

.execute-preset-content {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.execute-preset-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.execute-preset-detail {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.execute-preset-total {
  color: var(--primary-dark);
  font-size: 12px;
  font-weight: 500;
}
</style>
