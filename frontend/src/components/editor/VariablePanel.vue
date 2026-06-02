<template>
  <aside class="variable-panel">
    <section>
      <h3>内置系统变量</h3>
      <button v-for="variable in systemVariables" :key="variable.key" type="button" @click="emit('insert', variable.key)">
        <strong>{{ placeholderOf(variable.key) }}</strong>
        <span>{{ variable.label }}</span>
      </button>
    </section>
    <section>
      <h3>项目全局变量</h3>
      <button v-for="variable in projectVariables" :key="variable.key" type="button" @click="emit('insert', variable.key)">
        <strong>{{ placeholderOf(variable.key) }}</strong>
        <span>{{ variable.value }}</span>
      </button>
    </section>
  </aside>
</template>

<script setup lang="ts">
import type { VariableOption } from '../../utils/http-request-config';
import { placeholderOf, systemVariables } from '../../utils/http-request-config';

defineProps<{ projectVariables: VariableOption[] }>();

const emit = defineEmits<{ insert: [key: string] }>();
</script>
