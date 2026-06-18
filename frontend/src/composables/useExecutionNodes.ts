import { computed, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { ExecutionNode, ExecutionNodeRole } from '../types';
import { checkExecutionNodeApi, listExecutionNodesApi, registerExecutionNodeApi } from '../api/execution-nodes';

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

  async function registerNode() {
    if (!form.name.trim() || !form.host.trim() || !form.sshUsername.trim() || !form.sshKeyPath.trim()) {
      message.error('请完整填写节点信息');
      return false;
    }
    const node = await registerExecutionNodeApi({
      name: form.name.trim(),
      host: form.host.trim(),
      sshPort: form.sshPort,
      sshUsername: form.sshUsername.trim(),
      sshKeyPath: form.sshKeyPath.trim(),
      role: form.role,
      remoteWorkDir: form.remoteWorkDir.trim() || '/tmp/perftest-platform',
    });
    nodes.value = [node, ...nodes.value.filter((item) => item.id !== node.id)];
    message.success('执行节点已注册');
    return true;
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
    controllerNodes,
    workerNodes,
    loadNodes,
    registerNode,
    checkNode,
  };
}
