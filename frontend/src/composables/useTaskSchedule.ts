import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type {
  TaskResultFilter,
  TaskStatus,
  TaskStatusFilter,
  TestTask,
} from '../types';
import { nextId } from '../utils/format';
import { createMockTask } from '../utils/task-mock';
import { useWorkspace } from './useWorkspace';
import { useAuth } from './useAuth';
import { getTaskApi, getTaskResultApi, listTasksApi, mapBackendTask, submitTaskApi } from '../api/platform';

type TaskFormPayload = {
  id?: number;
  scriptId: number | null;
  name: string;
  environment: string;
  threads: number;
  rampUp: number;
  duration: number;
  loops: number;
  priority: string;
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
const pageSize = 6;
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
  }

  async function refreshTask(taskId: number) {
    try {
      const backendTask = await getTaskApi(taskId);
      const result = await getTaskResultApi(taskId);
      const remoteTask = mapBackendTask(backendTask, result);
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

  const resultPageCount = computed(() => Math.max(1, Math.ceil(resultSamples.value.length / pageSize)));
  const pagedSamples = computed(() =>
    resultSamples.value.slice((resultPage.value - 1) * pageSize, resultPage.value * pageSize),
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
  watch([resultFilter, detailTask], () => {
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
  }

  function backToList() {
    detailTaskId.value = null;
  }

  function saveTask(payload: TaskFormPayload) {
    if (!currentProject.value || !payload.scriptId) {
      ElMessage.error('请选择脚本');
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
          threads: payload.threads,
          rampUp: payload.rampUp,
          duration: payload.duration,
          loops: payload.loops,
          priority: payload.priority,
          remark: payload.remark,
        });
        selectedTaskId.value = target.id;
      }
      ElMessage.success('任务已更新');
      return true;
    }
    const task = createMockTask(script ?? currentProjectScripts.value[0], projectTasks.value.length);
    Object.assign(task, {
      id: -nextId(tasks.value.map((item) => ({ id: Math.abs(item.id) }))),
      projectId: currentProject.value.id,
      scriptId: payload.scriptId,
      name: payload.name,
      status: 'PENDING' as TaskStatus,
      environment: payload.environment,
      threads: payload.threads,
      rampUp: payload.rampUp,
      duration: payload.duration,
      loops: payload.loops,
      priority: payload.priority,
      remark: payload.remark,
      createdAt: now,
      lastRunAt: null,
    });
    tasks.value.unshift(task);
    selectedTaskId.value = task.id;
    ElMessage.success('任务已保存，点击执行后提交后端');
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
      ElMessage.success('JMeter 任务已提交后端执行');
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '任务提交失败');
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
    selectedSampleId,
  };
}
