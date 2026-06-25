import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { MonitorTarget } from '../types';
import { checkMonitorTargetApi, createMonitorTargetApi, deleteMonitorTargetApi, listMonitorTargetsApi, type MonitorTargetPayload, updateMonitorTargetApi } from '../api/monitoring';
import { confirmAction } from '../utils/feedback';

const monitorTargets = ref<MonitorTarget[]>([]);
const loadingMonitorTargets = ref(false);
const monitorTargetRequests = new Map<number, Promise<MonitorTarget[]>>();

function replaceProjectTargets(projectId: number, targets: MonitorTarget[]) {
  monitorTargets.value = [
    ...monitorTargets.value.filter((target) => target.projectId !== projectId),
    ...targets,
  ];
}

function upsertTarget(target: MonitorTarget) {
  const index = monitorTargets.value.findIndex((item) => item.id === target.id);
  if (index >= 0) {
    monitorTargets.value.splice(index, 1, target);
  } else {
    monitorTargets.value.unshift(target);
  }
}

async function loadMonitorTargets(projectId: number, force = false) {
  const cached = monitorTargets.value.filter((target) => target.projectId === projectId);
  if (cached.length > 0 && !force) {
    return cached;
  }
  if (!monitorTargetRequests.has(projectId) || force) {
    loadingMonitorTargets.value = true;
    monitorTargetRequests.set(
      projectId,
      listMonitorTargetsApi(projectId)
        .then((targets) => {
          replaceProjectTargets(projectId, targets);
          return targets;
        })
        .catch(() => [])
        .finally(() => {
          loadingMonitorTargets.value = false;
          monitorTargetRequests.delete(projectId);
        }),
    );
  }
  return monitorTargetRequests.get(projectId)!;
}

async function saveMonitorTarget(projectId: number, payload: MonitorTargetPayload, editing: MonitorTarget | null) {
  try {
    const target = editing
      ? await updateMonitorTargetApi(editing.id, payload)
      : await createMonitorTargetApi(projectId, payload);
    upsertTarget(target);
    message.success(editing ? '监控目标已更新' : '监控目标已创建');
    return true;
  } catch (error) {
    message.error(error instanceof Error ? error.message : '监控目标保存失败');
    return false;
  }
}

async function checkMonitorTarget(target: MonitorTarget) {
  try {
    upsertTarget(await checkMonitorTargetApi(target.id));
    message.success('探活已完成');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '探活失败');
  }
}

async function deleteMonitorTarget(target: MonitorTarget) {
  try {
    await confirmAction({
      title: '删除监控目标',
      content: `确认删除「${target.name}」？`,
      okText: '删除',
      okType: 'danger',
    });
    await deleteMonitorTargetApi(target.id);
    monitorTargets.value = monitorTargets.value.filter((item) => item.id !== target.id);
    message.success('监控目标已删除');
  } catch {
  }
}

export function useMonitoring() {
  const enabledMonitorTargets = computed(() => monitorTargets.value.filter((target) => target.enabled && target.lastCheckStatus !== 'FAILED'));
  return {
    monitorTargets,
    enabledMonitorTargets,
    loadingMonitorTargets,
    loadMonitorTargets,
    saveMonitorTarget,
    checkMonitorTarget,
    deleteMonitorTarget,
  };
}
