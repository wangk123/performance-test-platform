import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { delay, nextId, sanitizeFileName } from '../utils/format';
import { parseJmeterFile } from '../utils/jmeter';
import { createStepsFromParsed } from '../utils/script-steps';
import { createVersionRecord } from '../utils/seed';
import { useWorkspace } from './useWorkspace';
import { useAuth } from './useAuth';
import type { ScriptAsset } from '../types';

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
  const { currentProject, scriptAssets, currentProjectScripts, selectedScriptId } = useWorkspace();
  const { currentUser } = useAuth();
  if (!currentProject.value || !scriptFile.value) {
    return;
  }
  if (!scriptFile.value.name.toLowerCase().endsWith('.jmx')) {
    ElMessage.error('文件类型不支持，请上传 .jmx 文件');
    return;
  }

  scriptUploading.value = true;
  try {
    const cleanFileName = sanitizeFileName(scriptFile.value.name);
    const scriptName = scriptForm.name.trim() || cleanFileName.replace(/\.jmx$/i, '');
    const parsed = await parseJmeterFile(scriptFile.value, scriptName);
    await delay(260);

    const now = new Date().toISOString();
    const importedBy = currentUser.value?.username ?? 'admin';
    const existing = currentProjectScripts.value.find((script) => script.name === scriptName);
    if (existing) {
      existing.latestVersion += 1;
      existing.sourceFile = cleanFileName;
      existing.parseStatus = parsed.parseStatus;
      existing.threadGroups = parsed.threadGroups;
      existing.apis = parsed.apis;
      existing.monitors = parsed.monitors;
      existing.variables = parsed.variables;
      existing.params = parsed.params;
      existing.steps = createStepsFromParsed(
        scriptName,
        parsed.threadGroups,
        parsed.apis,
        parsed.variables,
      );
      existing.remark = scriptForm.remark;
      existing.updatedAt = now;
      existing.versions.unshift(
        createVersionRecord(existing.latestVersion, cleanFileName, scriptFile.value, now, scriptForm.remark, importedBy),
      );
      selectedScriptId.value = existing.id;
      ElMessage.success(`已解析并更新 ${scriptName} v${existing.latestVersion}`);
    } else {
      const asset: ScriptAsset = {
        id: nextId(scriptAssets.value),
        projectId: currentProject.value.id,
        name: scriptName,
        sourceFile: cleanFileName,
        latestVersion: 1,
        parseStatus: parsed.parseStatus,
        remark: scriptForm.remark,
        updatedAt: now,
        threadGroups: parsed.threadGroups,
        apis: parsed.apis,
        monitors: parsed.monitors,
        variables: parsed.variables,
        params: parsed.params,
        versions: [createVersionRecord(1, cleanFileName, scriptFile.value, now, scriptForm.remark, importedBy)],
        steps: createStepsFromParsed(scriptName, parsed.threadGroups, parsed.apis, parsed.variables),
      };
      scriptAssets.value.unshift(asset);
      selectedScriptId.value = asset.id;
      ElMessage.success(`已解析并导入 ${scriptName}`);
    }

    scriptImportDialogVisible.value = false;
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
