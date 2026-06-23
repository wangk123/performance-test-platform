import { reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { useWorkspace } from './useWorkspace';
import { useAuth } from './useAuth';
import { createScriptApi, mapScriptDefinition } from '../api/scripts';

const scriptCreateDialogVisible = ref(false);
const scriptCreating = ref(false);

const scriptCreateForm = reactive({
  name: '',
});

function openScriptCreateDialog() {
  resetScriptCreateForm();
  scriptCreateDialogVisible.value = true;
}

function resetScriptCreateForm() {
  scriptCreating.value = false;
  scriptCreateForm.name = '';
}

async function createScriptAsset() {
  const { currentProject, scriptAssets, selectedScriptId } = useWorkspace();
  const { currentUser } = useAuth();
  const name = scriptCreateForm.name.trim();
  if (!currentProject.value || !name) {
    return;
  }

  scriptCreating.value = true;
  try {
    const createdBy = currentUser.value?.username ?? 'admin';
    const definition = await createScriptApi(currentProject.value.id, name, createdBy);
    const asset = mapScriptDefinition(definition);
    scriptAssets.value = [asset, ...scriptAssets.value.filter((item) => item.id !== asset.id)];
    selectedScriptId.value = asset.id;
    message.success(`已创建脚本 ${asset.name}`);
    scriptCreateDialogVisible.value = false;
  } catch (error) {
    message.error(error instanceof Error ? error.message : '脚本创建失败');
  } finally {
    scriptCreating.value = false;
  }
}

export function useScriptCreate() {
  return {
    scriptCreateDialogVisible,
    scriptCreating,
    scriptCreateForm,
    openScriptCreateDialog,
    resetScriptCreateForm,
    createScriptAsset,
  };
}
