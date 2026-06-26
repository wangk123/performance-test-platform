<template>
  <section v-if="instances.length" class="target-metric-section">
    <div class="target-metric-toolbar">
      <span class="target-metric-toolbar-label">JVM 应用</span>
      <a-select
        v-model:value="selectedInstanceKey"
        class="target-metric-select"
        :options="instanceOptions"
        placeholder="选择应用"
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
        :item-id="activeItemId"
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
import type { JvmInstanceSelectable, MetricKind } from '../../types';

const props = defineProps<{
  executionId: number;
  instances: JvmInstanceSelectable[];
  polling: boolean;
  refreshIntervalMs: number;
}>();

const selectedInstanceKey = ref<string | null>(null);

const instanceOptions = computed(() => props.instances.map((instance) => ({
  label: `${instance.serviceName} · ${instance.host}`,
  value: `${instance.targetId}:${instance.itemId}`,
})));

watch(
  () => props.instances,
  (instances) => {
    const first = instances[0];
    selectedInstanceKey.value = first ? `${first.targetId}:${first.itemId}` : null;
  },
  { immediate: true },
);

const activeSelection = computed(() => {
  if (!selectedInstanceKey.value) {
    return null;
  }
  const [targetId, itemId] = selectedInstanceKey.value.split(':');
  return { targetId: Number(targetId), itemId };
});

const activeTargetIds = computed(() => (activeSelection.value ? [activeSelection.value.targetId] : []));
const activeItemId = computed(() => activeSelection.value?.itemId ?? null);

const panels: Array<{ kind: MetricKind; title: string; description: string; dualAxis?: boolean }> = [
  { kind: 'JVM_HEAP_PCT', title: 'Heap 使用率', description: '堆内存使用占最大堆比例。' },
  { kind: 'JVM_MEMORY_BYTES', title: 'Heap & Non-Heap', description: '堆与非堆内存字节用量。' },
  { kind: 'JVM_GC', title: 'GC 频次与耗时', description: '左轴 GC 次数/s，右轴 GC 耗时/s。', dualAxis: true },
  { kind: 'JVM_THREADS', title: '线程数', description: 'Current / Daemon / Peak 三线。' },
  { kind: 'JVM_CPU', title: 'Process CPU', description: 'JVM 进程 CPU 占用。' },
];
</script>
