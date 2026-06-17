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
          <el-segmented v-model="detailMode" :options="detailModeOptions" @change="onModeChange" />
          <el-button
            type="danger"
            plain
            @click="editor.confirmDeleteStep(step.id)"
          >
            删除步骤
          </el-button>
        </div>
      </div>

      <el-form v-if="detailMode === 'visual'" class="step-config-form" label-position="top" @submit.prevent>
        <el-form-item label="步骤名称">
          <el-input v-model="step.name" />
        </el-form-item>

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
          <el-form-item label="CSV 文件">
            <el-input
              :model-value="(step.config.fileName as string)"
              placeholder="data/users.csv"
              @update:model-value="updateConfig('fileName', $event)"
            />
          </el-form-item>
          <el-form-item label="变量名">
            <el-input
              :model-value="(step.config.variableNames as string)"
              placeholder="userId,token,amount"
              @update:model-value="updateConfig('variableNames', $event)"
            />
          </el-form-item>
        </template>

        <template v-else-if="step.type === 'USER_PARAMS'">
          <el-form-item label="用户参数">
            <el-input
              :model-value="(step.config.paramsText as string)"
              type="textarea"
              :rows="6"
              placeholder="mobile=13800000000&#10;channel=APP"
              @update:model-value="updateConfig('paramsText', $event)"
            />
          </el-form-item>
        </template>

        <template v-else-if="step.type === 'HEADER_CONFIG'">
          <el-form-item label="Header 配置">
            <el-input
              :model-value="(step.config.headersText as string)"
              type="textarea"
              :rows="6"
              placeholder="Content-Type: application/json&#10;Authorization: Bearer ${token}"
              @update:model-value="updateConfig('headersText', $event)"
            />
          </el-form-item>
        </template>
      </el-form>

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
