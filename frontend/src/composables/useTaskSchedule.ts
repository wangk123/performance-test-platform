import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import type {
  TaskResultFilter,
  ScriptAsset,
  TaskStatus,
  TaskStatusFilter,
  TestTask,
} from '../types';
import { nextId } from '../utils/format';
import { createMockTask } from '../utils/task-mock';
import { useWorkspace } from './useWorkspace';
import { useAuth } from './useAuth';
import { deleteTaskApi, getTaskApi, getTaskMonitoringApi, getTaskResultApi, listTasksApi, mapBackendTask, submitScriptTaskApi, submitTaskApi } from '../api/tasks';
import { confirmAction } from '../utils/feedback';

type TaskFormPayload = {
  id?: number;
  scriptId: number | null;
  name: string;
  environment: string;
  priority: string;
  executionMode: 'LOCAL' | 'DISTRIBUTED';
  controllerNodeId: number | null;
  workerNodeIds: number[];
  remark: string;
};

const tasks = ref<TestTask[]>([]);
const selectedTaskId = ref<number | null>(null);
const detailTaskId = ref<number | null>(null);
const taskKeyword = ref('');
const taskStatusFilter = ref<TaskStatusFilter>('ALL');
const resultFilter = ref<TaskResultFilter>('ALL');
const resultPage = ref(1);
const selectedSampleId = ref<number | null>(null);
const pageSize = ref(10);
let refreshTimer: number | null = null;

function taskStatusText(status: TaskStatus) {
  const map: Record<TaskStatus, string> = {
    PENDING: '待执行',
    RUNNING: '运行中',
    SUCCESS: '成功',
    FAILED: '失败',
  };
  return map[status];
}

export function useTaskSchedule() {
  const { currentProject, currentProjectScripts } = useWorkspace();
  const route = useRoute();
  const router = useRouter();

  async function ensureProjectTasks() {
    if (!currentProject.value) {
      return;
    }
    try {
      const remoteTasks = (await listTasksApi(currentProject.value.id)).map((task) => mapBackendTask(task));
      tasks.value = [
        ...tasks.value.filter((task) => task.projectId !== currentProject.value?.id || task.id < 0),
        ...remoteTasks,
      ];
    } catch {
      const hasProjectTasks = tasks.value.some((task) => task.projectId === currentProject.value?.id);
      if (!hasProjectTasks) {
        tasks.value.push(...currentProjectScripts.value.map(createMockTask));
      }
    }
    if (!selectedTaskId.value) {
      selectedTaskId.value = projectTasks.value[0]?.id ?? null;
    }
    const routeTaskId = Number(route.params.taskId);
    if (routeTaskId) {
      detailTaskId.value = routeTaskId;
      selectedTaskId.value = routeTaskId;
      void refreshTask(routeTaskId);
    }
  }

  async function refreshTask(taskId: number) {
    try {
      const backendTask = await getTaskApi(taskId);
      const result = await getTaskResultApi(taskId);
      const monitoring = await getTaskMonitoringApi(taskId);
      const remoteTask = mapBackendTask(backendTask, result, monitoring);
      replaceTask(remoteTask);
      if (detailTaskId.value === taskId) {
        selectedSampleId.value = remoteTask.samples[0]?.id ?? null;
      }
      if (remoteTask.status === 'RUNNING' || remoteTask.status === 'PENDING') {
        scheduleRefresh(taskId);
      }
    } catch {
    }
  }

  function scheduleRefresh(taskId: number) {
    if (refreshTimer !== null) {
      window.clearTimeout(refreshTimer);
    }
    refreshTimer = window.setTimeout(() => {
      void refreshTask(taskId);
    }, 1500);
  }

  function replaceTask(task: TestTask) {
    tasks.value = [
      task,
      ...tasks.value.filter((item) => item.id !== task.id),
    ];
  }

  const projectTasks = computed(() => {
    if (!currentProject.value) {
      return [];
    }
    return tasks.value.filter((task) => task.projectId === currentProject.value?.id);
  });

  const filteredTasks = computed(() => {
    const keyword = taskKeyword.value.trim().toLowerCase();
    return projectTasks.value.filter((task) => {
      const script = currentProjectScripts.value.find((item) => item.id === task.scriptId);
      const source = `${task.name} ${script?.name ?? ''} ${script?.sourceFile ?? ''}`.toLowerCase();
      const matchesStatus = taskStatusFilter.value === 'ALL' || task.status === taskStatusFilter.value;
      return matchesStatus && (!keyword || source.includes(keyword));
    });
  });

  const selectedTask = computed(
    () => projectTasks.value.find((task) => task.id === selectedTaskId.value) ?? projectTasks.value[0] ?? null,
  );
  const detailTask = computed(() => projectTasks.value.find((task) => task.id === detailTaskId.value) ?? null);
  const runningTaskCount = computed(() => projectTasks.value.filter((task) => task.status === 'RUNNING').length);
  const pendingTaskCount = computed(() => projectTasks.value.filter((task) => task.status === 'PENDING').length);
  const failedTaskCount = computed(() => projectTasks.value.filter((task) => task.status === 'FAILED').length);

  const resultSamples = computed(() => {
    if (!detailTask.value) {
      return [];
    }
    if (resultFilter.value === 'SUCCESS') {
      return detailTask.value.samples.filter((sample) => sample.success);
    }
    if (resultFilter.value === 'ERROR') {
      return detailTask.value.samples.filter((sample) => !sample.success);
    }
    return detailTask.value.samples;
  });

  const resultPageCount = computed(() => Math.max(1, Math.ceil(resultSamples.value.length / pageSize.value)));
  const pagedSamples = computed(() =>
    resultSamples.value.slice((resultPage.value - 1) * pageSize.value, resultPage.value * pageSize.value),
  );
  const selectedSample = computed(
    () => detailTask.value?.samples.find((sample) => sample.id === selectedSampleId.value) ?? pagedSamples.value[0] ?? null,
  );

  watch([currentProject, currentProjectScripts], () => {
    void ensureProjectTasks();
  }, { immediate: true });
  watch(filteredTasks, (items) => {
    if (!items.some((task) => task.id === selectedTaskId.value)) {
      selectedTaskId.value = items[0]?.id ?? null;
    }
  });
  watch([resultFilter, detailTask, pageSize], () => {
    resultPage.value = 1;
    selectedSampleId.value = resultSamples.value[0]?.id ?? null;
  });

  function scriptById(scriptId: number) {
    return currentProjectScripts.value.find((script) => script.id === scriptId) ?? null;
  }

  function selectTask(task: TestTask) {
    selectedTaskId.value = task.id;
  }

  function showTaskDetail(task: TestTask) {
    selectedTaskId.value = task.id;
    detailTaskId.value = task.id;
    resultFilter.value = 'ALL';
    selectedSampleId.value = task.samples[0]?.id ?? null;
    void refreshTask(task.id);
    if (currentProject.value) {
      void router.push(`/projects/${currentProject.value.id}/tasks/${task.id}`);
    }
  }

  function backToList() {
    detailTaskId.value = null;
    if (currentProject.value) {
      void router.push(`/projects/${currentProject.value.id}/tasks`);
    }
  }

  function saveTask(payload: TaskFormPayload) {
    if (!currentProject.value || !payload.scriptId) {
      message.error('请选择脚本');
      return false;
    }
    const script = scriptById(payload.scriptId);
    const now = new Date().toISOString();
    if (payload.id) {
      const target = tasks.value.find((task) => task.id === payload.id);
      if (target) {
        Object.assign(target, {
          scriptId: payload.scriptId,
          name: payload.name,
          environment: payload.environment,
          priority: payload.priority,
          executionMode: payload.executionMode,
          controllerNodeId: payload.controllerNodeId,
          workerNodeIds: payload.workerNodeIds,
          remark: payload.remark,
        });
        selectedTaskId.value = target.id;
      }
      message.success('任务已更新');
      return true;
    }
    const task = createMockTask(script ?? currentProjectScripts.value[0], projectTasks.value.length);
    Object.assign(task, {
      id: -nextId(tasks.value.map((item) => ({ id: Math.abs(item.id) }))),
      projectId: currentProject.value.id,
      scriptId: payload.scriptId,
      name: payload.name,
      status: 'PENDING' as TaskStatus,
      executionMode: payload.executionMode,
      controllerNodeId: payload.controllerNodeId,
      workerNodeIds: payload.workerNodeIds,
      grafanaUrl: null,
      monitoring: { interfaces: [], points: [] },
      environment: payload.environment,
      priority: payload.priority,
      remark: payload.remark,
      createdAt: now,
      lastRunAt: null,
    });
    tasks.value.unshift(task);
    selectedTaskId.value = task.id;
    message.success('任务已保存，点击执行后提交后端');
    return true;
  }

  async function runTask(task: TestTask) {
    if (!currentProject.value) {
      return;
    }
    const { currentUser } = useAuth();
    try {
      const remoteTask = mapBackendTask(await submitTaskApi(currentProject.value.id, task, currentUser.value?.username ?? 'admin'));
      tasks.value = [remoteTask, ...tasks.value.filter((item) => item.id !== task.id && item.id !== remoteTask.id)];
      selectedTaskId.value = remoteTask.id;
      showTaskDetail(remoteTask);
      scheduleRefresh(remoteTask.id);
      message.success('JMeter 任务已提交后端执行');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '任务提交失败');
    }
  }

  async function runScriptAsset(script: ScriptAsset) {
    if (!currentProject.value) {
      return false;
    }
    const { currentUser } = useAuth();
    try {
      await confirmAction({
        title: '执行脚本',
        content: `确认执行脚本「${script.name}」？系统会立即创建任务计划并提交执行。`,
        okText: '执行',
      });
      const remoteTask = mapBackendTask(await submitScriptTaskApi(currentProject.value.id, script, currentUser.value?.username ?? 'admin'));
      replaceTask(remoteTask);
      selectedTaskId.value = remoteTask.id;
      showTaskDetail(remoteTask);
      scheduleRefresh(remoteTask.id);
      message.success('任务已创建并提交执行');
      return true;
    } catch (error) {
      if (error instanceof Error) {
        message.error(error.message);
      }
      return false;
    }
  }

  async function deleteTask(task: TestTask) {
    if (task.status === 'RUNNING') {
      message.warning('运行中任务不能删除');
      return;
    }
    try {
      await confirmAction({
        title: '删除任务',
        content: `确认删除任务「${task.name}」？`,
        okText: '删除',
        okType: 'danger',
      });
      if (task.id > 0) {
        await deleteTaskApi(task.id);
      }
      tasks.value = tasks.value.filter((item) => item.id !== task.id);
      if (selectedTaskId.value === task.id) {
        selectedTaskId.value = projectTasks.value[0]?.id ?? null;
      }
      if (detailTaskId.value === task.id) {
        backToList();
      }
      message.success('任务已删除');
    } catch (error) {
      if (error instanceof Error) {
        message.error(error.message);
      }
    }
  }

  return {
    tasks,
    taskKeyword,
    taskStatusFilter,
    resultFilter,
    resultPage,
    pageSize,
    projectTasks,
    filteredTasks,
    selectedTask,
    detailTask,
    runningTaskCount,
    pendingTaskCount,
    failedTaskCount,
    resultSamples,
    resultPageCount,
    pagedSamples,
    selectedSample,
    taskStatusText,
    scriptById,
    selectTask,
    showTaskDetail,
    backToList,
    saveTask,
    runTask,
    runScriptAsset,
    deleteTask,
    selectedSampleId,
  };
}
