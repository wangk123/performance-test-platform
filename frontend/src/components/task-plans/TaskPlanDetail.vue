<template>
  <section class="task-detail">
    <div class="panel task-detail-hero">
      <div>
        <h2>{{ plan.name }}</h2>
        <p>{{ plan.scenarioCount }} 个场景 · {{ plan.remark || '无备注' }}</p>
      </div>
      <a-button type="primary" @click="openAddScenario">添加场景</a-button>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Scenarios</span>
          <h2>测试场景</h2>
          <p>每个场景绑定一份脚本，可单独执行并查看多次执行记录。</p>
        </div>
        <a-button @click="planDialogVisible = true">编辑计划默认配置</a-button>
      </div>
      <a-table
        class="workspace-table"
        :columns="columns"
        :data-source="scenarios"
        :pagination="false"
        :row-key="(record: TaskScenario) => record.id"
        :locale="{ emptyText: '暂无场景，请添加。' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'script'">
            <strong>{{ scriptById(record.scriptVersionId)?.name ?? '未知脚本' }}</strong>
          </template>
          <template v-else-if="column.key === 'status'">
            <span v-if="record.latestExecutionStatus" class="status" :class="statusClass(record.latestExecutionStatus)">
              {{ executionStatusText(toUiStatus(record.latestExecutionStatus)) }}
            </span>
            <span v-else class="status-na">未执行</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <div class="scenario-actions">
              <a-tooltip title="执行">
                <a-button
                  type="primary"
                  shape="circle"
                  size="small"
                  @click="openExecuteConfirm(record)"
                >
                  <template #icon><CaretRightOutlined /></template>
                </a-button>
              </a-tooltip>
              <a-button type="link" @click="openEditScenario(record)">编辑</a-button>
              <a-button type="link" danger @click="removeScenario(record)">删除</a-button>
              <a-button
                type="link"
                :disabled="!record.latestExecutionStatus"
                :loading="loadingExecutionId === record.id"
                @click="openLatestExecution(record)"
              >执行记录</a-button>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <TaskPlanDialog v-model="planDialogVisible" :editing-plan="plan" />
    <ScenarioDialog v-model="scenarioDialogVisible" :plan="plan" :editing-scenario="editingScenario" />
    <ExecuteConfirmDialog v-model="executeDialogVisible" :scenario="pendingScenario" @confirm="handleExecuteConfirm" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import { message } from 'ant-design-vue';
import { CaretRightOutlined } from '@ant-design/icons-vue';
import type { TaskPlan, TaskScenario } from '../../types';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { listExecutionsApi } from '../../api/task-plans';
import TaskPlanDialog from './TaskPlanDialog.vue';
import ScenarioDialog from './ScenarioDialog.vue';
import ExecuteConfirmDialog from './ExecuteConfirmDialog.vue';

defineProps<{ plan: TaskPlan; scenarios: TaskScenario[] }>();
defineEmits<{ (e: 'back'): void }>();

const planDialogVisible = ref(false);
const scenarioDialogVisible = ref(false);
const editingScenario = ref<TaskScenario | null>(null);
const loadingExecutionId = ref<number | null>(null);
const executeDialogVisible = ref(false);
const pendingScenario = ref<TaskScenario | null>(null);

const { scriptById, runScenario, removeScenario, executionStatusText, toUiStatus, openExecution } = useTaskPlans();

function openExecuteConfirm(scenario: TaskScenario) {
  pendingScenario.value = scenario;
  executeDialogVisible.value = true;
}

function handleExecuteConfirm(executionName: string) {
  if (pendingScenario.value) {
    void runScenario(pendingScenario.value, executionName || undefined);
  }
}

const columns: TableColumnsType<TaskScenario> = [
  { title: '场景名称', dataIndex: 'name', key: 'name' },
  { title: '脚本', key: 'script' },
  { title: '最近执行', key: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 250 },
];

function openAddScenario() {
  editingScenario.value = null;
  scenarioDialogVisible.value = true;
}

function openEditScenario(scenario: TaskScenario) {
  editingScenario.value = scenario;
  scenarioDialogVisible.value = true;
}

async function openLatestExecution(scenario: TaskScenario) {
  loadingExecutionId.value = scenario.id;
  try {
    const executions = await listExecutionsApi(scenario.id);
    if (executions.length > 0) {
      openExecution(executions[0]);
    } else {
      message.info('暂无执行记录');
    }
  } catch {
    message.info('暂无执行记录');
  } finally {
    loadingExecutionId.value = null;
  }
}

function statusClass(status: TaskScenario['latestExecutionStatus']) {
  const ui = status ? toUiStatus(status) : 'PENDING';
  return { pending: ui === 'PENDING', running: ui === 'RUNNING' || ui === 'STOPPING', success: ui === 'SUCCESS', error: ui === 'FAILED' || ui === 'INTERRUPTED' };
}
</script>
