<template>
  <a-modal v-model:open="visible" :title="editingScenario ? '编辑场景' : '添加场景'" width="720px" destroy-on-close>
    <a-form layout="vertical">
      <a-form-item label="场景名称"><a-input v-model:value="form.name" /></a-form-item>
      <a-form-item label="选择脚本">
        <div class="script-choice-list">
          <button
            v-for="script in currentProjectScripts"
            :key="script.id"
            class="script-choice"
            :class="{ selected: form.scriptVersionId === script.id }"
            type="button"
            @click="form.scriptVersionId = script.id"
          >
            <span><strong>{{ script.name }}</strong><small>{{ script.sourceFile }}</small></span>
          </button>
        </div>
      </a-form-item>
      <div class="task-form-grid">
        <a-form-item label="线程数"><a-input-number v-model:value="form.threads" :min="1" /></a-form-item>
        <a-form-item label="Ramp-Up(s)"><a-input-number v-model:value="form.rampUp" :min="0" /></a-form-item>
        <a-form-item label="持续时间(s)"><a-input-number v-model:value="form.duration" :min="0" /></a-form-item>
        <a-form-item label="循环次数"><a-input-number v-model:value="form.loops" :min="0" /></a-form-item>
      </div>
      <a-form-item>
        <a-checkbox v-model:checked="form.overridePlanDefaults">覆盖计划默认节点与监控配置</a-checkbox>
      </a-form-item>
      <template v-if="form.overridePlanDefaults">
        <a-form-item label="Controller">
          <a-select v-model:value="form.controllerNodeId" :loading="loading">
            <a-select-option v-for="node in controllerNodes" :key="node.id" :value="node.id">{{ node.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="Worker">
          <a-select v-model:value="form.workerNodeIds" mode="multiple" :loading="loading">
            <a-select-option v-for="node in workerNodes" :key="node.id" :value="node.id">{{ node.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="监控目标">
          <a-select v-model:value="form.monitorTargetIds" mode="multiple" :loading="loadingMonitorTargets">
            <a-select-option v-for="target in selectableMonitorTargets" :key="target.id" :value="target.id">{{ target.name }}</a-select-option>
          </a-select>
        </a-form-item>
      </template>
    </a-form>
    <template #footer>
      <a-button @click="visible = false">取消</a-button>
      <a-button type="primary" :disabled="!canSave" @click="onSave">保存</a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import type { TaskPlan, TaskScenario } from '../../types';
import { useWorkspace } from '../../composables/useWorkspace';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { useExecutionNodes } from '../../composables/useExecutionNodes';
import { useMonitoring } from '../../composables/useMonitoring';

const props = defineProps<{ modelValue: boolean; plan: TaskPlan; editingScenario: TaskScenario | null }>();
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>();

const { currentProjectScripts } = useWorkspace();
const { saveScenario } = useTaskPlans();
const { controllerNodes, workerNodes, loading, loadNodes } = useExecutionNodes();
const { monitorTargets, loadingMonitorTargets, loadMonitorTargets } = useMonitoring();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

const form = reactive({
  id: undefined as number | undefined,
  name: '',
  scriptVersionId: null as number | null,
  threads: 1,
  rampUp: 0,
  duration: 0,
  loops: 1,
  overridePlanDefaults: false,
  controllerNodeId: null as number | null,
  workerNodeIds: [] as number[],
  monitorTargetIds: [] as number[],
});

const selectableMonitorTargets = computed(() => monitorTargets.value.filter((t) => t.enabled));
const canSave = computed(() => form.name.trim() && form.scriptVersionId !== null);

watch(() => [props.modelValue, props.editingScenario, props.plan] as const, async () => {
  if (!props.modelValue) return;
  void loadNodes();
  await loadMonitorTargets(props.plan.projectId);
  const script = props.editingScenario
    ? currentProjectScripts.value.find((s) => s.id === props.editingScenario?.scriptVersionId)
    : currentProjectScripts.value[0];
  form.id = props.editingScenario?.id;
  form.name = props.editingScenario?.name ?? (script ? `${script.name} 场景` : '');
  form.scriptVersionId = props.editingScenario?.scriptVersionId ?? script?.id ?? null;
  form.threads = props.editingScenario?.threads ?? 1;
  form.rampUp = props.editingScenario?.rampUp ?? 0;
  form.duration = props.editingScenario?.duration ?? 0;
  form.loops = props.editingScenario?.loops ?? 1;
  form.overridePlanDefaults = props.editingScenario?.controllerNodeId != null
    || (props.editingScenario?.workerNodeIds?.length ?? 0) > 0
    || (props.editingScenario?.monitorTargetIds?.length ?? 0) > 0;
  form.controllerNodeId = props.editingScenario?.controllerNodeId ?? props.plan.defaultControllerNodeId;
  form.workerNodeIds = [...(props.editingScenario?.workerNodeIds ?? props.plan.defaultWorkerNodeIds)];
  form.monitorTargetIds = [...(props.editingScenario?.monitorTargetIds ?? props.plan.defaultMonitorTargetIds)];
}, { immediate: true });

async function onSave() {
  if (!form.scriptVersionId) return;
  if (await saveScenario(props.plan.id, {
    id: form.id,
    name: form.name,
    scriptVersionId: form.scriptVersionId,
    threads: form.threads,
    rampUp: form.rampUp,
    duration: form.duration,
    loops: form.loops,
    overridePlanDefaults: form.overridePlanDefaults,
    controllerNodeId: form.overridePlanDefaults ? form.controllerNodeId : undefined,
    workerNodeIds: form.overridePlanDefaults ? form.workerNodeIds : undefined,
    monitorTargetIds: form.overridePlanDefaults ? form.monitorTargetIds : undefined,
  })) {
    visible.value = false;
  }
}
</script>
