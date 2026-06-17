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
        </div>
        <div class="detail-heading-actions">
          <a-segmented v-model:value="detailMode" :options="detailModeOptions" @change="onModeChange" />
          <a-button
            danger

            @click="editor.confirmDeleteStep(step.id)"
          >
            删除步骤
          </a-button>
        </div>
      </div>

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

        <template v-else-if="step.type === 'CSV_DATA'">
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
        </template>

        <template v-else-if="step.type === 'USER_PARAMS'">
          <a-form-item label="用户参数">
            <a-textarea
              :value="(step.config.paramsText as string)"
              :rows="6"
              placeholder="mobile=13800000000&#10;channel=APP"
              @update:value="updateConfig('paramsText', $event)"
            />
          </a-form-item>
        </template>

        <template v-else-if="step.type === 'HEADER_CONFIG'">
          <a-form-item label="Header 配置">
            <a-textarea
              :value="(step.config.headersText as string)"
              :rows="6"
              placeholder="Content-Type: application/json&#10;Authorization: Bearer ${token}"
              @update:value="updateConfig('headersText', $event)"
            />
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
import StepComponentXmlEditor from './StepComponentXmlEditor.vue';
import StepTypeIcon from '../scripts/StepTypeIcon.vue';
import ThreadGroupEditor from './ThreadGroupEditor.vue';

const editor = useScriptEditor();
const step = computed(() => editor.selectedEditorStep.value);
const meta = computed(() => (step.value ? stepTypeMeta[step.value.type] : stepTypeMeta.HTTP_REQUEST));
const detailMode = ref<'visual' | 'xml'>('visual');
const detailModeOptions = [
  { label: '可视化模式', value: 'visual' },
  { label: 'XML 模式', value: 'xml' },
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

function updateConfig(key: string, value: string | number | null | undefined) {
  if (!step.value || value === null || value === undefined) {
    return;
  }
  step.value.config = { ...step.value.config, [key]: value };
}

watch(
  () => step.value?.id,
  () => {
    detailMode.value = 'visual';
  },
);

function onModeChange(value: string | number | boolean) {
  detailMode.value = value === 'xml' ? 'xml' : 'visual';
}
</script>
