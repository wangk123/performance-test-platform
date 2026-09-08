<template>
  <section class="task-detail plan-detail">
    <div class="plan-head-card">
      <div class="page-head">
        <div class="plan-head-info">
          <h1 class="plan-head-title">
            {{ doc.plan.value?.name ?? plan.name }}
            <span class="phase-badge" :class="phaseBadgeClass">
              <span v-if="isRunning" class="dot" />
              {{ phaseText }} · {{ statusText }}
            </span>
          </h1>
          <p class="plan-head-meta">
            <span>负责人 <b>{{ plan.createdBy }}</b></span>
            <span>场景 <b>{{ scenarios.length }}</b></span>
            <span>文档 <b class="mono">revision {{ doc.plan.value?.revision ?? plan.revision }}</b></span>
            <span>更新于 <b>{{ formatDate(doc.plan.value?.updatedAt ?? plan.updatedAt) }}</b></span>
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
    </div>

    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="document" tab="文档">
        <PlanDetailDocument
          :doc="doc"
          :plan="doc.plan.value ?? plan"
          :scenarios="scenarios"
          @changed="onDocChanged"
          @request-add="openAddScenario"
          @request-edit="openEditScenario"
        />
      </a-tab-pane>
      <a-tab-pane key="review" tab="评审">
        <div class="plan-tab-scroll"><PlanDetailReview :doc="doc" /></div>
      </a-tab-pane>
      <a-tab-pane key="report" tab="报告">
        <div class="plan-tab-scroll"><PlanDetailReport :doc="doc" :scenarios="scenarios" /></div>
      </a-tab-pane>
      <a-tab-pane key="publish" tab="发布">
        <div class="plan-tab-scroll"><PlanDetailPublish :doc="doc" /></div>
      </a-tab-pane>
    </a-tabs>

    <TaskPlanDialog v-model="planDialogVisible" :editing-plan="plan" />
    <ScenarioDialog v-model="scenarioDialogVisible" :plan="doc.plan.value ?? plan" :editing-scenario="editingScenario" />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { TaskPlan, TaskScenario } from '../../types';
import { usePlanDoc, statusLabel } from '../../composables/usePlanDoc';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { formatDate } from '../../utils/format';
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

const PHASE_TEXT: Record<string, string> = {
  DRAFT: '草稿', REVIEW: '评审', EXECUTION: '执行', REPORT: '报告', PUBLISH: '发布',
};

const phase = computed(() => doc.plan.value?.phase ?? 'DRAFT');
const status = computed(() => doc.plan.value?.status ?? 'DRAFT');
const phaseText = computed(() => PHASE_TEXT[phase.value] ?? phase.value);
const statusText = computed(() => statusLabel(phase.value, status.value));
const isRunning = computed(() => phase.value === 'EXECUTION' && status.value === 'RUNNING');
const phaseBadgeClass = computed(() => `is-${phase.value.toLowerCase()}`);

onMounted(() => void doc.load(props.plan.id));
watch(() => props.plan.id, (id) => void doc.load(id));

/** 文档或场景实体变更后，除刷新文档外还需重载场景列表（绑定徽标/执行状态取自场景实体）。 */
const { loadScenarios } = useTaskPlans();
function onDocChanged() {
  void doc.refresh();
  void loadScenarios(props.plan.id);
}

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
