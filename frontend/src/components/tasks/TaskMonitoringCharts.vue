<template>
  <section class="task-chart-grid">
    <div class="panel task-chart-card">
      <h2>TPS 视图</h2>
      <p>按接口分别展示每秒采样吞吐，单位 req/s。</p>
      <v-chart class="echarts-panel" :option="tpsOption" autoresize />
    </div>

    <div class="panel task-chart-card">
      <h2>响应时间视图</h2>
      <p>按接口展示平均响应时间，单位 ms。</p>
      <v-chart class="echarts-panel" :option="responseOption" autoresize />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { buildTrendOption } from './chart-options';
import type { TaskMetricSeries } from '../../types';

const props = defineProps<{
  monitoring: TaskMetricSeries;
}>();

const tpsOption = computed(() => buildTrendOption(props.monitoring, 'throughput', 'TPS', 'req/s'));
const responseOption = computed(() => buildTrendOption(props.monitoring, 'avgRtMs', '平均响应时间', 'ms'));
</script>
