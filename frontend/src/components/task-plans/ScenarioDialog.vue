<template>
  <a-modal v-model:open="visible" :title="isEditing ? '编辑场景' : '添加场景'" width="720px" destroy-on-close>
    <a-form layout="vertical">
      <a-form-item :label="isEditing ? '场景名称' : '场景名称前缀'">
        <a-input v-model:value="form.name" :placeholder="isEditing ? '' : '留空则使用脚本名称'" />
      </a-form-item>
      <a-form-item label="选择脚本">
        <div class="script-card-list">
          <div
            v-for="script in currentProjectScripts"
            :key="script.id"
            class="script-card"
            :class="{ selected: isScriptSelected(script.id) }"
            @click="toggleScript(script.id)"
          >
            <a-checkbox
              :checked="isScriptSelected(script.id)"
              @click.stop="toggleScript(script.id)"
            />
            <span class="script-card-body">
              <strong>{{ script.name }}</strong>
              <small>{{ script.sourceFile }}</small>
            </span>
            <EditOutlined
              class="script-card-edit"
              @click.stop="openScriptEditor(script.id)"
            />
          </div>
        </div>
        <div v-if="selectedCount > 0 && !isEditing" class="script-card-hint">
          已选 {{ selectedCount }} 个脚本，将创建 {{ selectedCount }} 个场景
        </div>
      </a-form-item>
      <a-form-item v-if="activeScriptVersionId" label="线程组配置">
        <ScenarioThreadGroupConfigEditor
          :project-id="plan.projectId"
          :script-version-id="activeScriptVersionId"
          v-model="form.threadGroupConfigs"
        />
      </a-form-item>
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
      <a-button type="primary" :disabled="!canSave" :loading="saving" @click="onSave">保存</a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { EditOutlined } from '@ant-design/icons-vue';
import type { TaskPlan, TaskScenario, ScenarioThreadGroupConfig } from '../../types';
import { useWorkspace } from '../../composables/useWorkspace';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { useExecutionNodes } from '../../composables/useExecutionNodes';
import { useMonitoring } from '../../composables/useMonitoring';
import ScenarioThreadGroupConfigEditor from './ScenarioThreadGroupConfigEditor.vue';

const props = defineProps<{ modelValue: boolean; plan: TaskPlan; editingScenario: TaskScenario | null }>();
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>();

const router = useRouter();
const { currentProjectScripts } = useWorkspace();
const { saveScenario } = useTaskPlans();
const { controllerNodes, workerNodes, loading, loadNodes } = useExecutionNodes();
const { monitorTargets, loadingMonitorTargets, loadMonitorTargets } = useMonitoring();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

const isEditing = computed(() => props.editingScenario != null);
const saving = ref(false);

const form = reactive({
  id: undefined as number | undefined,
  name: '',
  selectedScriptIds: [] as number[],
  overridePlanDefaults: false,
  controllerNodeId: null as number | null,
  workerNodeIds: [] as number[],
  monitorTargetIds: [] as number[],
  threadGroupConfigs: [] as ScenarioThreadGroupConfig[],
});

const activeScriptVersionId = computed(() => (
  isEditing.value ? form.selectedScriptIds[0] ?? null : form.selectedScriptIds[0] ?? null
));

const selectableMonitorTargets = computed(() => monitorTargets.value.filter((t) => t.enabled));
const selectedCount = computed(() => form.selectedScriptIds.length);
const canSave = computed(() => form.selectedScriptIds.length > 0);

function isScriptSelected(scriptId: number) {
  return form.selectedScriptIds.includes(scriptId);
}

function toggleScript(scriptId: number) {
  if (isEditing.value) {
    form.selectedScriptIds = [scriptId];
  } else {
    const idx = form.selectedScriptIds.indexOf(scriptId);
    if (idx >= 0) {
      form.selectedScriptIds.splice(idx, 1);
    } else {
      form.selectedScriptIds.push(scriptId);
    }
  }
}

watch(() => [props.modelValue, props.editingScenario, props.plan] as const, async () => {
  if (!props.modelValue) return;
  void loadNodes();
  await loadMonitorTargets(props.plan.projectId);

  if (props.editingScenario) {
    const script = currentProjectScripts.value.find((s) => s.id === props.editingScenario?.scriptVersionId);
    form.id = props.editingScenario.id;
    form.name = props.editingScenario.name;
    form.selectedScriptIds = props.editingScenario.scriptVersionId ? [props.editingScenario.scriptVersionId] : [];
    form.overridePlanDefaults = props.editingScenario.controllerNodeId != null
      || (props.editingScenario.workerNodeIds?.length ?? 0) > 0
      || (props.editingScenario.monitorTargetIds?.length ?? 0) > 0;
    form.controllerNodeId = props.editingScenario.controllerNodeId ?? props.plan.defaultControllerNodeId;
    form.workerNodeIds = [...(props.editingScenario.workerNodeIds ?? props.plan.defaultWorkerNodeIds)];
    form.monitorTargetIds = [...(props.editingScenario.monitorTargetIds ?? props.plan.defaultMonitorTargetIds)];
    form.threadGroupConfigs = [...(props.editingScenario.threadGroupConfigs ?? [])];
  } else {
    form.id = undefined;
    form.name = '';
    form.selectedScriptIds = [];
    form.overridePlanDefaults = false;
    form.controllerNodeId = props.plan.defaultControllerNodeId;
    form.workerNodeIds = [...props.plan.defaultWorkerNodeIds];
    form.monitorTargetIds = [...props.plan.defaultMonitorTargetIds];
    form.threadGroupConfigs = [];
  }
}, { immediate: true });

function openScriptEditor(scriptId: number) {
  const route = router.resolve(`/projects/${props.plan.projectId}/scripts/${scriptId}/edit`);
  window.open(route.href, '_blank');
}

async function onSave() {
  if (form.selectedScriptIds.length === 0) return;
  saving.value = true;
  try {
    for (const scriptId of form.selectedScriptIds) {
      const script = currentProjectScripts.value.find((s) => s.id === scriptId);
      const scenarioName = isEditing.value
        ? form.name
        : (form.name.trim() ? `${form.name.trim()} ${script?.name ?? ''}` : `${script?.name ?? ''} 场景`);
      const success = await saveScenario(props.plan.id, {
        id: isEditing.value ? form.id : undefined,
        name: scenarioName,
        scriptVersionId: scriptId,
        threadGroupConfigs: isEditing.value || scriptId === form.selectedScriptIds[0]
          ? form.threadGroupConfigs.map((item, sortOrder) => ({ ...item, sortOrder }))
          : [],
        overridePlanDefaults: form.overridePlanDefaults,
        controllerNodeId: form.overridePlanDefaults ? form.controllerNodeId : undefined,
        workerNodeIds: form.overridePlanDefaults ? form.workerNodeIds : undefined,
        monitorTargetIds: form.overridePlanDefaults ? form.monitorTargetIds : undefined,
      });
      if (!success) break;
    }
    visible.value = false;
  } finally {
    saving.value = false;
  }
}
</script>
