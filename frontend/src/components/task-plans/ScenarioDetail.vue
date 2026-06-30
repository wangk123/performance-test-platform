<template>
  <section class="task-detail">
    <div class="panel task-detail-hero">
      <div>
        <h2>{{ scenario.name }}</h2>
        <p>{{ script?.name }}</p>
      </div>
      <a-button type="primary" @click="runScenario(scenario)">执行场景</a-button>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Executions</span>
          <h2>执行记录</h2>
          <p>保留全部历史记录，可查看任意一次执行详情或删除无效记录。</p>
        </div>
      </div>
      <a-table
        class="workspace-table"
        :columns="columns"
        :data-source="executions"
        :pagination="false"
        :row-key="(record: ScenarioExecution) => record.id"
        :locale="{ emptyText: '暂无执行记录。' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <span class="status" :class="statusClass(record.status)">{{ executionStatusText(toUiStatus(record.status)) }}</span>
          </template>
          <template v-else-if="column.key === 'startedAt'">{{ record.startedAt ? formatDate(record.startedAt) : formatDate(record.createdAt) }}</template>
          <template v-else-if="column.key === 'duration'">{{ record.durationMs ? `${Math.round(record.durationMs / 1000)}s` : '-' }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-button type="link" @click="openExecution(record)">详情</a-button>
            <a-button
              v-if="['RUNNING', 'QUEUED', 'STOPPING'].includes(record.status)"
              type="link"
              @click="stopExecution(record)"
            >停止</a-button>
            <a-button
              v-else
              type="link"
              danger
              @click="removeExecution(record)"
            >删除</a-button>
          </template>
        </template>
      </a-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import type { ScenarioExecution, TaskScenario } from '../../types';
import { formatDate } from '../../utils/format';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { stopExecutionApi } from '../../api/task-plans';
import { message } from 'ant-design-vue';

const props = defineProps<{ scenario: TaskScenario; executions: ScenarioExecution[] }>();
defineEmits<{ (e: 'back'): void }>();

const { scriptById, openExecution, removeExecution, executionStatusText, toUiStatus, runScenario, loadExecutions } = useTaskPlans();
const script = computed(() => scriptById(props.scenario.scriptVersionId));

const columns: TableColumnsType<ScenarioExecution> = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '状态', key: 'status', width: 90 },
  { title: '开始时间', key: 'startedAt', width: 160 },
  { title: '耗时', key: 'duration', width: 80 },
  { title: '操作', key: 'actions', width: 180 },
];

function statusClass(status: ScenarioExecution['status']) {
  const ui = toUiStatus(status);
  return { pending: ui === 'PENDING', running: ui === 'RUNNING' || ui === 'STOPPING', success: ui === 'SUCCESS', error: ui === 'FAILED' || ui === 'INTERRUPTED' };
}

async function stopExecution(execution: ScenarioExecution) {
  try {
    await stopExecutionApi(execution.id);
    await loadExecutions(props.scenario.id);
    message.success('已请求停止');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '停止失败');
  }
}
</script>
