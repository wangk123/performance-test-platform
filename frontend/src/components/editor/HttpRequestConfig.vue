<template>
  <div class="http-config">
    <div class="http-request-line">
      <el-form-item label="请求方法">
        <el-select :model-value="config.method" @update:model-value="setConfig('method', $event)">
          <el-option v-for="method in httpMethods" :key="method" :label="method" :value="method" />
        </el-select>
      </el-form-item>
      <el-form-item label="请求 URL">
        <VariableField
          id="url"
          :value="config.url"
          placeholder="${host}/api/example"
          :active-field="activeField"
          :active-index="activeIndex"
          :suggestions="suggestionVariables"
          @change="setConfig('url', $event)"
          @active="updateActiveField"
          @choose="chooseSuggestion"
          @move="moveSuggestion"
          @close="closeSuggestion"
        />
      </el-form-item>
    </div>

    <div class="http-config-layout">
      <el-tabs v-model="activeTab" class="http-config-tabs">
        <el-tab-pane label="Params" name="params">
          <HttpKeyValueEditor
            kind="params"
            :items="config.params"
            key-placeholder="pageNo"
            value-placeholder="${pageNo}"
            description-placeholder="参数说明"
            add-text="新增参数"
            :active-field="activeField"
            :active-index="activeIndex"
            :suggestions="suggestionVariables"
            @update="(index, field, value) => updateConfigList('params', index, field, value)"
            @remove="removeConfigListItem('params', $event)"
            @add="addConfigListItem('params')"
            @active="updateActiveField"
            @choose="chooseSuggestion"
            @move="moveSuggestion"
            @close="closeSuggestion"
          />
        </el-tab-pane>

        <el-tab-pane label="Headers" name="headers">
          <HttpKeyValueEditor
            kind="headers"
            :items="config.headers"
            key-placeholder="Content-Type"
            value-placeholder="application/json"
            description-placeholder="Header 说明"
            add-text="新增 Header"
            :active-field="activeField"
            :active-index="activeIndex"
            :suggestions="suggestionVariables"
            @update="(index, field, value) => updateConfigList('headers', index, field, value)"
            @remove="removeConfigListItem('headers', $event)"
            @add="addConfigListItem('headers')"
            @active="updateActiveField"
            @choose="chooseSuggestion"
            @move="moveSuggestion"
            @close="closeSuggestion"
          />
        </el-tab-pane>

        <el-tab-pane label="Body" name="body">
          <HttpBodyConfig
            :body-type="config.bodyType"
            :raw-body-type="config.rawBodyType"
            :body="config.body"
            :body-params="config.bodyParams"
            :body-placeholder="bodyPlaceholder"
            :active-field="activeField"
            :active-index="activeIndex"
            :suggestions="suggestionVariables"
            @update-body-type="updateBodyType"
            @update-raw-body-type="updateRawBodyType"
            @update-body="setConfig('body', $event)"
            @update-body-param="(index, field, value) => updateConfigList('bodyParams', index, field, value)"
            @remove-body-param="removeConfigListItem('bodyParams', $event)"
            @add-body-param="addConfigListItem('bodyParams')"
            @active="updateActiveField"
            @choose="chooseSuggestion"
            @move="moveSuggestion"
            @close="closeSuggestion"
            @format-body="formatBody"
          />
        </el-tab-pane>

        <el-tab-pane label="Advanced" name="advanced">
          <HttpAdvancedConfig :advanced="config.advanced" @update="updateAdvanced" />
        </el-tab-pane>
      </el-tabs>

      <VariablePanel :project-variables="projectVariables" @insert="insertVariable" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import type {
  HttpAdvancedConfig as HttpAdvancedConfigType,
  HttpBodyType,
  HttpParamConfig,
  HttpRawBodyType,
  HttpRequestConfig,
  ScriptStep,
} from '../../types';
import type { ActiveVariableField, VariableOption } from '../../utils/http-request-config';
import {
  createEmptyHttpParam,
  formatBodyContent,
  normalizeBodyType,
  normalizeRawBodyType,
  preferredHttpConfigTab,
  systemVariables,
  syncHeadersContentType,
} from '../../utils/http-request-config';
import { useScriptEditor } from '../../composables/useScriptEditor';
import HttpAdvancedConfig from './HttpAdvancedConfig.vue';
import HttpBodyConfig from './HttpBodyConfig.vue';
import HttpKeyValueEditor from './HttpKeyValueEditor.vue';
import VariableField from './VariableField.vue';
import VariablePanel from './VariablePanel.vue';

type ConfigListKey = 'params' | 'headers' | 'bodyParams';
type ConfigListField = keyof HttpParamConfig;

const props = defineProps<{ step: ScriptStep }>();
const editor = useScriptEditor();
const activeTab = ref('params');
const activeField = ref<ActiveVariableField | null>(null);
const activeIndex = ref(0);
const httpMethods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'];

const projectVariables = computed<VariableOption[]>(() => {
  const script = editor.editorScriptAsset.value;
  if (!script) {
    return [];
  }
  return [
    ...script.variables.map((item) => ({ key: item.key, label: item.key, value: String(item.value) })),
    ...script.params.map((item) => ({ key: item.key, label: item.label, value: String(item.value) })),
  ];
});

const allVariables = computed(() => [...systemVariables, ...projectVariables.value]);
const bodyPlaceholder = computed(() =>
  config.value.rawBodyType === 'xml' ? '<user><name>${name}</name></user>' : '{"mobile":"${mobile}"}',
);
const suggestionVariables = computed(() => {
  const query = activeField.value?.query.toLowerCase() ?? '';
  return allVariables.value.filter((item) => item.key.toLowerCase().includes(query) || item.label.includes(query));
});
const config = computed(() => normalizeHttpConfig(props.step));

watch(
  () => props.step.id,
  () => {
    const nextConfig = normalizeHttpConfig(props.step);
    activeTab.value = preferredHttpConfigTab(nextConfig);
  },
  { immediate: true },
);

function normalizeHttpConfig(step: ScriptStep): HttpRequestConfig {
  const oldDomain = String(step.config.domain ?? '');
  const oldPath = String(step.config.path ?? '');
  const legacyUrl = `${oldDomain}${oldPath}`;
  const normalizedBody = normalizeBodyType(String(step.config.bodyType ?? 'none'));
  const rawBodyType = normalizeRawBodyType(String(step.config.rawBodyType ?? normalizedBody.rawBodyType));
  const headers = syncHeadersContentType(
    Array.isArray(step.config.headers) ? step.config.headers : [],
    normalizedBody.bodyType,
    rawBodyType,
  );
  const next = {
    method: String(step.config.method ?? 'GET'),
    url: String(step.config.url ?? (legacyUrl || '${host}/api/example')),
    params: Array.isArray(step.config.params) ? step.config.params : [],
    headers,
    bodyType: normalizedBody.bodyType,
    rawBodyType,
    body: String(step.config.body ?? ''),
    bodyParams: Array.isArray(step.config.bodyParams) ? step.config.bodyParams : [],
    advanced: {
      connectTimeout: 30000,
      responseTimeout: 30000,
      followRedirects: true,
      keepAlive: true,
      ...(typeof step.config.advanced === 'object' && !Array.isArray(step.config.advanced) ? step.config.advanced : {}),
    },
  };
  step.config = { ...step.config, ...next };
  return next;
}

function setConfig(key: keyof HttpRequestConfig, value: string | HttpParamConfig[] | HttpAdvancedConfigType) {
  props.step.config = { ...props.step.config, [key]: value };
}

function updateConfigList(listKey: ConfigListKey, index: number, field: ConfigListField, value: string | boolean) {
  const list = [...config.value[listKey]];
  list[index] = { ...list[index], [field]: value };
  setConfig(listKey, list);
}

function addConfigListItem(listKey: ConfigListKey) {
  setConfig(listKey, [...config.value[listKey], createEmptyHttpParam()]);
}

function removeConfigListItem(listKey: ConfigListKey, index: number) {
  setConfig(listKey, config.value[listKey].filter((_, itemIndex) => itemIndex !== index));
}

function updateAdvanced(key: keyof HttpAdvancedConfigType, value: number | boolean | null | undefined) {
  if (value === null || value === undefined) {
    return;
  }
  setConfig('advanced', { ...config.value.advanced, [key]: value });
}

function updateBodyType(value: string | number | boolean | undefined) {
  const bodyType = String(value ?? 'none') as HttpBodyType;
  setConfig('bodyType', bodyType);
  syncContentType(bodyType, config.value.rawBodyType);
  nextTick(formatBody);
}

function updateRawBodyType(value: string | number | boolean | undefined) {
  const rawBodyType = normalizeRawBodyType(String(value ?? 'json'));
  setConfig('rawBodyType', rawBodyType);
  syncContentType(config.value.bodyType, rawBodyType);
  nextTick(formatBody);
}

function formatBody() {
  const formatted = formatBodyContent(config.value.rawBodyType, config.value.body);
  if (formatted !== config.value.body) {
    setConfig('body', formatted);
  }
}

function syncContentType(bodyType: HttpBodyType, rawBodyType: HttpRawBodyType) {
  setConfig('headers', syncHeadersContentType(config.value.headers, bodyType, rawBodyType));
}

function insertVariable(key: string) {
  chooseSuggestion(key);
}

function chooseSuggestion(key = suggestionVariables.value[activeIndex.value]?.key) {
  if (!activeField.value || !key) {
    return;
  }
  const { element, triggerStart } = activeField.value;
  const end = element.selectionStart ?? element.value.length;
  const nextValue = `${element.value.slice(0, triggerStart)}\${${key}}${element.value.slice(end)}`;
  element.value = nextValue;
  element.dispatchEvent(new Event('input', { bubbles: true }));
  nextTick(() => {
    const caret = triggerStart + key.length + 3;
    element.focus();
    element.setSelectionRange(caret, caret);
    updateActiveField(activeField.value?.id ?? '', element);
  });
}

function updateActiveField(id: string, element: HTMLInputElement | HTMLTextAreaElement) {
  const caret = element.selectionStart ?? element.value.length;
  const beforeCaret = element.value.slice(0, caret);
  const match = beforeCaret.match(/\$\{([\w.-]*)$/);
  activeField.value = match
    ? { id, element, triggerStart: caret - match[0].length, query: match[1], suggesting: true }
    : { id, element, triggerStart: caret, query: '', suggesting: false };
  activeIndex.value = 0;
}

function moveSuggestion(offset: number) {
  const count = suggestionVariables.value.length;
  if (count) {
    activeIndex.value = (activeIndex.value + offset + count) % count;
  }
}

function closeSuggestion() {
  activeField.value = null;
}
</script>
