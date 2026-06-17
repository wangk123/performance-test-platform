<template>
  <a-modal v-model:open="visible" :title="editingTask ? '编辑任务' : '新建任务'" width="720px" destroy-on-close>
    <div class="task-config-dialog">
      <a-form layout="vertical">
        <a-form-item label="任务名称">
          <a-input v-model:value="form.name" placeholder="请输入任务名称" />
        </a-form-item>

        <a-form-item label="选择脚本">
          <div class="script-choice-list">
            <button
              v-for="script in currentProjectScripts"
              :key="script.id"
              class="script-choice"
              :class="{ selected: form.scriptId === script.id }"
              type="button"
              @click="selectScript(script)"
            >
              <span>
                <strong>{{ script.name }}</strong>
                <small>{{ script.sourceFile }} · v{{ script.latestVersion }} · {{ getThreadGroupCount(script) }} 线程组 / {{ script.apis.length }} API / {{ script.monitors.length }} 监控</small>
              </span>
              <span class="asset-status">{{ form.scriptId === script.id ? '已选择' : '解析成功' }}</span>
            </button>
          </div>
        </a-form-item>

        <template v-if="selectedScriptThreadGroups.length > 0">
          <a-form-item label="线程组配置（取自脚本）">
            <div class="thread-group-summary-list">
              <div v-for="group in selectedScriptThreadGroups" :key="group.name" class="thread-group-summary-item">
                <strong>{{ group.name }}</strong>
                <span>{{ threadGroupSummary(group) }}</span>
              </div>
            </div>
          </a-form-item>
        </template>

        <template v-else-if="form.scriptId !== null">
          <a-alert title="所选脚本无线程组" description="请先在脚本编辑器中创建至少一个线程组。" type="warning" :closable="false" show-icon />
        </template>

        <div class="task-form-grid">
          <a-form-item label="目标环境">
            <a-select v-model:value="form.environment">
              <a-select-option label="SIT / 127.0.0.1" value="SIT / 127.0.0.1" />
              <a-select-option label="UAT / 10.12.4.18" value="UAT / 10.12.4.18" />
            </a-select>
          </a-form-item>
          <a-form-item label="任务优先级">
            <a-select v-model:value="form.priority">
              <a-select-option label="普通" value="普通" />
              <a-select-option label="高" value="高" />
            </a-select>
          </a-form-item>
        </div>

        <a-form-item label="备注">
          <a-input v-model:value="form.remark" placeholder="请输入备注" />
        </a-form-item>
      </a-form>
    </div>

    <template #footer>
      <div class="task-dialog-actions">
        <a-button class="task-dialog-button" @click="visible = false">取消</a-button>
        <a-button class="task-dialog-button" type="primary" :disabled="!canSave" @click="onSave">保存任务</a-button>
      </div>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import type { ScriptAsset, TestTask, ThreadGroup } from '../../types';
import { useWorkspace } from '../../composables/useWorkspace';
import { useTaskSchedule } from '../../composables/useTaskSchedule';
import { useThreadGroups } from '../../composables/useThreadGroups';

const props = defineProps<{
  modelValue: boolean;
  editingTask: TestTask | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
}>();

const { currentProjectScripts } = useWorkspace();
const { saveTask } = useTaskSchedule();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

const form = reactive({
  id: undefined as number | undefined,
  scriptId: null as number | null,
  name: '',
  environment: 'SIT / 127.0.0.1',
  priority: '普通',
  remark: '',
});

const selectedScript = computed<ScriptAsset | null>(() =>
  currentProjectScripts.value.find((item) => item.id === form.scriptId) ?? null,
);

const selectedScriptThreadGroups = computed(() => {
  const script = selectedScript.value;
  if (!script) {
    return [];
  }
  return useThreadGroups(() => script.steps).threadGroups.value;
});

const canSave = computed(() => form.scriptId !== null && selectedScriptThreadGroups.value.length > 0);

watch(
  () => [props.modelValue, props.editingTask, currentProjectScripts.value] as const,
  () => {
    if (!props.modelValue) {
      return;
    }
    const script = props.editingTask
      ? currentProjectScripts.value.find((item) => item.id === props.editingTask?.scriptId)
      : currentProjectScripts.value[0];
    form.id = props.editingTask?.id;
    form.scriptId = props.editingTask?.scriptId ?? script?.id ?? null;
    form.name = props.editingTask?.name ?? (script ? `${script.name} / 回归验证` : '');
    form.environment = props.editingTask?.environment ?? 'SIT / 127.0.0.1';
    form.priority = props.editingTask?.priority ?? '普通';
    form.remark = props.editingTask?.remark ?? '用于回归验证链路稳定性';
  },
  { immediate: true },
);

function selectScript(script: ScriptAsset) {
  form.scriptId = script.id;
  if (!props.editingTask) {
    form.name = `${script.name} / 回归验证`;
  }
}

function getThreadGroupCount(script: ScriptAsset): number {
  return useThreadGroups(() => script.steps).threadGroupCount.value;
}

function threadGroupSummary(group: ThreadGroup): string {
  if (group.mode === 'stepping') {
    return `${group.threads} 线程 · 阶梯加压 · 每阶 ${group.stepping?.startUsersCount ?? '-'} 用户`;
  }
  if (group.mode === 'duration' || group.scheduler) {
    return `${group.threads} 线程 · Ramp-Up ${group.rampUp}s · 持续 ${group.duration}s`;
  }
  return `${group.threads} 线程 · Ramp-Up ${group.rampUp}s · 循环 ${group.loops} 次`;
}

function onSave() {
  if (saveTask({ ...form })) {
    visible.value = false;
  }
}
</script>

<style scoped>
.thread-group-summary-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.thread-group-summary-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--surface-soft);
  border-radius: 6px;
  font-size: 13px;
}

.thread-group-summary-item strong {
  color: var(--text);
}

.thread-group-summary-item span {
  color: var(--muted);
}
</style>
