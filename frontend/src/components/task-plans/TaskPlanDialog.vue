<template>
  <a-modal v-model:open="visible" :title="editingPlan ? '编辑任务计划' : '新建任务计划'" width="640px" destroy-on-close>
    <a-form layout="vertical">
      <a-form-item label="计划名称"><a-input v-model:value="form.name" /></a-form-item>
      <a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item>
      <a-form-item label="默认 Controller">
        <a-select v-model:value="form.controllerNodeId" :loading="loading" placeholder="选择 Controller">
          <a-select-option v-for="node in controllerNodes" :key="node.id" :value="node.id">{{ node.name }} / {{ node.host }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="默认 Worker">
        <a-select v-model:value="form.workerNodeIds" :loading="loading" mode="multiple" placeholder="不选则使用 Controller">
          <a-select-option v-for="node in workerNodes" :key="node.id" :value="node.id">{{ node.name }} / {{ node.host }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="默认监控目标">
        <a-select v-model:value="form.monitorTargetIds" :loading="loadingMonitorTargets" mode="multiple">
          <a-select-option v-for="target in selectableMonitorTargets" :key="target.id" :value="target.id">
            {{ target.serviceName }} / {{ target.env }}
          </a-select-option>
        </a-select>
      </a-form-item>
    </a-form>
    <template #footer>
      <a-button @click="visible = false">取消</a-button>
      <a-button type="primary" :disabled="!form.name.trim()" @click="onSave">保存</a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import type { TaskPlan } from '../../types';
import { useWorkspace } from '../../composables/useWorkspace';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { useExecutionNodes } from '../../composables/useExecutionNodes';
import { useMonitoring } from '../../composables/useMonitoring';

const props = defineProps<{ modelValue: boolean; editingPlan: TaskPlan | null }>();
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>();

const { currentProject } = useWorkspace();
const { savePlan } = useTaskPlans();
const { controllerNodes, workerNodes, loading, loadNodes } = useExecutionNodes();
const { monitorTargets, loadingMonitorTargets, loadMonitorTargets } = useMonitoring();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

const form = reactive({
  id: undefined as number | undefined,
  name: '',
  remark: '',
  controllerNodeId: null as number | null,
  workerNodeIds: [] as number[],
  monitorTargetIds: [] as number[],
});

const selectableMonitorTargets = computed(() => {
  if (!currentProject.value) return [];
  return monitorTargets.value.filter((t) => t.projectId === currentProject.value?.id && t.enabled);
});

watch(() => [props.modelValue, props.editingPlan] as const, async () => {
  if (!props.modelValue) return;
  void loadNodes();
  if (currentProject.value) await loadMonitorTargets(currentProject.value.id);
  form.id = props.editingPlan?.id;
  form.name = props.editingPlan?.name ?? '';
  form.remark = props.editingPlan?.remark ?? '';
  form.controllerNodeId = props.editingPlan?.defaultControllerNodeId ?? null;
  form.workerNodeIds = [...(props.editingPlan?.defaultWorkerNodeIds ?? [])];
  form.monitorTargetIds = [...(props.editingPlan?.defaultMonitorTargetIds ?? [])];
}, { immediate: true });

async function onSave() {
  if (await savePlan({ ...form })) visible.value = false;
}
</script>
