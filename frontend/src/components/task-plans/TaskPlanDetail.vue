<template>
  <section class="task-detail">
    <div class="panel task-detail-hero">
      <div>
        <div class="task-detail-nav">
          <a-button class="task-back-button" @click="$emit('back')">返回计划列表</a-button>
          <span class="eyebrow">Task Plan</span>
        </div>
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
            <span v-else>未执行</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-button type="link" @click="openScenario(record)">历史</a-button>
            <a-button type="link" @click="openEditScenario(record)">编辑</a-button>
            <a-button type="primary" size="small" @click="runScenario(record)">执行</a-button>
            <a-button type="link" danger @click="removeScenario(record)">删除</a-button>
          </template>
        </template>
      </a-table>
    </div>

    <TaskPlanDialog v-model="planDialogVisible" :editing-plan="plan" />
    <ScenarioDialog v-model="scenarioDialogVisible" :plan="plan" :editing-scenario="editingScenario" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import type { TaskPlan, TaskScenario } from '../../types';
import { useTaskPlans } from '../../composables/useTaskPlans';
import TaskPlanDialog from './TaskPlanDialog.vue';
import ScenarioDialog from './ScenarioDialog.vue';

defineProps<{ plan: TaskPlan; scenarios: TaskScenario[] }>();
defineEmits<{ (e: 'back'): void }>();

const planDialogVisible = ref(false);
const scenarioDialogVisible = ref(false);
const editingScenario = ref<TaskScenario | null>(null);

const { scriptById, openScenario, runScenario, removeScenario, executionStatusText, toUiStatus } = useTaskPlans();

const columns: TableColumnsType<TaskScenario> = [
  { title: '场景名称', dataIndex: 'name', key: 'name' },
  { title: '脚本', key: 'script' },
  { title: '最近执行', key: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 280 },
];

function openAddScenario() {
  editingScenario.value = null;
  scenarioDialogVisible.value = true;
}

function openEditScenario(scenario: TaskScenario) {
  editingScenario.value = scenario;
  scenarioDialogVisible.value = true;
}

function statusClass(status: TaskScenario['latestExecutionStatus']) {
  const ui = status ? toUiStatus(status) : 'PENDING';
  return { pending: ui === 'PENDING', running: ui === 'RUNNING' || ui === 'STOPPING', success: ui === 'SUCCESS', error: ui === 'FAILED' || ui === 'INTERRUPTED' };
}
</script>
