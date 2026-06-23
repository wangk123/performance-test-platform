import { computed, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { ExecutionNode, ExecutionNodeRole } from '../types';
import {
  checkExecutionNodeApi,
  deleteExecutionNodeApi,
  initializeExecutionNodesApi,
  listExecutionNodesApi,
  registerExecutionNodeApi,
  updateExecutionNodeApi,
} from '../api/execution-nodes';

const nodes = ref<ExecutionNode[]>([]);
const loading = ref(false);
const form = reactive({
  name: '',
  host: '',
  sshPort: 22,
  sshUsername: '',
  sshKeyPath: '',
  role: 'WORKER' as ExecutionNodeRole,
  remoteWorkDir: '/tmp/perftest-platform',
  sshPassword: '',
});
const initializeForm = reactive({
  hosts: '192.168.17.216\n192.168.17.217\n192.168.17.218',
  sshPort: 22,
  sshUsername: 'root',
  sshPassword: '',
  remoteWorkDir: '/tmp/perftest-platform',
});

export function useExecutionNodes() {
  const controllerNodes = computed(() => nodes.value.filter((node) => node.role === 'CONTROLLER' || node.role === 'BOTH'));
  const workerNodes = computed(() => nodes.value.filter((node) => node.role === 'WORKER' || node.role === 'BOTH'));

  async function loadNodes() {
    loading.value = true;
    try {
      nodes.value = await listExecutionNodesApi();
    } finally {
      loading.value = false;
    }
  }

  function resetForm(node?: ExecutionNode) {
    form.name = node?.name ?? '';
    form.host = node?.host ?? '';
    form.sshPort = node?.sshPort ?? 22;
    form.sshUsername = node?.sshUsername ?? 'root';
    form.sshKeyPath = node?.sshKeyPath ?? '';
    form.role = node?.role ?? 'WORKER';
    form.remoteWorkDir = node?.remoteWorkDir ?? '/tmp/perftest-platform';
    form.sshPassword = '';
  }

  async function saveNode(nodeId?: number) {
    if (!form.name.trim() || !form.host.trim() || !form.sshUsername.trim() || !form.sshPassword.trim()) {
      message.error('请完整填写节点信息');
      return false;
    }
    loading.value = true;
    try {
      const payload = {
        name: form.name.trim(),
        host: form.host.trim(),
        sshPort: form.sshPort,
        sshUsername: form.sshUsername.trim(),
        sshKeyPath: form.sshKeyPath.trim(),
        role: form.role,
        remoteWorkDir: form.remoteWorkDir.trim() || '/tmp/perftest-platform',
        sshPassword: form.sshPassword,
      };
      const node = nodeId
        ? await updateExecutionNodeApi(nodeId, payload)
        : await registerExecutionNodeApi(payload);
      nodes.value = [node, ...nodes.value.filter((item) => item.id !== node.id)];
      form.sshPassword = '';
      message.success(nodeId ? '执行器已修改并初始化' : '执行器已新增并初始化');
      return true;
    } finally {
      loading.value = false;
    }
  }

  async function registerNode() {
    return saveNode();
  }

  async function initializeNode(node: ExecutionNode) {
    if (!form.sshPassword.trim()) {
      message.error('请填写一次性 SSH 密码');
      return false;
    }
    loading.value = true;
    try {
      const initialized = await updateExecutionNodeApi(node.id, {
      name: form.name.trim(),
      host: form.host.trim(),
      sshPort: form.sshPort,
      sshUsername: form.sshUsername.trim(),
      sshKeyPath: form.sshKeyPath.trim(),
      role: form.role,
      remoteWorkDir: form.remoteWorkDir.trim() || '/tmp/perftest-platform',
        sshPassword: form.sshPassword,
      });
      nodes.value = nodes.value.map((item) => item.id === initialized.id ? initialized : item);
      form.sshPassword = '';
      message.success('执行器初始化完成');
      return true;
    } finally {
      loading.value = false;
    }
  }

  async function deleteNode(node: ExecutionNode) {
    await deleteExecutionNodeApi(node.id);
    nodes.value = nodes.value.filter((item) => item.id !== node.id);
    message.success('执行器已删除');
  }

  async function initializeNodes() {
    const hosts = initializeForm.hosts.split(/\s|,|，/).map((item) => item.trim()).filter(Boolean);
    if (!hosts.length || !initializeForm.sshUsername.trim() || !initializeForm.sshPassword) {
      message.error('请填写主机、SSH 用户和一次性密码');
      return false;
    }
    loading.value = true;
    try {
      const initialized = await initializeExecutionNodesApi({
        hosts,
        sshPort: initializeForm.sshPort,
        sshUsername: initializeForm.sshUsername.trim(),
        sshPassword: initializeForm.sshPassword,
        remoteWorkDir: initializeForm.remoteWorkDir.trim() || '/tmp/perftest-platform',
      });
      nodes.value = [
        ...initialized,
        ...nodes.value.filter((node) => !initialized.some((item) => item.id === node.id)),
      ];
      initializeForm.sshPassword = '';
      message.success('执行器节点初始化完成');
      return true;
    } finally {
      loading.value = false;
    }
  }

  async function checkNode(node: ExecutionNode) {
    const checked = await checkExecutionNodeApi(node.id);
    nodes.value = nodes.value.map((item) => item.id === checked.id ? checked : item);
    message[checked.status === 'AVAILABLE' ? 'success' : 'error'](checked.lastMessage || '节点检查完成');
  }

  return {
    nodes,
    loading,
    form,
    initializeForm,
    controllerNodes,
    workerNodes,
    loadNodes,
    resetForm,
    saveNode,
    registerNode,
    initializeNode,
    deleteNode,
    initializeNodes,
    checkNode,
  };
}
