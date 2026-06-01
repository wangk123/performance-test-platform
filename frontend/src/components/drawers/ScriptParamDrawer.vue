<template>
  <el-drawer v-model="paramDrawerVisible" title="脚本默认执行参数" size="460px">
    <div v-if="paramScriptAsset" class="param-drawer">
      <div class="drawer-title">
        <span class="eyebrow">{{ currentProject?.code }}</span>
        <h2>{{ paramScriptAsset.name }} v{{ paramScriptAsset.latestVersion }}</h2>
      </div>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="线程数">
          <el-input-number v-model="paramForm.threads" :min="0" :step="10" controls-position="right" />
        </el-form-item>
        <el-form-item label="循环次数">
          <el-input-number v-model="paramForm.loops" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="持续时间（秒）">
          <el-input-number v-model="paramForm.duration" :min="0" :step="60" controls-position="right" />
        </el-form-item>
        <el-form-item label="Ramp-Up（秒）">
          <el-input-number v-model="paramForm.rampUp" :min="0" :step="10" controls-position="right" />
        </el-form-item>
        <el-form-item label="目标环境">
          <el-select v-model="paramForm.environment" placeholder="选择环境">
            <el-option label="SIT" value="SIT" />
            <el-option label="UAT" value="UAT" />
            <el-option label="PRE" value="PRE" />
            <el-option label="PROD" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item label="JMeter 属性扩展项">
          <el-input
            v-model="paramForm.extraProperties"
            type="textarea"
            :rows="5"
            placeholder="host=127.0.0.1&#10;protocol=https"
          />
        </el-form-item>
      </el-form>

      <div class="drawer-actions">
        <el-button @click="paramDrawerVisible = false">取消</el-button>
        <el-button type="primary" @click="saveParams">保存参数</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { useScriptParams } from '../../composables/useScriptParams';
import { useWorkspace } from '../../composables/useWorkspace';

const { paramDrawerVisible, paramScriptAsset, paramForm, saveParams } = useScriptParams();
const { currentProject } = useWorkspace();
</script>
