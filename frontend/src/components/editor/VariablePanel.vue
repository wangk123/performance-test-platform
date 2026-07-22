<template>
  <aside class="variable-panel">
    <div class="variable-panel-scroll">
      <section>
        <h3>内置系统变量</h3>
        <button
          v-for="variable in systemVariables"
          :key="variable.key"
          type="button"
          @click="emit('insert', variable.key)"
        >
          <strong>{{ placeholderOf(variable.key) }}</strong>
          <span>{{ variable.label }}</span>
        </button>
      </section>

      <section>
        <h3>项目全局变量</h3>
        <p v-if="!projectVariables.length" class="variable-panel-hint">暂无项目变量</p>
        <button
          v-for="variable in projectVariables"
          :key="variable.key"
          type="button"
          @click="emit('insert', variable.key)"
        >
          <strong>{{ placeholderOf(variable.key) }}</strong>
          <span>{{ variable.value }}</span>
        </button>
      </section>

      <section>
        <h3>平台函数</h3>
        <p v-if="platformFunctionsLoading" class="variable-panel-hint">加载中...</p>
        <p v-else-if="!platformFunctions.length" class="variable-panel-hint">暂无函数</p>
        <button
          v-for="item in platformFunctions"
          :key="item.key"
          type="button"
          @click="emit('insert', item.example)"
        >
          <strong>{{ item.example }}</strong>
          <span>{{ item.displayName }}</span>
        </button>
      </section>

      <section>
        <h3>JMeter 内置函数</h3>
        <button
          v-for="item in jmeterBuiltinFunctions"
          :key="item.key"
          type="button"
          @click="emit('insert', item.example)"
        >
          <strong>{{ item.example }}</strong>
          <span>{{ item.displayName }}</span>
        </button>
      </section>

      <p class="variable-panel-hint">HTTP 调试与报文预览不执行函数，请以分布式压测为准。</p>
    </div>
  </aside>
</template>

<script setup lang="ts">
import type { JmeterFunctionDefinition } from '../../api/jmeter-functions';
import type { VariableOption } from '../../utils/http-request-config';
import { placeholderOf, systemVariables } from '../../utils/http-request-config';
import { jmeterBuiltinFunctions } from '../../utils/jmeter-builtin-functions';

withDefaults(
  defineProps<{
    projectVariables: VariableOption[];
    platformFunctions?: JmeterFunctionDefinition[];
    platformFunctionsLoading?: boolean;
  }>(),
  {
    platformFunctions: () => [],
    platformFunctionsLoading: false,
  },
);

const emit = defineEmits<{ insert: [key: string] }>();
</script>
