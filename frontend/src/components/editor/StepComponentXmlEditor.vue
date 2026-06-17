<template>
  <div class="component-xml-panel" v-loading="xmlLoading">
    <div class="component-xml-toolbar">
      <span>{{ step.name }} · 含子组件 hashTree</span>
      <div>
        <el-button @click="formatComponentXml">格式化</el-button>
        <el-button type="primary" :loading="xmlSaving" @click="applyComponentXml">应用 XML</el-button>
      </div>
    </div>
    <div class="component-xml-editor" :class="{ 'is-empty': !componentXml }">
      <pre ref="xmlHighlightRef" class="component-xml-highlight" v-html="highlightedXml" />
      <textarea
        v-model="componentXml"
        class="component-xml-control"
        spellcheck="false"
        @input="xmlDirty = true"
        @scroll="syncXmlScroll"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { ScriptStep } from '../../types';
import { useAuth } from '../../composables/useAuth';
import { useScriptEditor } from '../../composables/useScriptEditor';
import { useWorkspace } from '../../composables/useWorkspace';
import {
  getScriptContentApi,
  getScriptDefinitionApi,
  mapScriptDefinition,
  saveScriptContentApi,
} from '../../api/scripts';
import { extractStepXml, formatXml, patchStepXml } from '../../utils/jmeter-component-xml';
import { findStepById } from '../../utils/script-steps';
import { highlightContent } from '../../utils/syntax-highlight';

const props = defineProps<{
  step: ScriptStep;
}>();

const editor = useScriptEditor();
const { scriptAssets } = useWorkspace();
const { currentUser } = useAuth();
const componentXml = ref('');
const xmlDirty = ref(false);
const xmlLoading = ref(false);
const xmlSaving = ref(false);
const xmlHighlightRef = ref<HTMLElement | null>(null);
const highlightedXml = computed(() => highlightContent(componentXml.value, 'xml'));

onMounted(loadComponentXml);

async function loadComponentXml() {
  const script = editor.editorScriptAsset.value;
  if (!script) {
    return;
  }
  xmlLoading.value = true;
  try {
    const selectedId = props.step.id;
    if (!(await editor.saveEditorScript())) {
      return;
    }
    const currentScript = editor.editorScriptAsset.value;
    if (!currentScript) {
      return;
    }
    const content = await getScriptContentApi(currentScript.projectId, currentScript.id);
    componentXml.value = extractStepXml(content.content, selectedId);
    xmlDirty.value = false;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'XML 加载失败');
  } finally {
    xmlLoading.value = false;
  }
}

function formatComponentXml() {
  try {
    componentXml.value = formatXml(componentXml.value);
    xmlDirty.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'XML 格式化失败');
  }
}

async function applyComponentXml() {
  const script = editor.editorScriptAsset.value;
  if (!script) {
    return false;
  }
  xmlSaving.value = true;
  try {
    const selectedId = props.step.id;
    const content = await getScriptContentApi(script.projectId, script.id);
    const patched = patchStepXml(content.content, selectedId, componentXml.value);
    await saveScriptContentApi(
      script.projectId,
      script.id,
      script.sourceFile,
      patched,
      currentUser.value?.username ?? 'admin',
    );
    await refreshScriptDefinition(script.projectId, script.id, selectedId);
    const latestContent = await getScriptContentApi(script.projectId, script.id);
    componentXml.value = extractStepXml(latestContent.content, selectedId);
    xmlDirty.value = false;
    ElMessage.success('组件 XML 已应用');
    return true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '组件 XML 保存失败');
    return false;
  } finally {
    xmlSaving.value = false;
  }
}

async function refreshScriptDefinition(projectId: number, scriptId: number, selectedId: string) {
  const definition = await getScriptDefinitionApi(projectId, scriptId);
  const saved = mapScriptDefinition(definition);
  const index = scriptAssets.value.findIndex((item) => item.id === saved.id);
  if (index >= 0) {
    scriptAssets.value.splice(index, 1, saved);
  } else {
    scriptAssets.value.unshift(saved);
  }
  editor.selectedEditorStepId.value = findStepById(saved.steps, selectedId) ? selectedId : (saved.steps[0]?.id ?? null);
}

function syncXmlScroll(event: Event) {
  if (!xmlHighlightRef.value) {
    return;
  }
  const element = event.target as HTMLTextAreaElement;
  xmlHighlightRef.value.scrollTop = element.scrollTop;
  xmlHighlightRef.value.scrollLeft = element.scrollLeft;
}
</script>
