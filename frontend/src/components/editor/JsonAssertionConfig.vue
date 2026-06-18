<template>
  <div class="json-assertion-config">
    <a-form-item label="JSONPath">
      <a-input
        :value="config.jsonPath"
        placeholder="$.data.code"
        @update:value="updateField('jsonPath', $event)"
      />
    </a-form-item>
    <a-form-item label="校验期望值">
      <a-switch :checked="config.validateValue" @update:checked="updateField('validateValue', $event)" />
    </a-form-item>
    <template v-if="config.validateValue">
      <a-form-item label="期望值">
        <a-textarea
          :value="config.expectedValue"
          :rows="3"
          placeholder='0 或 "success" 或 true'
          @update:value="updateField('expectedValue', $event)"
        />
      </a-form-item>
      <a-form-item label="正则匹配">
        <a-switch :checked="config.useRegex" @update:checked="updateField('useRegex', $event)" />
      </a-form-item>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { JsonAssertionConfig, ScriptStep } from '../../types';

const props = defineProps<{ step: ScriptStep }>();

const config = computed<JsonAssertionConfig>(() => ({
  jsonPath: String(props.step.config.jsonPath ?? ''),
  validateValue: props.step.config.validateValue === true || props.step.config.validateValue === 'true',
  expectedValue: String(props.step.config.expectedValue ?? ''),
  useRegex: props.step.config.useRegex === true || props.step.config.useRegex === 'true',
}));

function updateField(key: keyof JsonAssertionConfig, value: string | boolean) {
  props.step.config = { ...props.step.config, [key]: value };
}
</script>
