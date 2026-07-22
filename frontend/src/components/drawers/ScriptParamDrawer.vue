<template>
  <a-drawer v-model:open="paramDrawerVisible" title="脚本默认执行参数" size="460px">
    <div v-if="paramScriptAsset" class="param-drawer">
      <div class="drawer-title">
        <h2>{{ paramScriptAsset.name }} · v{{ paramScriptAsset.latestVersion }}</h2>
        <p v-if="currentProject?.code">{{ currentProject.code }}</p>
      </div>

      <a-form layout="vertical" @submit.prevent>
        <a-form-item label="线程数">
          <a-input-number v-model:value="paramForm.threads" :min="0" :step="10" />
        </a-form-item>
        <a-form-item label="循环次数">
          <a-input-number v-model:value="paramForm.loops" :min="0" />
        </a-form-item>
        <a-form-item label="持续时间（秒）">
          <a-input-number v-model:value="paramForm.duration" :min="0" :step="60" />
        </a-form-item>
        <a-form-item label="Ramp-Up（秒）">
          <a-input-number v-model:value="paramForm.rampUp" :min="0" :step="10" />
        </a-form-item>
        <a-form-item label="目标环境">
          <a-select v-model:value="paramForm.environment" placeholder="选择环境">
            <a-select-option label="SIT" value="SIT" />
            <a-select-option label="UAT" value="UAT" />
            <a-select-option label="PRE" value="PRE" />
            <a-select-option label="PROD" value="PROD" />
          </a-select>
        </a-form-item>
        <a-form-item label="JMeter 属性扩展项">
          <a-textarea
            v-model:value="paramForm.extraProperties"
            :rows="5"
            placeholder="host=127.0.0.1&#10;protocol=https"
          />
        </a-form-item>
      </a-form>

      <div class="drawer-actions">
        <a-button @click="paramDrawerVisible = false">取消</a-button>
        <a-button type="primary" @click="saveParams">保存参数</a-button>
      </div>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { useScriptParams } from '../../composables/useScriptParams';
import { useWorkspace } from '../../composables/useWorkspace';

const { paramDrawerVisible, paramScriptAsset, paramForm, saveParams } = useScriptParams();
const { currentProject } = useWorkspace();
</script>
