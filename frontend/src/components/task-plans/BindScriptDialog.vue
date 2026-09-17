<template>
  <a-modal
    :open="open"
    :title="`关联脚本：${scenarioName}`"
    ok-text="关联"
    cancel-text="取消"
    :ok-button-props="{ disabled: !resolvedId }"
    @ok="handleOk"
    @cancel="close"
  >
    <p class="bind-hint">选择项目内脚本版本（评审通过后编写并上传），或直接输入脚本版本 ID。</p>
    <a-form layout="vertical">
      <a-form-item label="项目脚本版本">
        <a-select
          v-model:value="selectedId"
          placeholder="搜索并选择脚本"
          show-search
          allow-clear
          :options="scriptOptions"
          option-filter-prop="label"
        />
      </a-form-item>
      <a-form-item label="或输入脚本版本 ID">
        <a-input-number
          v-model:value="manualId"
          :min="1"
          :precision="0"
          placeholder="手动输入 ID 兜底"
          style="width: 100%"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { ScriptAsset } from '../../types';

const props = defineProps<{ open: boolean; scenarioName: string; scripts: ScriptAsset[] }>();
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'confirm', scriptVersionId: number): void;
}>();

const selectedId = ref<number | null>(null);
const manualId = ref<number | null>(null);

watch(() => props.open, (open) => {
  if (open) {
    selectedId.value = null;
    manualId.value = null;
  }
});

/** 每脚本展开其已发布版本（草稿不可绑定、不可执行）。 */
const scriptOptions = computed(() =>
  props.scripts.flatMap((script) =>
    script.versions
      .filter((version) => version.status === 'PUBLISHED')
      .map((version) => ({
        value: version.id,
        label: `${script.name} · ${version.versionLabel || `v${version.versionNo}`}${version.remark ? `（${version.remark.slice(0, 20)}）` : ''}`,
      })),
  ),
);

/** 手输 ID 优先，其次下拉选择。 */
const resolvedId = computed(() => manualId.value ?? selectedId.value ?? null);

function handleOk() {
  if (!resolvedId.value) return;
  emit('confirm', resolvedId.value);
  close();
}

function close() {
  emit('update:open', false);
}
</script>

<style scoped>
.bind-hint {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 12px;
}
</style>
