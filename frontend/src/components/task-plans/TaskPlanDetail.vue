<template>
  <section class="task-detail">
    <div class="page-head">
      <div>
        <h1>{{ doc.plan.value?.name ?? plan.name }}</h1>
        <p>
          {{ plan.scenarioCount }} 个场景 · 负责人 {{ plan.createdBy }} ·
          文档 revision {{ doc.plan.value?.revision ?? plan.revision }}
        </p>
      </div>
      <div class="script-assets-actions">
        <a-button v-if="can('EDIT')" @click="openPlanConfig">编辑默认配置</a-button>
        <a-button v-if="can('SUBMIT')" type="primary" @click="submitForReview">提交评审</a-button>
        <a-button v-if="can('WITHDRAW')" @click="doc.transition('withdraw', undefined, '已撤回')">撤回</a-button>
        <a-button v-if="can('BACK_TO_DRAFT')" @click="doc.transition('back-to-draft', undefined, '已退回草稿')">退回草稿</a-button>
      </div>
    </div>

    <PlanPhaseStepper :phase="doc.plan.value?.phase ?? 'DRAFT'" :status="doc.plan.value?.status ?? 'DRAFT'" />

    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="document" tab="文档">
        <PlanDetailDocument
          :doc="doc"
          :plan="doc.plan.value ?? plan"
          :scenarios="scenarios"
          @changed="doc.refresh"
          @request-add="openAddScenario"
          @request-edit="openEditScenario"
        />
      </a-tab-pane>
      <a-tab-pane key="review" tab="评审">
        <PlanDetailReview :doc="doc" />
      </a-tab-pane>
      <a-tab-pane key="report" tab="报告">
        <PlanDetailReport :doc="doc" :scenarios="scenarios" />
      </a-tab-pane>
      <a-tab-pane key="publish" tab="发布">
        <PlanDetailPublish :doc="doc" />
      </a-tab-pane>
    </a-tabs>

    <TaskPlanDialog v-model="planDialogVisible" :editing-plan="plan" />
    <ScenarioDialog v-model="scenarioDialogVisible" :plan="doc.plan.value ?? plan" :editing-scenario="editingScenario" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { TaskPlan, TaskScenario } from '../../types';
import { usePlanDoc } from '../../composables/usePlanDoc';
import PlanPhaseStepper from './PlanPhaseStepper.vue';
import PlanDetailDocument from './PlanDetailDocument.vue';
import PlanDetailReview from './PlanDetailReview.vue';
import PlanDetailReport from './PlanDetailReport.vue';
import PlanDetailPublish from './PlanDetailPublish.vue';
import TaskPlanDialog from './TaskPlanDialog.vue';
import ScenarioDialog from './ScenarioDialog.vue';

const props = defineProps<{ plan: TaskPlan; scenarios: TaskScenario[] }>();
defineEmits<{ (e: 'back'): void }>();

const doc = usePlanDoc();
const activeTab = ref('document');
const planDialogVisible = ref(false);
const scenarioDialogVisible = ref(false);
const editingScenario = ref<TaskScenario | null>(null);

onMounted(() => void doc.load(props.plan.id));
watch(() => props.plan.id, (id) => void doc.load(id));

function can(action: string) {
  return Boolean(doc.permissions.value[action]);
}

function openPlanConfig() {
  planDialogVisible.value = true;
}

function openAddScenario() {
  editingScenario.value = null;
  scenarioDialogVisible.value = true;
}

function openEditScenario(scenario: TaskScenario) {
  editingScenario.value = scenario;
  scenarioDialogVisible.value = true;
}

async function submitForReview() {
  await doc.transition('submit', undefined, '已提交评审');
}
</script>
