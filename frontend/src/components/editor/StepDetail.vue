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
        <el-button
          type="danger"
          plain
          @click="editor.confirmDeleteStep(step.id)"
        >
          删除步骤
        </el-button>
      </div>

      <el-form class="step-config-form" label-position="top" @submit.prevent>
        <el-form-item label="步骤名称">
          <el-input v-model="step.name" />
        </el-form-item>

        <template v-if="step.type === 'THREAD_GROUP'">
          <div class="step-config-grid">
            <el-form-item label="线程数">
              <el-input-number
                :model-value="(step.config.threads as number)"
                :min="1"
                :step="10"
                controls-position="right"
                @update:model-value="updateConfig('threads', $event)"
              />
            </el-form-item>
            <el-form-item label="Ramp-Up（秒）">
              <el-input-number
                :model-value="(step.config.rampUp as number)"
                :min="0"
                :step="10"
                controls-position="right"
                @update:model-value="updateConfig('rampUp', $event)"
              />
            </el-form-item>
            <el-form-item label="循环次数">
              <el-input-number
                :model-value="(step.config.loops as number)"
                :min="1"
                controls-position="right"
                @update:model-value="updateConfig('loops', $event)"
              />
            </el-form-item>
            <el-form-item label="持续时间（秒）">
              <el-input-number
                :model-value="(step.config.duration as number)"
                :min="0"
                :step="60"
                controls-position="right"
                @update:model-value="updateConfig('duration', $event)"
              />
            </el-form-item>
          </div>
        </template>

        <template v-else-if="step.type === 'HTTP_REQUEST'">
          <HttpRequestConfig :step="step" />
        </template>

        <template v-else-if="step.type === 'ASSERTION'">
          <el-form-item label="断言目标">
            <el-input
              :model-value="(step.config.target as string)"
              placeholder="响应体 / 响应码 / Header"
              @update:model-value="updateConfig('target', $event)"
            />
          </el-form-item>
          <el-form-item label="匹配规则">
            <el-input
              :model-value="(step.config.rule as string)"
              type="textarea"
              :rows="4"
              placeholder="例如 $.code == 0"
              @update:model-value="updateConfig('rule', $event)"
            />
          </el-form-item>
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
    </template>

    <div v-else class="empty-detail">
      <h2>未选择步骤</h2>
      <p>从左侧选择一个线程组或请求步骤后维护配置。</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useScriptEditor } from '../../composables/useScriptEditor';
import { stepTypeMeta } from '../../constants';
import HttpRequestConfig from './HttpRequestConfig.vue';
import StepTypeIcon from '../scripts/StepTypeIcon.vue';

const editor = useScriptEditor();
const step = computed(() => editor.selectedEditorStep.value);
const meta = computed(() => (step.value ? stepTypeMeta[step.value.type] : stepTypeMeta.HTTP_REQUEST));

function updateConfig(key: string, value: string | number | null | undefined) {
  if (!step.value || value === null || value === undefined) {
    return;
  }
  step.value.config = { ...step.value.config, [key]: value };
}
</script>
