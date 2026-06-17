<template>
  <div class="response-assertion-config">
    <div class="step-config-grid">
      <a-form-item label="断言目标">
        <a-select :value="config.target" @update:value="updateField('target', $event)">
          <a-select-option label="响应体" value="body" />
          <a-select-option label="响应码" value="statusCode" />
          <a-select-option label="响应头" value="headers" />
        </a-select>
      </a-form-item>
      <a-form-item label="匹配方式">
        <a-select :value="config.match" @update:value="updateField('match', $event)">
          <a-select-option label="包含" value="contains" />
          <a-select-option label="等于" value="equals" />
          <a-select-option label="正则" value="regex" />
        </a-select>
      </a-form-item>
    </div>
    <a-form-item label="匹配内容">
      <a-textarea
        :value="config.rule"
        :rows="4"
        placeholder="输入响应码、Header 片段、响应体文本或正则"
        @update:value="updateField('rule', $event)"
      />
    </a-form-item>
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
