import { reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { useWorkspace } from './useWorkspace';
import { useAuth } from './useAuth';
import { mapScriptDefinition, uploadScriptApi } from '../api/scripts';

const scriptImportDialogVisible = ref(false);
const scriptUploading = ref(false);
const scriptFile = ref<File | null>(null);
const scriptFileInputRef = ref<HTMLInputElement | null>(null);

const scriptForm = reactive({
  name: '',
  remark: '',
});

function openScriptImportDialog() {
  resetScriptImportForm();
  scriptImportDialogVisible.value = true;
}

function resetScriptImportForm() {
  scriptFile.value = null;
  scriptUploading.value = false;
  scriptForm.name = '';
  scriptForm.remark = '';
  if (scriptFileInputRef.value) {
    scriptFileInputRef.value.value = '';
  }
}

function handleScriptFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  scriptFile.value = input.files?.[0] ?? null;
}

async function importScriptAsset() {
  const { currentProject, scriptAssets, selectedScriptId } = useWorkspace();
  const { currentUser } = useAuth();
  if (!currentProject.value || !scriptFile.value) {
    return;
  }
  if (!scriptFile.value.name.toLowerCase().endsWith('.jmx')) {
    message.error('文件类型不支持，请上传 .jmx 文件');
    return;
  }

  scriptUploading.value = true;
  try {
    const importedBy = currentUser.value?.username ?? 'admin';
    const definition = await uploadScriptApi(currentProject.value.id, scriptFile.value, importedBy);
    const asset = mapScriptDefinition(definition);
    scriptAssets.value = [asset, ...scriptAssets.value.filter((item) => item.id !== asset.id)];
    selectedScriptId.value = asset.id;
    message.success(`已解析并导入 ${asset.name}`);
    scriptImportDialogVisible.value = false;
  } catch (error) {
    message.error(error instanceof Error ? error.message : '脚本导入失败');
  } finally {
    scriptUploading.value = false;
  }
}

export function useScriptImport() {
  return {
    scriptImportDialogVisible,
    scriptUploading,
    scriptFile,
    scriptFileInputRef,
    scriptForm,
    openScriptImportDialog,
    resetScriptImportForm,
    handleScriptFileChange,
    importScriptAsset,
  };
}
