<template>
  <div class="scenario-row-panel">
    <a-table
      v-if="configs.length > 0"
      class="workspace-table scenario-config-table"
      size="small"
      :columns="columns"
      :data-source="configs"
      :pagination="false"
      :row-key="(record: ScenarioThreadGroupConfig) => record.id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'samples'">
          {{ formatSamples(record.latestSummary?.samples) }}
        </template>
        <template v-else-if="column.key === 'throughput'">
          {{ formatThroughput(record.latestSummary?.throughput) }}
        </template>
        <template v-else-if="column.key === 'avgRt'">
          {{ record.latestSummary ? `${record.latestSummary.avgRt}ms` : '—' }}
        </template>
        <template v-else-if="column.key === 'errorRate'">
          {{ formatErrorRate(record.latestSummary?.errorRate) }}
        </template>
      </template>
    </a-table>
    <p v-else class="scenario-row-empty">暂无线程组配置，执行时将使用脚本默认参数。</p>
  </div>
</template>

<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue';
import type { ScenarioThreadGroupConfig } from '../../types';
import { formatErrorRate, formatSamples, formatThroughput } from '../../utils/scenario-thread-group';

defineProps<{ configs: ScenarioThreadGroupConfig[] }>();

const columns: TableColumnsType<ScenarioThreadGroupConfig> = [
  { title: 'Thread Group', dataIndex: 'stepName', key: 'stepName' },
  { title: '线程数', dataIndex: 'threads', key: 'threads', align: 'right', width: 80 },
  { title: 'Ramp-Up', dataIndex: 'rampUp', key: 'rampUp', align: 'right', width: 90 },
  { title: '执行时间', dataIndex: 'duration', key: 'duration', align: 'right', width: 90 },
  { title: '采样数', key: 'samples', align: 'right', width: 100 },
  { title: 'TPS', key: 'throughput', align: 'right', width: 80 },
  { title: '平均响应时间', key: 'avgRt', align: 'right', width: 110 },
  { title: '错误率', key: 'errorRate', align: 'right', width: 80 },
];
</script>

<style scoped>
.scenario-row-panel {
  padding: 12px 14px 14px 42px;
  border-top: 1px dashed var(--border);
  background: var(--surface-soft);
}

.scenario-row-empty {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.scenario-config-table :deep(.ant-table) {
  background: transparent;
}
</style>
