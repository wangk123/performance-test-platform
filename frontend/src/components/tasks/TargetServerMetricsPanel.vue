<template>
  <section v-if="targets.length" class="target-metric-section">
    <div class="target-metric-toolbar">
      <span class="target-metric-toolbar-label">服务器资源</span>
      <a-segmented v-model:value="viewMode" :options="viewModeOptions" />
      <a-select
        v-if="viewMode === 'single'"
        v-model:value="selectedTargetId"
        class="target-metric-select"
        :options="targetOptions"
        placeholder="选择服务器"
      />
      <a-select
        v-else
        v-model:value="selectedTargetIds"
        class="target-metric-select"
        mode="multiple"
        :options="targetOptions"
        placeholder="选择服务器"
        :max-tag-count="2"
      />
    </div>
    <div class="target-metric-grid">
      <TargetMetricChartCard
        v-for="panel in panels"
        :key="panel.kind"
        :execution-id="executionId"
        :kind="panel.kind"
        :title="panel.title"
        :description="panel.description"
        :target-ids="activeTargetIds"
        :polling="polling"
        :refresh-interval-ms="refreshIntervalMs"
        :dual-axis="panel.dualAxis"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import TargetMetricChartCard from './TargetMetricChartCard.vue';
import type { MetricKind, ServerSelectable } from '../../types';

const props = defineProps<{
  executionId: number;
  targets: ServerSelectable[];
  polling: boolean;
  refreshIntervalMs: number;
}>();

const viewModeOptions = [
  { label: '同图多线', value: 'multi' },
  { label: '单选', value: 'single' },
];

const viewMode = ref<'multi' | 'single'>('multi');
const selectedTargetIds = ref<number[]>([]);
const selectedTargetId = ref<number | null>(null);

const targetOptions = computed(() => props.targets.map((target) => ({
  label: `${target.name} (${target.host})`,
  value: target.id,
})));

watch(
  () => props.targets,
  (targets) => {
    selectedTargetIds.value = targets.map((target) => target.id);
    selectedTargetId.value = targets[0]?.id ?? null;
  },
  { immediate: true },
);

const activeTargetIds = computed(() => {
  if (viewMode.value === 'single') {
    return selectedTargetId.value ? [selectedTargetId.value] : [];
  }
  return selectedTargetIds.value;
});

const panels: Array<{ kind: MetricKind; title: string; description: string; dualAxis?: boolean }> = [
  { kind: 'SERVER_CPU', title: 'CPU 使用率', description: '按服务器展示 CPU 占用百分比。' },
  { kind: 'SERVER_LOAD', title: 'Load Average', description: '1/5/15 分钟负载均值。' },
  { kind: 'SERVER_MEM', title: '内存使用率', description: '按可用内存反算使用率。' },
  { kind: 'SERVER_DISK_IO', title: '磁盘 IO Util', description: '按设备展示磁盘 IO 利用率。' },
  { kind: 'SERVER_NET', title: '网络吞吐', description: '收发速率，单位 B/s。' },
  { kind: 'SERVER_TCP', title: 'TCP 连接与重传', description: '左轴连接数，右轴重传速率。', dualAxis: true },
];
</script>
