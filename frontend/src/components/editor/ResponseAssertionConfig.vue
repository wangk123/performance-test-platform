<template>
  <div class="response-assertion-config">
    <div class="step-config-grid">
      <el-form-item label="断言目标">
        <el-select :model-value="config.target" @update:model-value="updateField('target', $event)">
          <el-option label="响应体" value="body" />
          <el-option label="响应码" value="statusCode" />
          <el-option label="响应头" value="headers" />
        </el-select>
      </el-form-item>
      <el-form-item label="匹配方式">
        <el-select :model-value="config.match" @update:model-value="updateField('match', $event)">
          <el-option label="包含" value="contains" />
          <el-option label="等于" value="equals" />
          <el-option label="正则" value="regex" />
        </el-select>
      </el-form-item>
    </div>
    <el-form-item label="匹配内容">
      <el-input
        :model-value="config.rule"
        type="textarea"
        :rows="4"
        placeholder="输入响应码、Header 片段、响应体文本或正则"
        @update:model-value="updateField('rule', $event)"
      />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { AssertionMatch, AssertionTarget, ResponseAssertionConfig, ScriptStep } from '../../types';

const props = defineProps<{ step: ScriptStep }>();

const config = computed<ResponseAssertionConfig>(() => ({
  target: normalizeTarget(props.step.config.target),
  match: normalizeMatch(props.step.config.match),
  rule: String(props.step.config.rule ?? ''),
}));

function updateField(key: keyof ResponseAssertionConfig, value: string | number | boolean) {
  props.step.config = { ...props.step.config, [key]: String(value) };
}

function normalizeTarget(value: unknown): AssertionTarget {
  return value === 'statusCode' || value === 'headers' ? value : 'body';
}

function normalizeMatch(value: unknown): AssertionMatch {
  return value === 'equals' || value === 'regex' ? value : 'contains';
}
</script>
