<template>
  <section class="script-step-detail panel">
    <template v-if="step">
      <div class="detail-heading">
        <div class="detail-heading-text">
          <div class="step-detail-tag" :class="`tone-${meta.tone}`">
            <StepTypeIcon :type="step.type" />
            <span>{{ meta.label }}</span>
          </div>
          <h2>{{ step.name }}</h2>
          <a-tag v-if="step.config.enabled === false" class="step-disabled-tag" color="red">已禁用</a-tag>
        </div>
        <div class="detail-heading-actions">
          <div class="editor-mode-switch">
            <a-segmented v-model:value="detailMode" :options="detailModeOptions" size="small" />
          </div>
          <a-tooltip title="保存草稿">
            <a-button class="editor-toolbar-button" type="primary" size="small" :loading="saving" @click="emit('save')">
              保存
            </a-button>
          </a-tooltip>
        </div>
      </div>

      <a-alert
        v-if="saveWarnings.length"
        type="warning"
        show-icon
        closable
        class="save-warnings-alert"
        :message="saveWarnings[0]"
        @close="dismissSaveWarnings"
      />

      <a-form v-if="detailMode === 'visual'" class="step-config-form" layout="vertical" @submit.prevent>
        <a-form-item label="步骤名称">
          <a-input v-model:value="step.name" />
        </a-form-item>

        <template v-if="step.type === 'THREAD_GROUP'">
          <ThreadGroupEditor :config="threadGroupConfig" @update:config="updateThreadGroupConfig" />
        </template>

        <template v-else-if="step.type === 'HTTP_REQUEST'">
          <HttpRequestConfig :step="step" />
        </template>

        <template v-else-if="step.type === 'ASSERTION'">
          <ResponseAssertionConfig :step="step" />
        </template>

        <template v-else-if="step.type === 'JSON_ASSERTION'">
          <JsonAssertionConfig :step="step" />
        </template>

        <template v-else-if="step.type === 'CSV_DATA'">
          <a-alert
            type="info"
            show-icon
            message="文件名与变量在此定义；数据文件本体在 计划 → 场景 → CSV 数据文件 中绑定分发"
            class="csv-binding-alert"
          />
          <a-form-item label="CSV 文件">
            <a-input
              :value="(step.config.fileName as string)"
              placeholder="data/users.csv"
              @update:value="updateConfig('fileName', $event)"
            />
          </a-form-item>
          <a-form-item label="变量名">
            <a-input
              :value="(step.config.variableNames as string)"
              placeholder="userId,token,amount"
              @update:value="updateConfig('variableNames', $event)"
            />
          </a-form-item>
          <a-collapse class="csv-advanced-collapse">
            <a-collapse-panel key="advanced" header="高级属性">
              <a-form-item label="分隔符">
                <a-input
                  :value="(step.config.delimiter as string | undefined) ?? ','"
                  placeholder=","
                  @update:value="updateConfig('delimiter', $event)"
                />
              </a-form-item>
              <a-form-item label="文件编码">
                <a-select
                  :value="(step.config.fileEncoding as string | undefined) ?? 'UTF-8'"
                  @change="updateConfig('fileEncoding', $event)"
                >
                  <a-select-option value="UTF-8">UTF-8</a-select-option>
                  <a-select-option value="GBK">GBK</a-select-option>
                  <a-select-option value="ISO-8859-1">ISO-8859-1</a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="共享模式">
                <a-select
                  :value="(step.config.shareMode as string | undefined) ?? 'shareMode.all'"
                  @change="updateConfig('shareMode', $event)"
                >
                  <a-select-option value="shareMode.all">所有线程</a-select-option>
                  <a-select-option value="shareMode.thread">当前线程</a-select-option>
                  <a-select-option value="shareMode.threadGroup">线程组</a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="忽略首行" extra="开启后跳过 CSV 第一行表头">
                <a-switch
                  :checked="csvBoolean('ignoreFirstLine', true)"
                  @update:checked="updateConfig('ignoreFirstLine', $event)"
                />
              </a-form-item>
              <a-form-item label="循环读取" extra="读至文件末尾后回到开头继续取值">
                <a-switch
                  :checked="csvBoolean('recycle', true)"
                  @update:checked="updateConfig('recycle', $event)"
                />
              </a-form-item>
              <a-form-item label="读完停止线程" extra="不循环时读取完文件后停止该线程">
                <a-switch
                  :checked="csvBoolean('stopThread', false)"
                  @update:checked="updateConfig('stopThread', $event)"
                />
              </a-form-item>
            </a-collapse-panel>
          </a-collapse>
        </template>

        <template v-else-if="step.type === 'USER_PARAMS'">
          <UserParamsConfig :step="step" />
        </template>

        <template v-else-if="step.type === 'USER_VARIABLES'">
          <KeyValueGrid
            :items="userVariableItems"
            key-label="变量名"
            value-label="值"
            key-placeholder="host"
            value-placeholder="api.example.com"
            add-label="添加变量"
            with-description
            @change="updateUserVariables"
          />
        </template>

        <template v-else-if="step.type === 'HEADER_CONFIG'">
          <KeyValueGrid
            :items="headerItems"
            key-label="Header 名称"
            value-label="值"
            key-placeholder="Content-Type"
            value-placeholder="application/json"
            add-label="添加 Header"
            @change="updateHeaders"
          />
        </template>

        <template v-else-if="step.type === 'CONSTANT_TIMER'">
          <a-form-item label="线程延迟（毫秒）" extra="作用域内每次采样前固定停留的时长">
            <a-input-number
              class="timer-number-input"
              :value="timerNumber('delay', 300)"
              :min="0"
              :step="100"
              @update:value="updateConfig('delay', String($event ?? 0))"
            />
          </a-form-item>
        </template>

        <template v-else-if="step.type === 'RANDOM_TIMER'">
          <a-form-item label="随机延迟基准（毫秒）" extra="每次停留时长的固定基准部分">
            <a-input-number
              class="timer-number-input"
              :value="timerNumber('delay', 1000)"
              :min="0"
              :step="100"
              @update:value="updateConfig('delay', String($event ?? 0))"
            />
          </a-form-item>
          <a-form-item label="随机浮动范围（毫秒）" extra="实际停留 = 基准 + 0 ～ 范围内的随机值，与 JMeter Uniform Random Timer 一致">
            <a-input-number
              class="timer-number-input"
              :value="timerNumber('range', 500)"
              :min="0"
              :step="100"
              @update:value="updateConfig('range', String($event ?? 0))"
            />
          </a-form-item>
        </template>

        <template v-else-if="step.type === 'JSR223_PRE_PROCESSOR' || step.type === 'JSR223_POST_PROCESSOR'">
          <a-form-item label="脚本语言">
            <div class="jsr223-language-row">
              <a-tag v-if="scriptLanguage === 'groovy'" class="jsr223-language-tag" color="geekblue">groovy</a-tag>
              <template v-else>
                <a-tag class="jsr223-language-tag">{{ scriptLanguage }}</a-tag>
                <a-button size="small" @click="updateConfig('scriptLanguage', 'groovy')">切换为 groovy</a-button>
              </template>
            </div>
          </a-form-item>
          <a-form-item label="参数（parameters · 逗号分隔，脚本内 args[] 取用）">
            <a-input
              class="jsr223-parameters-input"
              :value="(step.config.parameters as string)"
              placeholder="env=prod,algo=HmacSHA256"
              @update:value="updateConfig('parameters', $event)"
            />
          </a-form-item>
          <a-form-item label="脚本">
            <div class="jsr223-script-layout">
              <div class="jsr223-script-editor">
                <CodeEditor
                  ref="codeRef"
                  language="groovy"
                  :model-value="(step.config.script as string)"
                  placeholder="// Groovy 脚本，密钥请使用 ${__P(...)} 参数化引用"
                  @update:model-value="updateConfig('script', $event)"
                />
              </div>
              <Jsr223SnippetPanel :on-insert="insertSnippet" />
            </div>
          </a-form-item>
        </template>
      </a-form>

      <StepComponentXmlEditor v-else :step="step" />
    </template>

    <div v-else class="empty-detail">
      <h2>未选择步骤</h2>
      <p>从左侧选择一个线程组或请求步骤后维护配置。</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useScriptEditor } from '../../composables/useScriptEditor';
import { stepTypeMeta } from '../../constants';
import type { ThreadGroup } from '../../types';
import HttpRequestConfig from './HttpRequestConfig.vue';
import ResponseAssertionConfig from './ResponseAssertionConfig.vue';
import JsonAssertionConfig from './JsonAssertionConfig.vue';
import StepComponentXmlEditor from './StepComponentXmlEditor.vue';
import StepTypeIcon from '../scripts/StepTypeIcon.vue';
import ThreadGroupEditor from './ThreadGroupEditor.vue';
import CodeEditor from './CodeEditor.vue';
import Jsr223SnippetPanel from './Jsr223SnippetPanel.vue';
import UserParamsConfig from './UserParamsConfig.vue';
import KeyValueGrid, { type KeyValueItem } from './KeyValueGrid.vue';

defineProps<{
  saving: boolean;
}>();

const emit = defineEmits<{
  save: [];
}>();

const editor = useScriptEditor();
const step = computed(() => editor.selectedEditorStep.value);
const meta = computed(() => (step.value ? stepTypeMeta[step.value.type] : stepTypeMeta.HTTP_REQUEST));
const saveWarnings = computed(() => editor.saveWarnings.value);
const codeRef = ref<InstanceType<typeof CodeEditor> | null>(null);
const scriptLanguage = computed(() => {
  const raw = step.value?.config.scriptLanguage;
  return typeof raw === 'string' && raw ? raw : 'groovy';
});
const detailMode = ref<'visual' | 'xml'>('visual');
const detailModeOptions = [
  { label: '可视化', value: 'visual' },
  { label: 'XML', value: 'xml' },
];

const threadGroupConfig = computed<ThreadGroup>(() => ({
  name: step.value?.name ?? '',
  threads: Number(step.value?.config.threads ?? 1),
  rampUp: Number(step.value?.config.rampUp ?? 0),
  loops: Number(step.value?.config.loops ?? 1),
  duration: Number(step.value?.config.duration ?? 0),
  scheduler: Boolean(step.value?.config.scheduler ?? false),
  mode: step.value?.config.mode === 'stepping' || step.value?.config.mode === 'duration'
    ? step.value.config.mode
    : (step.value?.config.scheduler ? 'duration' : 'count'),
  stepping: typeof step.value?.config.stepping === 'object'
    ? step.value.config.stepping as ThreadGroup['stepping']
    : undefined,
}));

function updateThreadGroupConfig(config: ThreadGroup) {
  if (!step.value) {
    return;
  }
  const nextConfig: typeof step.value.config = {
    ...step.value.config,
    threads: config.threads,
    rampUp: config.rampUp,
    loops: config.scheduler ? -1 : config.loops,
    duration: config.duration,
    scheduler: config.scheduler ?? false,
    mode: config.mode ?? (config.scheduler ? 'duration' : 'count'),
  };
  if (config.stepping) {
    nextConfig.stepping = config.stepping;
  }
  step.value.config = nextConfig;
}

function updateConfig(key: string, value: string | number | boolean | null | undefined) {
  if (!step.value || value === null || value === undefined) {
    return;
  }
  step.value.config = { ...step.value.config, [key]: value };
}

// 旧 Header 数据只有 headersText（k: v 文本）：读取时转换，保存落结构化数组
const headerItems = computed<KeyValueItem[]>(() => {
  const config = step.value?.config;
  if (!config) {
    return [];
  }
  if (Array.isArray(config.headers)) {
    return (config.headers as Array<Record<string, unknown>>).map((item) => ({
      key: String(item.key ?? ''),
      value: String(item.value ?? ''),
      description: '',
    }));
  }
  const text = typeof config.headersText === 'string' ? config.headersText : '';
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const separator = line.indexOf(':') >= 0 ? line.indexOf(':') : line.indexOf('=');
      return separator > 0
        ? { key: line.substring(0, separator).trim(), value: line.substring(separator + 1).trim(), description: '' }
        : { key: line, value: '', description: '' };
    });
});

function updateHeaders(items: KeyValueItem[]) {
  if (!step.value) {
    return;
  }
  step.value.config = {
    ...step.value.config,
    headers: items.map((item) => ({ enabled: true, key: item.key, value: item.value, description: '' })),
    headersText: undefined,
  };
}

const userVariableItems = computed<KeyValueItem[]>(() => {
  const variables = step.value?.config.variables;
  if (!Array.isArray(variables)) {
    return [];
  }
  return (variables as Array<Record<string, unknown>>).map((item) => ({
    key: String(item.key ?? ''),
    value: String(item.value ?? ''),
    description: String(item.description ?? ''),
  }));
});

function updateUserVariables(items: KeyValueItem[]) {
  if (!step.value) {
    return;
  }
  step.value.config = {
    ...step.value.config,
    variables: items.map((item) => ({ enabled: true, key: item.key, value: item.value, description: item.description ?? '' })),
  };
}

function timerNumber(key: 'delay' | 'range', fallback: number): number {
  const raw = step.value?.config[key];
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

function csvBoolean(key: 'ignoreFirstLine' | 'recycle' | 'stopThread', fallback: boolean): boolean {
  const raw = step.value?.config[key];
  if (raw === undefined) {
    return fallback;
  }
  return raw === true || raw === 'true';
}

function dismissSaveWarnings() {
  editor.saveWarnings.value = [];
}

function insertSnippet(code: string) {
  codeRef.value?.insertAtCursor(`\n${code}\n`);
}

watch(
  () => step.value?.id,
  () => {
    detailMode.value = 'visual';
  },
);

</script>

<style scoped>
.csv-binding-alert {
  margin-bottom: 16px;
}

.timer-number-input {
  width: 220px;
  font-family: var(--font-data);
}

.save-warnings-alert {
  margin-top: 12px;
}

.csv-advanced-collapse {
  margin-top: 4px;
}

.jsr223-language-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.jsr223-language-tag {
  margin-inline-end: 0;
  font-family: var(--font-data);
}

.jsr223-parameters-input :deep(input) {
  font-family: var(--font-data);
}

.jsr223-script-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 12px;
  align-items: stretch;
}

.jsr223-script-editor {
  min-height: 320px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--code-bg);
}

.jsr223-script-editor .code-editor {
  width: 100%;
  height: 100%;
}

@media (max-width: 1280px) {
  .jsr223-script-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
