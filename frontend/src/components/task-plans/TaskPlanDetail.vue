<template>
  <section class="task-detail">
    <div class="page-head">
      <div>
        <h1>{{ plan.name }}</h1>
        <p>{{ plan.scenarioCount }} 个场景 · {{ plan.remark || '无备注' }}</p>
      </div>
      <div class="script-assets-actions">
        <a-button @click="planDialogVisible = true">编辑计划默认配置</a-button>
        <a-button type="primary" @click="openAddScenario">添加场景</a-button>
      </div>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <h2>测试场景</h2>
          <p>展开查看线程组配置与最近一次执行聚合结果。</p>
        </div>
      </div>

      <div v-if="scenarios.length === 0" class="scenario-list-empty">暂无场景，请添加。</div>
      <div v-else class="scenario-list">
        <div v-for="scenario in scenarios" :key="scenario.id" class="scenario-list-item">
          <button
            type="button"
            class="task-table-row scenario-list-toggle"
            :class="{ active: expandedIds.has(scenario.id) }"
            @click="toggleExpanded(scenario.id)"
          >
            <span class="scenario-expand-icon">{{ expandedIds.has(scenario.id) ? '▾' : '▸' }}</span>
            <span>
              <strong>{{ scenario.name }}</strong>
              <small>{{ scriptById(scenario.scriptVersionId)?.name ?? '未知脚本' }}</small>
            </span>
            <span>
              <span v-if="scenario.latestExecutionStatus" class="status" :class="statusClass(scenario.latestExecutionStatus)">
                {{ executionStatusText(toUiStatus(scenario.latestExecutionStatus)) }}
              </span>
              <span v-else class="status-na">未执行</span>
            </span>
            <span class="task-row-actions" @click.stop>
              <a-tooltip title="执行">
                <a-button type="primary" shape="circle" size="small" @click="openExecuteConfirm(scenario)">
                  <template #icon><CaretRightOutlined /></template>
                </a-button>
              </a-tooltip>
              <a-button type="link" @click="openEditScenario(scenario)">编辑</a-button>
              <a-button type="link" danger @click="removeScenario(scenario)">删除</a-button>
              <a-button
                type="link"
                :disabled="!scenario.latestExecutionStatus"
                :loading="loadingExecutionId === scenario.id"
                @click="openLatestExecution(scenario)"
              >执行记录</a-button>
            </span>
          </button>
          <ScenarioRowPanel v-if="expandedIds.has(scenario.id)" :configs="scenario.threadGroupConfigs ?? []" />
        </div>
      </div>
    </div>

    <TaskPlanDialog v-model="planDialogVisible" :editing-plan="plan" />
    <ScenarioDialog v-model="scenarioDialogVisible" :plan="plan" :editing-scenario="editingScenario" />
    <ExecuteConfirmDialog v-model="executeDialogVisible" :scenario="pendingScenario" @confirm="handleExecuteConfirm" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { CaretRightOutlined } from '@ant-design/icons-vue';
import type { TaskPlan, TaskScenario } from '../../types';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { listExecutionsApi } from '../../api/task-plans';
import TaskPlanDialog from './TaskPlanDialog.vue';
import ScenarioDialog from './ScenarioDialog.vue';
import ExecuteConfirmDialog from './ExecuteConfirmDialog.vue';
import ScenarioRowPanel from './ScenarioRowPanel.vue';

const props = defineProps<{ plan: TaskPlan; scenarios: TaskScenario[] }>();
defineEmits<{ (e: 'back'): void }>();

const planDialogVisible = ref(false);
const scenarioDialogVisible = ref(false);
const editingScenario = ref<TaskScenario | null>(null);
const loadingExecutionId = ref<number | null>(null);
const executeDialogVisible = ref(false);
const pendingScenario = ref<TaskScenario | null>(null);
const expandedIds = ref<Set<number>>(new Set());

const { scriptById, runScenario, removeScenario, executionStatusText, toUiStatus, openExecution } = useTaskPlans();

function toggleExpanded(scenarioId: number) {
  const next = new Set(expandedIds.value);
  if (next.has(scenarioId)) {
    next.delete(scenarioId);
  } else {
    next.add(scenarioId);
  }
  expandedIds.value = next;
}

function openExecuteConfirm(scenario: TaskScenario) {
  pendingScenario.value = scenario;
  executeDialogVisible.value = true;
}

function handleExecuteConfirm(payload: {
  executionName?: string;
  threadGroupConfigId?: number | null;
  threadGroupPresetSortOrder?: number | null;
}) {
  if (pendingScenario.value) {
    void runScenario(pendingScenario.value, payload);
  }
}

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

<style scoped>
.scenario-list {
  border-top: 1px solid var(--border);
}

.scenario-list-item + .scenario-list-item {
  border-top: 1px solid var(--border);
}

.scenario-list-toggle {
  width: 100%;
  grid-template-columns: 28px minmax(180px, 1.2fr) 100px 250px;
}

.scenario-expand-icon {
  color: var(--muted);
  font-size: 12px;
}

.scenario-list-empty {
  padding: 24px;
  text-align: center;
  color: var(--muted);
}
</style>
