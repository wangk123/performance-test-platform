<template>
  <section v-if="activeExecutionId && !executionDetail" class="task-schedule">
    <div class="panel task-detail-loading">
      <a-spin tip="加载执行详情..." />
    </div>
  </section>

  <ExecutionDetailView
    v-else-if="activeExecutionId && executionDetail"
    :execution="executionDetail"
    @back="backToPlanDetail(executionDetail.planId)"
  />

  <TaskPlanDetail
    v-else-if="activePlanId && activePlan"
    :plan="activePlan"
    :scenarios="scenarios"
    @back="backToPlanList"
  />

  <section v-else class="task-schedule">
    <div class="metrics-grid compact">
      <div class="metric"><span>计划总数</span><strong>{{ projectPlans.length }}</strong></div>
      <div class="metric"><span>场景总数</span><strong>{{ totalScenarios }}</strong></div>
    </div>
    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Task Plans</span>
          <h2>任务计划</h2>
          <p>每个计划包含多个测试场景，按场景单独执行并保留历史记录。</p>
        </div>
        <a-button type="primary" @click="openCreatePlan">新建计划</a-button>
      </div>
      <div class="task-filters">
        <a-input v-model:value="planKeyword" allow-clear placeholder="搜索计划名称" />
      </div>
      <a-table
        class="workspace-table"
        :columns="columns"
        :data-source="filteredPlans"
        :pagination="false"
        :row-key="(record: TaskPlan) => record.id"
        :custom-row="(record: TaskPlan) => ({ onClick: () => openPlan(record) })"
        :locale="{ emptyText: '暂无任务计划。' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <strong>{{ record.name }}</strong>
          </template>
          <template v-else-if="column.key === 'scenarios'">{{ record.scenarioCount }} 个场景</template>
          <template v-else-if="column.key === 'updatedAt'">{{ formatDate(record.updatedAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-button type="link" @click.stop="openPlan(record)">详情</a-button>
            <a-button type="link" @click.stop="openEditPlan(record)">编辑</a-button>
            <a-button type="link" danger @click.stop="removePlan(record)">删除</a-button>
          </template>
        </template>
      </a-table>
    </div>
    <TaskPlanDialog v-model="planDialogVisible" :editing-plan="editingPlan" />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import type { TaskPlan } from '../../types';
import { formatDate } from '../../utils/format';
import { useTaskPlans } from '../../composables/useTaskPlans';
import TaskPlanDialog from './TaskPlanDialog.vue';
import TaskPlanDetail from './TaskPlanDetail.vue';
import ExecutionDetailView from './ExecutionDetailView.vue';

const planDialogVisible = ref(false);
const editingPlan = ref<TaskPlan | null>(null);

const {
  planKeyword,
  filteredPlans,
  projectPlans,
  scenarios,
  executions,
  executionDetail,
  activePlan,
  activePlanId,
  activeExecutionId,
  openPlan,
  backToPlanList,
  backToPlanDetail,
  removePlan,
} = useTaskPlans();

const totalScenarios = computed(() => projectPlans.value.reduce((sum, plan) => sum + plan.scenarioCount, 0));

const columns: TableColumnsType<TaskPlan> = [
  { title: '计划名称', key: 'name', minWidth: 220 },
  { title: '场景数', key: 'scenarios', width: 100 },
  { title: '更新时间', key: 'updatedAt', width: 160 },
  { title: '操作', key: 'actions', width: 200 },
];

function openCreatePlan() {
  editingPlan.value = null;
  planDialogVisible.value = true;
}

function openEditPlan(plan: TaskPlan) {
  editingPlan.value = plan;
  planDialogVisible.value = true;
}
</script>
