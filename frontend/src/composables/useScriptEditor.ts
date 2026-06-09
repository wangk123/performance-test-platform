import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import type {
  FlatStepItem,
  ScriptAsset,
  ScriptStep,
  ScriptStepType,
  StepDropMode,
  StepRelation,
} from '../types';
import { stepTypeOptions } from '../constants';
import {
  appendChildStep,
  canAddChildStep,
  canMoveStep,
  createStepFromType,
  findStepById,
  flattenScriptSteps,
  insertStepRelative,
  removeStepById,
  takeStepById,
} from '../utils/script-steps';
import { useWorkspace } from './useWorkspace';
import { useAuth } from './useAuth';
import { mapScriptDefinition, saveScriptDefinitionApi } from '../api/scripts';

const editorScriptId = ref<number | null>(null);
const selectedEditorStepId = ref<string | null>(null);
const collapsedStepIds = ref<string[]>([]);
const draggingStepId = ref<string | null>(null);
const dragOverStepId = ref<string | null>(null);
const dragOverMode = ref<StepDropMode | null>(null);

const stepDialogVisible = ref(false);
const stepDialogTargetId = ref<string | null>(null);
const stepDialogForm = ref<{
  relation: StepRelation;
  type: ScriptStepType;
  name: string;
}>({
  relation: 'child',
  type: 'HTTP_REQUEST',
  name: '',
});

let initialized = false;

function ensureInit() {
  if (initialized) {
    return;
  }
  initialized = true;
  const { scriptAssets } = useWorkspace();

  const editorScriptAsset = computed(
    () => scriptAssets.value.find((script) => script.id === editorScriptId.value) ?? null,
  );

  watch(editorScriptAsset, (script) => {
    if (!script) {
      selectedEditorStepId.value = null;
      collapsedStepIds.value = [];
      return;
    }
    if (!findStepById(script.steps, selectedEditorStepId.value ?? '')) {
      selectedEditorStepId.value = script.steps[0]?.id ?? null;
    }
  });

  watch(
    () => stepDialogForm.value.relation,
    () => {
      const options = availableStepTypeOptions();
      if (!options.some((option) => option.value === stepDialogForm.value.type)) {
        stepDialogForm.value.type = options[0]?.value ?? 'HTTP_REQUEST';
      }
    },
  );
}

function availableStepTypeOptions() {
  if (stepDialogForm.value.relation === 'root') {
    return stepTypeOptions.filter((option) => option.value === 'THREAD_GROUP');
  }
  return stepTypeOptions.filter((option) => option.value !== 'THREAD_GROUP');
}

function useEditor() {
  ensureInit();
  const { scriptAssets } = useWorkspace();
  const route = useRoute();
  const router = useRouter();

  const editorScriptAsset = computed(
    () => scriptAssets.value.find((script) => script.id === editorScriptId.value) ?? null,
  );

  const scriptEditorVisible = computed(() => editorScriptId.value !== null);

  const flatEditorSteps = computed<FlatStepItem[]>(() =>
    flattenScriptSteps(editorScriptAsset.value?.steps ?? [], collapsedStepIds.value),
  );

  const selectedEditorStep = computed(() => {
    if (!editorScriptAsset.value || !selectedEditorStepId.value) {
      return null;
    }
    return findStepById(editorScriptAsset.value.steps, selectedEditorStepId.value);
  });

  const stepDialogTitle = computed(() =>
    stepDialogForm.value.relation === 'root' ? '新建线程组' : '新增子级步骤',
  );

  function isRootStep(stepId: string) {
    return editorScriptAsset.value?.steps.some((step) => step.id === stepId) ?? false;
  }

  function ensureScriptSteps(script: ScriptAsset) {
    if (!script.steps || script.steps.length === 0) {
      const created: ScriptStep[] = [];
      script.steps = created;
    }
  }

  function selectEditorStep(stepId: string) {
    selectedEditorStepId.value = stepId;
  }

  function isStepCollapsed(stepId: string) {
    return collapsedStepIds.value.includes(stepId);
  }

  function toggleStepCollapsed(stepId: string) {
    if (isStepCollapsed(stepId)) {
      expandStep(stepId);
    } else {
      collapsedStepIds.value = [...collapsedStepIds.value, stepId];
    }
  }

  function expandStep(stepId: string) {
    collapsedStepIds.value = collapsedStepIds.value.filter((id) => id !== stepId);
  }

  function canAddChildStepTo(parentId: string) {
    if (!editorScriptAsset.value) {
      return false;
    }
    return canAddChildStep(editorScriptAsset.value.steps, parentId);
  }

  function openStepDialog(relation: StepRelation, targetId = selectedEditorStepId.value) {
    const parentId =
      relation === 'root' ? null : (targetId ?? editorScriptAsset.value?.steps[0]?.id ?? null);
    if (relation === 'child' && parentId && !canAddChildStepTo(parentId)) {
      ElMessage.warning('步骤层级最多 3 级，无法继续新增子级');
      return;
    }
    stepDialogTargetId.value = parentId;
    stepDialogForm.value.relation = relation;
    stepDialogForm.value.type = relation === 'root' ? 'THREAD_GROUP' : 'HTTP_REQUEST';
    const options = availableStepTypeOptions();
    if (!options.some((option) => option.value === stepDialogForm.value.type)) {
      stepDialogForm.value.type = options[0]?.value ?? 'HTTP_REQUEST';
    }
    stepDialogForm.value.name = '';
    stepDialogVisible.value = true;
  }

  function createStep() {
    if (!editorScriptAsset.value) {
      return;
    }
    const newStep = createStepFromType(stepDialogForm.value.type, stepDialogForm.value.name);
    const selectedId = stepDialogTargetId.value ?? selectedEditorStepId.value;

    if (stepDialogForm.value.relation === 'root') {
      editorScriptAsset.value.steps.push(newStep);
    } else if (!selectedId || stepDialogForm.value.relation === 'child') {
      const parent = selectedId ? findStepById(editorScriptAsset.value.steps, selectedId) : null;
      if (parent) {
        if (!canAddChildStep(editorScriptAsset.value.steps, parent.id)) {
          ElMessage.warning('步骤层级最多 3 级，无法继续新增子级');
          return;
        }
        parent.children.push(newStep);
        expandStep(parent.id);
      } else {
        editorScriptAsset.value.steps.push(newStep);
      }
    }

    selectedEditorStepId.value = newStep.id;
    editorScriptAsset.value.updatedAt = new Date().toISOString();
    stepDialogTargetId.value = null;
    stepDialogVisible.value = false;
  }

  async function confirmDeleteStep(stepId: string) {
    const step = editorScriptAsset.value ? findStepById(editorScriptAsset.value.steps, stepId) : null;
    if (!editorScriptAsset.value || !step) {
      return;
    }
    try {
      await ElMessageBox.confirm(`确认删除步骤「${step.name}」？其下级步骤也会一起移除。`, '删除步骤', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      });
    } catch {
      return;
    }
    deleteStepById(stepId);
  }

  function deleteStepById(stepId: string) {
    if (!editorScriptAsset.value) {
      return;
    }
    const removed = removeStepById(editorScriptAsset.value.steps, stepId);
    if (removed) {
      if (
        selectedEditorStepId.value === stepId ||
        !findStepById(editorScriptAsset.value.steps, selectedEditorStepId.value ?? '')
      ) {
        selectedEditorStepId.value = editorScriptAsset.value.steps[0]?.id ?? null;
      }
      collapsedStepIds.value = collapsedStepIds.value.filter((id) => id !== stepId);
      editorScriptAsset.value.updatedAt = new Date().toISOString();
      ElMessage.success('步骤已删除');
    }
  }

  function startStepDrag(stepId: string, event: DragEvent) {
    draggingStepId.value = stepId;
    event.dataTransfer?.setData('text/plain', stepId);
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
    }
  }

  function resolveStepDropMode(event: DragEvent): StepDropMode {
    const target = event.currentTarget as HTMLElement | null;
    if (!target) {
      return 'after';
    }
    const rect = target.getBoundingClientRect();
    const relativeY = (event.clientY - rect.top) / Math.max(rect.height, 1);
    if (relativeY < 0.3) {
      return 'before';
    }
    if (relativeY > 0.7) {
      return 'after';
    }
    return 'child';
  }

  function updateStepDrop(item: FlatStepItem, event: DragEvent) {
    const sourceId = draggingStepId.value;
    if (!sourceId || !editorScriptAsset.value) {
      return;
    }
    const mode = resolveStepDropMode(event);
    if (!canMoveStep(editorScriptAsset.value.steps, sourceId, item.step.id, mode)) {
      clearStepDrop();
      return;
    }
    dragOverStepId.value = item.step.id;
    dragOverMode.value = mode;
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
  }

  function dropStepOn(item: FlatStepItem, event: DragEvent) {
    updateStepDrop(item, event);
    if (draggingStepId.value && dragOverMode.value) {
      moveStep(draggingStepId.value, item.step.id, dragOverMode.value);
    }
    endStepDrag();
  }

  function clearStepDrop() {
    dragOverStepId.value = null;
    dragOverMode.value = null;
  }

  function endStepDrag() {
    draggingStepId.value = null;
    clearStepDrop();
  }

  function moveStep(sourceId: string, targetId: string, mode: StepDropMode) {
    if (!editorScriptAsset.value || !canMoveStep(editorScriptAsset.value.steps, sourceId, targetId, mode)) {
      return;
    }
    const movedStep = takeStepById(editorScriptAsset.value.steps, sourceId);
    if (!movedStep) {
      return;
    }
    const inserted =
      mode === 'child'
        ? appendChildStep(editorScriptAsset.value.steps, targetId, movedStep)
        : insertStepRelative(editorScriptAsset.value.steps, targetId, movedStep, mode);
    if (!inserted) {
      editorScriptAsset.value.steps.push(movedStep);
      return;
    }
    if (mode === 'child') {
      expandStep(targetId);
    }
    selectedEditorStepId.value = movedStep.id;
    editorScriptAsset.value.updatedAt = new Date().toISOString();
  }

  function closeScriptEditor() {
    const script = editorScriptAsset.value;
    if (script) {
      void router.push(`/projects/${script.projectId}/scripts`);
    } else {
      void router.push('/projects');
    }
    editorScriptId.value = null;
    selectedEditorStepId.value = null;
  }

  function syncEditorRoute() {
    editorScriptId.value = Number(route.params.scriptId) || null;
  }

  function scriptEditorUrl(script: ScriptAsset) {
    return `/projects/${script.projectId}/scripts/${script.id}/edit`;
  }

  async function saveEditorScript() {
    if (!editorScriptAsset.value) {
      return false;
    }
    const { currentUser } = useAuth();
    try {
      const definition = await saveScriptDefinitionApi(
        editorScriptAsset.value.projectId,
        editorScriptAsset.value.id,
        editorScriptAsset.value.sourceFile,
        editorScriptAsset.value.steps,
        currentUser.value?.username ?? 'admin',
      );
      const saved = mapScriptDefinition(definition);
      const index = scriptAssets.value.findIndex((script) => script.id === editorScriptAsset.value?.id);
      if (index >= 0) {
        scriptAssets.value.splice(index, 1, saved);
      } else {
        scriptAssets.value.unshift(saved);
      }
      editorScriptId.value = saved.id;
      void router.replace(scriptEditorUrl(saved));
      selectedEditorStepId.value = saved.steps[0]?.id ?? null;
      return true;
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '脚本保存失败');
      return false;
    }
  }

  return {
    editorScriptId,
    editorScriptAsset,
    scriptEditorVisible,
    selectedEditorStepId,
    selectedEditorStep,
    flatEditorSteps,
    collapsedStepIds,
    draggingStepId,
    dragOverStepId,
    dragOverMode,
    stepDialogVisible,
    stepDialogForm,
    stepDialogTitle,
    availableStepTypeOptions: computed(() => availableStepTypeOptions()),
    isRootStep,
    isStepCollapsed,
    toggleStepCollapsed,
    selectEditorStep,
    canAddChildStepTo,
    openStepDialog,
    createStep,
    confirmDeleteStep,
    startStepDrag,
    updateStepDrop,
    dropStepOn,
    clearStepDrop,
    endStepDrag,
    closeScriptEditor,
    syncEditorRoute,
    scriptEditorUrl,
    ensureScriptSteps,
    saveEditorScript,
  };
}

export { useEditor as useScriptEditor };
