<template>
  <a-modal v-model:open="visible" :title="isEditing ? '编辑场景' : '添加场景'" width="800px" destroy-on-close>
    <a-form class="plan-dialog-form" layout="vertical">
      <a-form-item :label="isEditing ? '场景名称' : '场景名称前缀'">
        <a-input v-model:value="form.name" :placeholder="isEditing ? '' : '留空则使用脚本名称'" />
      </a-form-item>
      <a-form-item label="测试类型" name="testType" extra="用于测试方法章节分组与报告口径，创建后可修改">
        <!-- option-label-prop：选中回显取短名，下拉列表仍渲染「名称+说明」自定义内容 -->
        <a-select v-model:value="form.testType" placeholder="选择测试类型" option-label-prop="label">
          <a-select-option v-for="t in TEST_TYPES" :key="t.value" :value="t.value" :label="t.label">
            <div class="type-option">
              <span class="type-option-label">{{ t.label }}</span>
              <span class="type-option-desc">{{ t.desc }}</span>
            </div>
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="场景目的" name="purpose">
        <a-textarea v-model:value="form.purpose" :rows="2" placeholder="业务目的，写入计划文档场景章节" />
      </a-form-item>
      <a-form-item label="选择脚本">
        <div class="script-list">
          <div class="script-list-head">
            <span class="script-list-col-select" />
            <span class="script-list-col-name">脚本名称</span>
            <span class="script-list-col-file">文件</span>
            <span class="script-list-col-action" />
          </div>
          <div v-if="isEditing" class="script-list-body">
            <div
              v-for="script in currentProjectScripts"
              :key="script.id"
              class="script-list-row"
              :class="{ selected: isScriptSelected(script.id) }"
              @click="selectSingleScript(script.id)"
            >
              <span class="script-list-col-select">
                <a-radio :checked="isScriptSelected(script.id)" @click.stop="selectSingleScript(script.id)" />
              </span>
              <span class="script-list-col-name">
                <strong>{{ script.name }}</strong>
              </span>
              <span class="script-list-col-file">{{ script.sourceFile }}</span>
              <span class="script-list-col-action">
                <EditOutlined
                  class="script-list-edit"
                  @click.stop="openScriptEditor(script.id)"
                />
              </span>
            </div>
          </div>
          <div v-else class="script-list-body">
            <div
              v-for="script in currentProjectScripts"
              :key="script.id"
              class="script-list-row"
              :class="{ selected: isScriptSelected(script.id) }"
              @click="toggleScript(script.id)"
            >
              <span class="script-list-col-select">
                <a-checkbox
                  :checked="isScriptSelected(script.id)"
                  @click.stop="toggleScript(script.id)"
                />
              </span>
              <span class="script-list-col-name">
                <strong>{{ script.name }}</strong>
              </span>
              <span class="script-list-col-file">{{ script.sourceFile }}</span>
              <span class="script-list-col-action">
                <EditOutlined
                  class="script-list-edit"
                  @click.stop="openScriptEditor(script.id)"
                />
              </span>
            </div>
          </div>
        </div>
        <div v-if="selectedCount > 0 && !isEditing" class="script-list-hint">
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
      <a-form-item v-if="activeScriptVersionId" label="CSV 数据文件">
        <div class="csv-bind">
          <div v-if="csvSteps.length === 0" class="csv-bind-hint">当前脚本没有 CSV 数据步骤。</div>
          <template v-else>
            <div class="csv-bind-desc">
              绑定后执行时取数据文件最新版本，分发至 Controller 与全部 Worker；未绑定步骤按脚本内文件名同名兜底匹配。
            </div>
            <div v-for="row in csvSteps" :key="row.stepId" class="csv-bind-row">
              <div class="csv-bind-info">
                <div class="csv-bind-name">
                  <span>{{ row.stepName }}</span>
                  <a-tag class="csv-bind-file" :title="row.fileName">{{ row.fileName }}</a-tag>
                </div>
                <div v-if="row.variableNames" class="csv-bind-vars">vars: {{ row.variableNames }}</div>
              </div>
              <div class="csv-bind-control">
                <a-select
                  class="csv-bind-select"
                  :value="boundDataFileId(row.stepId)"
                  :loading="dataFilesLoading"
                  allow-clear
                  placeholder="选择数据文件"
                  option-label-prop="label"
                  @change="(value: number | undefined) => setDataFileBinding(row, value)"
                >
                  <a-select-option v-for="opt in dataFileOptions" :key="opt.value" :value="opt.value" :label="opt.label">
                    <div class="csv-bind-option" :title="opt.columns.length > 0 ? `列：${opt.columns.join('，')}` : ''">
                      <span class="csv-bind-option-label">{{ opt.label }}</span>
                      <span v-if="opt.columns.length > 0" class="csv-bind-option-cols">{{ opt.columns.join(' | ') }}</span>
                    </div>
                  </a-select-option>
                </a-select>
                <div v-if="!boundDataFileId(row.stepId)" class="csv-bind-fallback">
                  未绑定 · 执行时按 {{ row.fileName }} 同名兜底匹配
                </div>
              </div>
            </div>
          </template>
        </div>
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
import type { DataFile, ScriptStep, TaskPlan, TaskScenario, ScenarioDataFileBinding, ScenarioThreadGroupConfig } from '../../types';
import { useWorkspace } from '../../composables/useWorkspace';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { useExecutionNodes } from '../../composables/useExecutionNodes';
import { useMonitoring } from '../../composables/useMonitoring';
import { listDataFilesApi } from '../../api/data-files';
import { getScriptDefinitionApi } from '../../api/scripts';
import { formatFileSize } from '../../utils/format';
import { TEST_TYPE_LABEL } from '../../utils/test-type';
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

const TEST_TYPES = [
  { value: 'BENCHMARK', label: TEST_TYPE_LABEL.BENCHMARK, desc: '低压力阶梯施压，采集基线响应时间与资源占用，验证环境与脚本就绪' },
  { value: 'SINGLE_TXN', label: TEST_TYPE_LABEL.SINGLE_TXN, desc: '单接口阶梯加压与并发冲击，定位容量拐点与瓶颈层' },
  { value: 'COMPOSITE', label: TEST_TYPE_LABEL.COMPOSITE, desc: '按生产流量配比多链路并行施压，验证资源隔离与互不劣化' },
  { value: 'STABILITY', label: TEST_TYPE_LABEL.STABILITY, desc: '目标容量水位长时间稳态运行，观察内存、连接与慢查询累积效应' },
] as const;

const form = reactive({
  id: undefined as number | undefined,
  name: '',
  purpose: '',
  testType: null as string | null,
  selectedScriptIds: [] as number[],
  overridePlanDefaults: false,
  controllerNodeId: null as number | null,
  workerNodeIds: [] as number[],
  monitorTargetIds: [] as number[],
  threadGroupConfigs: [] as ScenarioThreadGroupConfig[],
  dataFileBindings: [] as ScenarioDataFileBinding[],
});

const activeScriptVersionId = computed(() => (
  isEditing.value ? form.selectedScriptIds[0] ?? null : form.selectedScriptIds[0] ?? null
));

const selectableMonitorTargets = computed(() => monitorTargets.value.filter((t) => t.enabled));
const selectedCount = computed(() => form.selectedScriptIds.length);
const canSave = computed(() => form.selectedScriptIds.length > 0 || form.name.trim().length > 0);

function isScriptSelected(scriptId: number) {
  return form.selectedScriptIds.includes(scriptId);
}

function selectSingleScript(scriptId: number) {
  form.selectedScriptIds = [scriptId];
}

function toggleScript(scriptId: number) {
  const idx = form.selectedScriptIds.indexOf(scriptId);
  if (idx >= 0) {
    form.selectedScriptIds.splice(idx, 1);
  } else {
    form.selectedScriptIds.push(scriptId);
  }
}

type CsvStepRow = { stepId: string; stepName: string; fileName: string; variableNames: string };

const csvSteps = ref<CsvStepRow[]>([]);
const dataFiles = ref<DataFile[]>([]);
const dataFilesLoading = ref(false);

function collectCsvSteps(steps: ScriptStep[]): CsvStepRow[] {
  return steps.flatMap((step) => [
    ...(step.type === 'CSV_DATA'
      ? [{
          stepId: step.id,
          stepName: step.name,
          fileName: String(step.config.fileName ?? ''),
          variableNames: String(step.config.variableNames ?? ''),
        }]
      : []),
    ...collectCsvSteps(step.children),
  ]);
}

watch(() => [props.plan.projectId, activeScriptVersionId.value] as const, async ([projectId, versionId]) => {
  csvSteps.value = [];
  if (!projectId || !versionId) return;
  try {
    const definition = await getScriptDefinitionApi(projectId, versionId);
    csvSteps.value = collectCsvSteps(definition.steps);
    // 换绑脚本后丢弃不在当前 CSV 步骤里的绑定项（与后端 normalize 同口径）
    const validStepIds = new Set(csvSteps.value.map((row) => row.stepId));
    form.dataFileBindings = form.dataFileBindings.filter((binding) => validStepIds.has(binding.stepId));
  } catch {
    csvSteps.value = [];
  }
}, { immediate: true });

async function loadDataFiles(projectId: number) {
  dataFilesLoading.value = true;
  try {
    dataFiles.value = await listDataFilesApi(projectId);
  } catch {
    dataFiles.value = [];
  } finally {
    dataFilesLoading.value = false;
  }
}

type DataFileOption = { value: number; label: string; columns: string[] };

const dataFileOptions = computed<DataFileOption[]>(() => dataFiles.value.map((file) => {
  const version = file.latestVersion;
  const meta = [
    version ? `v${version.versionNo}` : '',
    version?.rowCount != null ? `${version.rowCount.toLocaleString()} 行` : '',
    version ? formatFileSize(version.sizeBytes) : '',
  ].filter(Boolean);
  return {
    value: file.id,
    label: [file.name, ...meta].join(' · '),
    columns: version?.headerColumns ?? [],
  };
}));

function boundDataFileId(stepId: string) {
  return form.dataFileBindings.find((binding) => binding.stepId === stepId)?.dataFileId;
}

function setDataFileBinding(row: CsvStepRow, dataFileId: number | undefined) {
  const idx = form.dataFileBindings.findIndex((binding) => binding.stepId === row.stepId);
  if (dataFileId == null) {
    // allow-clear 清空 = 未绑定，走同名兜底，不入提交数组
    if (idx >= 0) form.dataFileBindings.splice(idx, 1);
    return;
  }
  const entry: ScenarioDataFileBinding = { stepId: row.stepId, stepName: row.stepName, dataFileId };
  if (idx >= 0) form.dataFileBindings.splice(idx, 1, entry);
  else form.dataFileBindings.push(entry);
}

watch(() => [props.modelValue, props.editingScenario, props.plan] as const, async () => {
  if (!props.modelValue) return;
  void loadNodes();
  await loadMonitorTargets(props.plan.projectId);
  void loadDataFiles(props.plan.projectId);

  if (props.editingScenario) {
    const script = currentProjectScripts.value.find((s) => s.id === props.editingScenario?.scriptVersionId);
    form.id = props.editingScenario.id;
    form.name = props.editingScenario.name;
    form.purpose = props.editingScenario.purpose ?? '';
    form.testType = props.editingScenario.testType ?? null;
    form.selectedScriptIds = props.editingScenario.scriptVersionId ? [props.editingScenario.scriptVersionId] : [];
    form.overridePlanDefaults = props.editingScenario.controllerNodeId != null
      || (props.editingScenario.workerNodeIds?.length ?? 0) > 0
      || (props.editingScenario.monitorTargetIds?.length ?? 0) > 0;
    form.controllerNodeId = props.editingScenario.controllerNodeId ?? props.plan.defaultControllerNodeId;
    form.workerNodeIds = [...(props.editingScenario.workerNodeIds ?? props.plan.defaultWorkerNodeIds)];
    form.monitorTargetIds = [...(props.editingScenario.monitorTargetIds ?? props.plan.defaultMonitorTargetIds)];
    form.threadGroupConfigs = [...(props.editingScenario.threadGroupConfigs ?? [])];
    form.dataFileBindings = [...(props.editingScenario.dataFileBindings ?? [])];
  } else {
    form.id = undefined;
    form.name = '';
    form.purpose = '';
    form.testType = 'BENCHMARK';
    form.selectedScriptIds = [];
    form.overridePlanDefaults = false;
    form.controllerNodeId = props.plan.defaultControllerNodeId;
    form.workerNodeIds = [...props.plan.defaultWorkerNodeIds];
    form.monitorTargetIds = [...props.plan.defaultMonitorTargetIds];
    form.threadGroupConfigs = [];
    form.dataFileBindings = [];
  }
}, { immediate: true });

function openScriptEditor(scriptId: number) {
  const route = router.resolve(`/projects/${props.plan.projectId}/scripts/${scriptId}/edit`);
  window.open(route.href, '_blank');
}

async function onSave() {
  if (!canSave.value) return;
  saving.value = true;
  try {
    if (form.selectedScriptIds.length === 0) {
      const success = await saveScenario(props.plan.id, {
        id: isEditing.value ? form.id : undefined,
        name: form.name.trim() || '未命名场景',
        scriptVersionId: null,
        purpose: form.purpose,
        testType: form.testType,
        threadGroupConfigs: form.threadGroupConfigs,
        dataFileBindings: [],
        overridePlanDefaults: form.overridePlanDefaults,
        controllerNodeId: form.overridePlanDefaults ? form.controllerNodeId : undefined,
        workerNodeIds: form.overridePlanDefaults ? form.workerNodeIds : undefined,
        monitorTargetIds: form.overridePlanDefaults ? form.monitorTargetIds : undefined,
      });
      if (success) visible.value = false;
      return;
    }
    for (const scriptId of form.selectedScriptIds) {
      const script = currentProjectScripts.value.find((s) => s.id === scriptId);
      const scenarioName = isEditing.value
        ? form.name
        : (form.name.trim() ? `${form.name.trim()} ${script?.name ?? ''}` : `${script?.name ?? ''} 场景`);
      const success = await saveScenario(props.plan.id, {
        id: isEditing.value ? form.id : undefined,
        name: scenarioName,
        scriptVersionId: scriptId,
        purpose: form.purpose,
        testType: form.testType,
        threadGroupConfigs: isEditing.value || scriptId === form.selectedScriptIds[0]
          ? form.threadGroupConfigs
          : [],
        dataFileBindings: isEditing.value || scriptId === form.selectedScriptIds[0]
          ? form.dataFileBindings
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

<style scoped>
.type-option {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
  padding: 2px 0;
}
.type-option-label {
  font-weight: 600;
}
.type-option-desc {
  font-size: 11.5px;
  opacity: 0.65;
}
.csv-bind {
  display: grid;
  gap: 10px;
}
.csv-bind-hint {
  color: var(--muted);
  font-size: 13px;
}
.csv-bind-desc {
  color: var(--muted);
  font-size: 12.5px;
}
.csv-bind-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-soft);
}
.csv-bind-info {
  min-width: 0;
  display: grid;
  gap: 4px;
}
.csv-bind-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}
.csv-bind-file {
  margin: 0;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-data);
  font-size: 12px;
}
.csv-bind-vars {
  font-family: var(--font-data);
  font-size: 12px;
  color: var(--muted);
  word-break: break-all;
}
.csv-bind-control {
  flex: 0 0 300px;
  display: grid;
  gap: 4px;
  justify-items: stretch;
}
.csv-bind-select {
  width: 100%;
}
.csv-bind-fallback {
  font-size: 12px;
  color: var(--muted);
}
.csv-bind-option {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
  padding: 2px 0;
}
.csv-bind-option-label {
  font-weight: 500;
}
.csv-bind-option-cols {
  font-family: var(--font-data);
  font-size: 11.5px;
  opacity: 0.65;
}
</style>
