import { computed, onScopeDispose, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import type {
  ExecutionDetail,
  ExecutionUiStatus,
  MetricTick,
  ScriptAsset,
  TaskPlan,
  TaskScenario,
  ScenarioExecution,
  TaskSample,
} from '../types';
import { useWorkspace } from './useWorkspace';
import { useAuth } from './useAuth';
import {
  createScenarioApi,
  createTaskPlanApi,
  deleteExecutionApi,
  deleteScenarioApi,
  deleteTaskPlanApi,
  getExecutionApi,
  getExecutionLogsApi,
  getExecutionMonitoringApi,
  getExecutionResultApi,
  getExecutionSamplesApi,
  getExecutionSampleDetailApi,
  getExecutionTargetMonitoringApi,
  listExecutionsApi,
  listScenariosApi,
  listTaskPlansApi,
  mapExecutionDetail,
  stopExecutionApi,
  toUiStatus,
  triggerExecutionApi,
  updateScenarioApi,
  updateTaskPlanApi,
} from '../api/task-plans';
import { confirmAction } from '../utils/feedback';

const plans = ref<TaskPlan[]>([]);
const scenarios = ref<TaskScenario[]>([]);
const executions = ref<ScenarioExecution[]>([]);
const executionDetail = ref<ExecutionDetail | null>(null);
const planKeyword = ref('');
const resultPage = ref(1);
const pageSize = ref(10);
const selectedSampleId = ref<number | null>(null);
const selectedSampleDetail = ref<TaskSample | null>(null);
const sampleDetailLoading = ref(false);
let refreshTimer: number | null = null;
let sampleStreamBackoffStep = 0;
let sampleStream: EventSource | null = null;
let sampleStreamExecutionId: number | null = null;
let sampleStreamRetryTimer: number | null = null;

function executionStatusText(status: ExecutionUiStatus) {
  const map: Record<ExecutionUiStatus, string> = {
    PENDING: '排队中',
    RUNNING: '运行中',
    STOPPING: '停止中',
    SUCCESS: '成功',
    FAILED: '失败',
    INTERRUPTED: '已停止',
  };
  return map[status];
}

export function useTaskPlans() {
  const { currentProject, currentProjectScripts } = useWorkspace();
  const route = useRoute();
  const router = useRouter();

  const projectPlans = computed(() => {
    if (!currentProject.value) return [];
    return plans.value.filter((plan) => plan.projectId === currentProject.value?.id);
  });

  const filteredPlans = computed(() => {
    const keyword = planKeyword.value.trim().toLowerCase();
    return projectPlans.value.filter((plan) => !keyword || plan.name.toLowerCase().includes(keyword));
  });

  const activePlanId = computed(() => Number(route.params.planId) || null);
  const activeScenarioId = computed(() => Number(route.params.scenarioId) || null);
  const activeExecutionId = computed(() => Number(route.params.executionId) || null);

  const activePlan = computed(() => projectPlans.value.find((plan) => plan.id === activePlanId.value) ?? null);
  const activeScenario = computed(() => scenarios.value.find((item) => item.id === activeScenarioId.value) ?? null);

  async function loadPlans() {
    if (!currentProject.value) return;
    try {
      plans.value = await listTaskPlansApi(currentProject.value.id);
    } catch {
      plans.value = [];
    }
  }

  async function loadScenarios(planId: number) {
    try {
      scenarios.value = await listScenariosApi(planId);
    } catch {
      scenarios.value = [];
    }
  }

  async function loadExecutions(scenarioId: number) {
    try {
      executions.value = await listExecutionsApi(scenarioId);
    } catch {
      executions.value = [];
    }
  }

  function disconnectSampleStream() {
    if (sampleStreamRetryTimer !== null) {
      window.clearTimeout(sampleStreamRetryTimer);
      sampleStreamRetryTimer = null;
    }
    sampleStream?.close();
    sampleStream = null;
    sampleStreamExecutionId = null;
  }

  function connectSampleStream(executionId: number) {
    if (sampleStream && sampleStreamExecutionId === executionId) return;
    disconnectSampleStream();
    sampleStreamExecutionId = executionId;
    sampleStream = new EventSource(`/api/executions/${executionId}/stream`);
    sampleStream.addEventListener('sample', (event) => {
      if (!executionDetail.value || executionDetail.value.id !== executionId) return;
      try {
        const sample = JSON.parse(event.data) as TaskSample;
        if (executionDetail.value.samples.some((item) => item.id === sample.id)) return;
        executionDetail.value.samples = [sample, ...executionDetail.value.samples].slice(0, pageSize.value);
        executionDetail.value.sampleTotal += 1;
      } catch {
      }
    });
    sampleStream.addEventListener('metric-tick', (event) => {
      if (!executionDetail.value || executionDetail.value.id !== executionId) return;
      try {
        const tick = JSON.parse(event.data) as MetricTick;
        const ticks = executionDetail.value.monitoring.ticks;
        if (ticks.length && ticks[ticks.length - 1].bucketTimeMs >= tick.bucketTimeMs) return;
        ticks.push(tick);
        if (ticks.length > 1200) ticks.splice(0, ticks.length - 1200);
      } catch {
      }
    });
    sampleStream.onerror = () => {
      disconnectSampleStream();
      const delay = Math.min(30000, 1000 * Math.pow(2, sampleStreamBackoffStep));
      sampleStreamBackoffStep = Math.min(5, sampleStreamBackoffStep + 1);
      sampleStreamRetryTimer = window.setTimeout(() => {
        if (!executionDetail.value || executionDetail.value.id !== executionId) return;
        const status = toUiStatus(executionDetail.value.status);
        if (status === 'RUNNING' || status === 'PENDING' || status === 'STOPPING') {
          connectSampleStream(executionId);
        }
      }, delay);
    };
    sampleStream.onopen = () => {
      sampleStreamBackoffStep = 0;
    };
  }

  async function loadSelectedSampleDetail(executionId: number, sampleId: number | null) {
    if (!sampleId) {
      selectedSampleDetail.value = null;
      return;
    }
    const cached = executionDetail.value?.samples.find((s) => s.id === sampleId);
    if (cached && (cached.responseBody || cached.requestBody || cached.failureMessage)) {
      selectedSampleDetail.value = cached;
      return;
    }
    sampleDetailLoading.value = true;
    try {
      selectedSampleDetail.value = await getExecutionSampleDetailApi(executionId, sampleId);
    } catch {
      selectedSampleDetail.value = null;
    } finally {
      sampleDetailLoading.value = false;
    }
  }

  async function refreshExecution(executionId: number) {
    try {
      const [execution, result, monitoring, targetMonitoring] = await Promise.all([
        getExecutionApi(executionId),
        getExecutionResultApi(executionId),
        getExecutionMonitoringApi(executionId),
        getExecutionTargetMonitoringApi(executionId),
      ]);
      const detail = mapExecutionDetail(execution, result, monitoring, targetMonitoring);
      if (toUiStatus(execution.status) === 'FAILED' || toUiStatus(execution.status) === 'INTERRUPTED') {
        try {
          detail.executionLogs = await getExecutionLogsApi(executionId);
        } catch {
        }
      }
      executionDetail.value = detail;
      void loadExecutionSamples(executionId);
      const uiStatus = toUiStatus(detail.status);
      if (uiStatus === 'RUNNING' || uiStatus === 'PENDING' || uiStatus === 'STOPPING') {
        connectSampleStream(executionId);
        scheduleRefresh(executionId);
      } else {
        disconnectSampleStream();
      }
    } catch {
    }
  }

  function scheduleRefresh(executionId: number) {
    if (refreshTimer !== null) window.clearTimeout(refreshTimer);
    if (typeof document !== 'undefined' && document.hidden) {
      refreshTimer = window.setTimeout(() => void refreshExecution(executionId), 10000);
      return;
    }
    refreshTimer = window.setTimeout(() => void refreshExecution(executionId), 5000);
  }

  watch([currentProject], () => void loadPlans(), { immediate: true });

  watch(
    () => [route.params.planId, route.name] as const,
    ([planId, name]) => {
      const id = Number(planId);
      if (id && String(name).includes('plan')) void loadScenarios(id);
    },
    { immediate: true },
  );

  watch(
    () => [route.params.scenarioId, route.name] as const,
    ([scenarioId, name]) => {
      const id = Number(scenarioId);
      if (id && String(name).includes('scenario')) void loadExecutions(id);
    },
    { immediate: true },
  );

  watch(
    () => route.params.executionId,
    (executionId) => {
      const id = Number(executionId);
      if (id) {
        resultPage.value = 1;
        void refreshExecution(id);
      } else {
        executionDetail.value = null;
        disconnectSampleStream();
      }
    },
    { immediate: true },
  );

  watch([resultPage, pageSize], () => {
    if (activeExecutionId.value) void loadExecutionSamples(activeExecutionId.value);
  });

  watch([activeExecutionId, selectedSampleId], ([executionId, sampleId]) => {
    if (executionId) void loadSelectedSampleDetail(executionId, sampleId);
    else selectedSampleDetail.value = null;
  });

  onScopeDispose(() => {
    disconnectSampleStream();
    if (refreshTimer !== null) window.clearTimeout(refreshTimer);
  });

  function scriptById(scriptId: number) {
    return currentProjectScripts.value.find((script) => script.id === scriptId) ?? null;
  }

  function openPlan(plan: TaskPlan) {
    void router.push(`/projects/${plan.projectId}/task-plans/${plan.id}`);
  }

  function openScenario(scenario: TaskScenario) {
    void backToPlanDetail(scenario.planId);
  }

  function openExecution(execution: ScenarioExecution) {
    void router.push(`/projects/${execution.projectId}/executions/${execution.id}`);
  }

  function backToPlanList() {
    if (currentProject.value) void router.push(`/projects/${currentProject.value.id}/task-plans`);
  }

  function backToPlanDetail(planId: number) {
    if (currentProject.value) void router.push(`/projects/${currentProject.value.id}/task-plans/${planId}`);
  }

  function backToScenarioDetail(scenarioId: number, planId: number) {
    void scenarioId;
    void backToPlanDetail(planId);
  }

  async function savePlan(payload: {
    id?: number;
    name: string;
    remark: string;
    controllerNodeId: number | null;
    workerNodeIds: number[];
    monitorTargetIds: number[];
  }) {
    if (!currentProject.value) return false;
    const { currentUser } = useAuth();
    try {
      if (payload.id) {
        const plan = await updateTaskPlanApi(payload.id, payload);
        plans.value = [plan, ...plans.value.filter((item) => item.id !== plan.id)];
      } else {
        const plan = await createTaskPlanApi(currentProject.value.id, payload, currentUser.value?.username ?? 'admin');
        plans.value = [plan, ...plans.value];
      }
      message.success('任务计划已保存');
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      return false;
    }
  }

  async function removePlan(plan: TaskPlan) {
    try {
      await confirmAction({ title: '删除计划', content: `确认删除「${plan.name}」？`, okText: '删除', okType: 'danger' });
      await deleteTaskPlanApi(plan.id);
      plans.value = plans.value.filter((item) => item.id !== plan.id);
      if (activePlanId.value === plan.id) backToPlanList();
      message.success('计划已删除');
    } catch (error) {
      if (error instanceof Error) message.error(error.message);
    }
  }

  async function saveScenario(planId: number, payload: Parameters<typeof createScenarioApi>[1] & { id?: number }) {
    try {
      if (payload.id) {
        const scenario = await updateScenarioApi(payload.id, payload);
        scenarios.value = scenarios.value.map((item) => (item.id === scenario.id ? scenario : item));
      } else {
        const scenario = await createScenarioApi(planId, payload);
        scenarios.value = [...scenarios.value, scenario];
      }
      message.success('场景已保存');
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      return false;
    }
  }

  async function removeScenario(scenario: TaskScenario) {
    try {
      await confirmAction({ title: '删除场景', content: `确认删除「${scenario.name}」？`, okText: '删除', okType: 'danger' });
      await deleteScenarioApi(scenario.id);
      scenarios.value = scenarios.value.filter((item) => item.id !== scenario.id);
      if (activeScenarioId.value === scenario.id) backToPlanDetail(scenario.planId);
      message.success('场景已删除');
    } catch (error) {
      if (error instanceof Error) message.error(error.message);
    }
  }

  async function runScenario(
    scenario: TaskScenario,
    options?: {
      executionName?: string;
      threadGroupConfigId?: number | null;
      threadGroupPresetSortOrder?: number | null;
    },
  ) {
    try {
      const execution = await triggerExecutionApi(scenario.id, options);
      executions.value = [execution, ...executions.value];
      openExecution(execution);
      message.success('场景已提交执行');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '执行失败');
    }
  }

  async function stopActiveExecution() {
    if (!executionDetail.value) return;
    try {
      await stopExecutionApi(executionDetail.value.id);
      await refreshExecution(executionDetail.value.id);
      message.success('已请求停止执行');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '停止失败');
    }
  }

  async function removeExecution(execution: ScenarioExecution) {
    const ui = toUiStatus(execution.status);
    if (ui === 'RUNNING' || ui === 'PENDING' || ui === 'STOPPING') {
      message.warning('运行中的记录不能删除');
      return;
    }
    try {
      await confirmAction({ title: '删除记录', content: '确认删除该执行记录？', okText: '删除', okType: 'danger' });
      await deleteExecutionApi(execution.id);
      executions.value = executions.value.filter((item) => item.id !== execution.id);
      if (activeExecutionId.value === execution.id) {
        backToPlanDetail(execution.planId);
      }
      message.success('记录已删除');
    } catch (error) {
      if (error instanceof Error) message.error(error.message);
    }
  }

  async function loadExecutionSamples(executionId: number) {
    if (!executionDetail.value || executionDetail.value.id !== executionId) return;
    try {
      const samplePage = await getExecutionSamplesApi(executionId, resultPage.value, pageSize.value);
      if (!executionDetail.value || executionDetail.value.id !== executionId) return;
      executionDetail.value.samples = samplePage.samples;
      executionDetail.value.sampleTotal = samplePage.total;
      selectedSampleId.value = executionDetail.value.samples.some((s) => s.id === selectedSampleId.value)
        ? selectedSampleId.value
        : executionDetail.value.samples[0]?.id ?? null;
    } catch {
    }
  }

  async function runScriptAsset(script: ScriptAsset) {
    if (!currentProject.value) return false;
    const { currentUser } = useAuth();
    try {
      await confirmAction({
        title: '执行脚本',
        content: `确认执行脚本「${script.name}」？将创建任务计划并立即执行。`,
        okText: '执行',
      });
      const plan = await createTaskPlanApi(
        currentProject.value.id,
        { name: `${script.name} / 即时执行`, remark: '从脚本列表直接执行' },
        currentUser.value?.username ?? 'admin',
      );
      const scenario = await createScenarioApi(plan.id, {
        scriptVersionId: script.id,
        name: script.name,
      });
      const execution = await triggerExecutionApi(scenario.id);
      plans.value = [plan, ...plans.value];
      openExecution(execution);
      message.success('已创建计划并提交执行');
      return true;
    } catch (error) {
      if (error instanceof Error) message.error(error.message);
      return false;
    }
  }

  const resultSamples = computed(() => executionDetail.value?.samples ?? []);
  const resultTotal = computed(() => executionDetail.value?.sampleTotal ?? 0);
  const pagedSamples = computed(() => resultSamples.value);
  const selectedSample = computed(() => selectedSampleDetail.value);

  return {
    plans,
    scenarios,
    executions,
    executionDetail,
    planKeyword,
    filteredPlans,
    projectPlans,
    activePlan,
    activeScenario,
    activePlanId,
    activeScenarioId,
    activeExecutionId,
    resultPage,
    pageSize,
    resultSamples,
    resultTotal,
    pagedSamples,
    selectedSample,
    selectedSampleId,
    sampleDetailLoading,
    executionStatusText,
    toUiStatus,
    scriptById,
    loadPlans,
    loadScenarios,
    loadExecutions,
    openPlan,
    openScenario,
    openExecution,
    backToPlanList,
    backToPlanDetail,
    backToScenarioDetail,
    savePlan,
    removePlan,
    saveScenario,
    removeScenario,
    runScenario,
    stopActiveExecution,
    removeExecution,
    runScriptAsset,
    refreshExecution,
  };
}
