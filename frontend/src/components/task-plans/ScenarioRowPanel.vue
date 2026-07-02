<template>
  <div class="scenario-row-panel">
    <template v-if="presetGroups.length > 0">
      <section
        v-for="(group, groupIndex) in presetGroups"
        :key="group.sortOrder"
        class="scenario-preset-block"
      >
        <header class="scenario-preset-head">
          <span class="scenario-preset-badge">配置 {{ groupIndex + 1 }}</span>
          <span class="scenario-preset-meta">{{ group.rows.length }} 个 Thread Group</span>
        </header>
        <div class="scenario-preset-table-wrap">
          <table class="scenario-preset-table">
            <colgroup>
              <col class="col-name" />
              <col class="col-num" />
              <col class="col-num" />
              <col class="col-num" />
              <col class="col-num-wide" />
              <col class="col-num" />
              <col class="col-num-wide" />
              <col class="col-num" />
            </colgroup>
            <thead>
              <tr>
                <th>Thread Group</th>
                <th class="num">线程数</th>
                <th class="num">Ramp-Up</th>
                <th class="num">执行时间</th>
                <th class="num">采样数</th>
                <th class="num">TPS</th>
                <th class="num">平均响应时间</th>
                <th class="num">错误率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in group.rows" :key="row.id || row.stepId">
                <td class="name">{{ row.stepName }}</td>
                <td class="num">{{ row.threads }}</td>
                <td class="num">{{ row.rampUp }}</td>
                <td class="num">{{ row.duration }}</td>
                <td class="num">{{ formatSamples(row.latestSummary?.samples) }}</td>
                <td class="num">{{ formatThroughput(row.latestSummary?.throughput) }}</td>
                <td class="num">{{ row.latestSummary ? `${row.latestSummary.avgRt}ms` : '—' }}</td>
                <td class="num">{{ formatErrorRate(row.latestSummary?.errorRate) }}</td>
              </tr>
              <tr v-if="showSummaryRow(group.rows)" class="scenario-preset-summary">
                <td class="name">汇总</td>
                <td class="num">{{ sumPresetThreads(group.rows) }}</td>
                <td class="num dash">—</td>
                <td class="num dash">—</td>
                <td class="num">{{ formatSamples(groupSummary(group.rows)?.samples) }}</td>
                <td class="num">{{ formatThroughput(groupSummary(group.rows)?.throughput) }}</td>
                <td class="num">
                  {{ groupSummary(group.rows) ? `${groupSummary(group.rows)!.avgRt}ms` : '—' }}
                </td>
                <td class="num">{{ formatErrorRate(groupSummary(group.rows)?.errorRate) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
    <p v-else class="scenario-row-empty">暂无线程组配置，执行时将使用脚本默认参数。</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ScenarioThreadGroupConfig } from '../../types';
import {
  aggregatePresetSummary,
  formatErrorRate,
  formatSamples,
  formatThroughput,
  groupStoredThreadGroupConfigs,
  sumPresetThreads,
} from '../../utils/scenario-thread-group';

const props = defineProps<{ configs: ScenarioThreadGroupConfig[] }>();

const presetGroups = computed(() => groupStoredThreadGroupConfigs(props.configs));

function showSummaryRow(rows: ScenarioThreadGroupConfig[]) {
  return rows.length > 1;
}

function groupSummary(rows: ScenarioThreadGroupConfig[]) {
  return aggregatePresetSummary(rows);
}
</script>

<style scoped>
.scenario-row-panel {
  display: grid;
  gap: 10px;
  padding: 12px 14px 14px 42px;
  border-top: 1px dashed var(--border);
  background: var(--surface-soft);
}

.scenario-row-empty {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.scenario-preset-block {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  overflow: hidden;
}

.scenario-preset-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  background: var(--active-bg);
}

.scenario-preset-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  color: var(--primary-ink);
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  border-radius: 999px;
  background: var(--primary);
}

.scenario-preset-meta {
  color: var(--text);
  font-size: 13px;
}

.scenario-preset-table-wrap {
  overflow-x: auto;
}

.scenario-preset-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 13px;
  color: var(--text);
}

.scenario-preset-table .col-name {
  width: 28%;
}

.scenario-preset-table .col-num {
  width: 9%;
}

.scenario-preset-table .col-num-wide {
  width: 11%;
}

.scenario-preset-table th,
.scenario-preset-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
}

.scenario-preset-table th {
  color: var(--text);
  font-weight: 600;
  font-size: 12px;
  background: var(--surface-soft);
  white-space: nowrap;
}

.scenario-preset-table th.num,
.scenario-preset-table td.num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.scenario-preset-table tbody tr:last-child td {
  border-bottom: 0;
}

.scenario-preset-table .name {
  font-weight: 500;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scenario-preset-table .dash {
  color: var(--muted);
}

.scenario-preset-summary td {
  background: var(--active-bg);
  font-weight: 600;
  color: var(--text);
}

.scenario-preset-summary .name {
  color: var(--primary-dark);
}
</style>
