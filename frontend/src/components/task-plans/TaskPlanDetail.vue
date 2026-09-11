<template>
  <section class="task-detail plan-detail">
    <div class="plan-head-card">
      <div class="page-head">
        <div class="plan-head-info">
          <h1 class="plan-head-title">
            {{ doc.plan.value?.name ?? plan.name }}
            <span class="phase-badge" :class="phaseBadgeClass">
              <span v-if="isRunning" class="dot" />
              {{ phaseText === statusText ? phaseText : `${phaseText} · ${statusText}` }}
            </span>
          </h1>
          <p class="plan-head-meta">
            <span>负责人 <b>{{ plan.createdBy }}</b></span>
            <span class="dot" aria-hidden="true">·</span>
            <span>场景 <b>{{ scenarios.length }}</b><a
              v-if="can('EDIT') && !scenarios.length"
              class="meta-link"
              href="#"
              @click.prevent="openAddScenario"
            >去绑定</a></span>
            <span class="dot" aria-hidden="true">·</span>
            <span>更新于 <b>{{ formatDate(doc.plan.value?.updatedAt ?? plan.updatedAt) }}</b></span>
          </p>
        </div>
        <div class="plan-head-side">
          <div class="script-assets-actions">
            <a-button v-if="can('EDIT')" @click="openPlanConfig">编辑默认配置</a-button>
            <a-button v-if="can('SUBMIT')" type="primary" @click="submitForReview">提交评审</a-button>
            <a-button v-if="can('WITHDRAW')" @click="doc.transition('withdraw', undefined, '已撤回')">撤回</a-button>
            <a-button v-if="can('BACK_TO_DRAFT')" @click="doc.transition('back-to-draft', undefined, '已退回草稿')">退回草稿</a-button>
          </div>
          <PlanPhaseStepper :phase="doc.plan.value?.phase ?? 'DRAFT'" />
        </div>
      </div>
    </div>

    <a-tabs v-model:active-key="activeTab">
      <template #rightExtra>
        <div v-if="activeTab === 'document'" class="plan-doc-toolbar">
          <button
            type="button"
            class="segmented-item anno-toggle"
            :class="{ active: doc.panelEffective.value }"
            :aria-pressed="doc.panelEffective.value"
            @click="doc.togglePanel"
          >💬 批注 {{ doc.threads.value.length }}</button>
          <div
            class="segmented"
            role="tablist"
            aria-label="文档视图切换"
            @keydown="onDocViewKeydown"
          >
            <button
              v-for="mode in DOC_VIEWS"
              :key="mode"
              type="button"
              role="tab"
              class="segmented-item"
              :class="{ active: docView === mode }"
              :aria-selected="docView === mode"
              :aria-controls="`doc-panel-${mode}`"
              @click="docView = mode"
            >{{ mode }}</button>
          </div>
          <div class="doc-toolbar-right">
            <a-button size="small" type="primary" ghost @click="publishOpen = true">发布版本</a-button>
          </div>
        </div>
      </template>
      <a-tab-pane key="document" tab="文档">
        <PlanDetailDocument
          v-model:view-mode="docView"
          :doc="doc"
          :plan="doc.plan.value ?? plan"
          :scenarios="scenarios"
          :locate-comment-id="pendingLocate"
          @located="pendingLocate = null"
          @changed="onDocChanged"
          @request-add="openAddScenario"
          @request-edit="openEditScenario"
        />
      </a-tab-pane>
      <a-tab-pane key="review" tab="评审">
        <div class="plan-tab-scroll"><PlanDetailReview :doc="doc" @locate="locateComment" /></div>
      </a-tab-pane>
      <a-tab-pane key="report" tab="报告">
        <div class="plan-tab-scroll"><PlanDetailReport :doc="doc" :scenarios="scenarios" /></div>
      </a-tab-pane>
      <a-tab-pane key="versions" tab="版本">
        <div class="plan-tab-scroll"><PlanDetailVersions :doc="doc" :refresh-tick="versionRefreshTick" @request-publish="publishOpen = true" /></div>
      </a-tab-pane>
    </a-tabs>

    <TaskPlanDialog v-model="planDialogVisible" :editing-plan="plan" />
    <ScenarioDialog v-model="scenarioDialogVisible" :plan="doc.plan.value ?? plan" :editing-scenario="editingScenario" />
    <PublishVersionModal
      v-model:open="publishOpen"
      :plan-id="(doc.plan.value ?? plan).id"
      @published="onVersionPublished"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import type { TaskPlan, TaskScenario } from '../../types';
import { usePlanDoc, statusLabel } from '../../composables/usePlanDoc';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { formatDate } from '../../utils/format';
import PlanPhaseStepper from './PlanPhaseStepper.vue';
import PlanDetailDocument from './PlanDetailDocument.vue';
import PlanDetailReview from './PlanDetailReview.vue';
import PlanDetailReport from './PlanDetailReport.vue';
import TaskPlanDialog from './TaskPlanDialog.vue';
import ScenarioDialog from './ScenarioDialog.vue';
import PlanDetailVersions from './PlanDetailVersions.vue';
import PublishVersionModal from './PublishVersionModal.vue';

const props = defineProps<{ plan: TaskPlan; scenarios: TaskScenario[] }>();
defineEmits<{ (e: 'back'): void }>();

const doc = usePlanDoc();
const route = useRoute();
const router = useRouter();
const activeTab = ref('document');
const planDialogVisible = ref(false);
const scenarioDialogVisible = ref(false);
const editingScenario = ref<TaskScenario | null>(null);

const publishOpen = ref(false);
const versionRefreshTick = ref(0);

function onVersionPublished() {
  versionRefreshTick.value += 1; // 发布后刷新版本 Tab（未发布变更提示/列表）
}

/** 文档视图状态上提至 Tabs 行工具条（rightExtra），经 v-model 下发组件内部使用。 */
const DOC_VIEWS = ['Pretty', 'Markdown'] as const;
const docView = ref<'Pretty' | 'Markdown'>('Pretty');

/** tablist 左右方向键切换视图（按钮本身仍可 Tab 逐一到达）。 */
function onDocViewKeydown(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
  event.preventDefault();
  docView.value = event.key === 'ArrowRight' ? 'Markdown' : 'Pretty';
}

/** 评审工作台「↧ 定位」与 ?comment= 深链共用：待定位批注经 prop 下发，文档组件定位完成后置空。 */
const pendingLocate = ref<number | null>(null);

/** query 同步（Task 11）：?tab= 三键白名单（publish 已并入 versions；versions 不入 URL，刷新回落 document）；?comment= 深链。 */
const TAB_KEYS = ['document', 'review', 'report'] as const;

function tabOfQuery(): string {
  const tab = route.query.tab;
  return typeof tab === 'string' && (TAB_KEYS as readonly string[]).includes(tab) ? tab : 'document';
}

/** locateComment 自写的 query 在途时抑制 watch(activeTab) 的补写：watcher（pre-flush 微任务）
 *  早于 vue-router 提交执行，读到旧 route.query 会误判 tab 不一致并覆盖掉 ?comment=。 */
let syncingTabQuery = false;

function locateComment(commentId: number) {
  pendingLocate.value = commentId;
  activeTab.value = 'document';
  syncingTabQuery = true;
  void router.replace({ query: { ...route.query, tab: 'document', comment: String(commentId) } })
    .finally(() => { syncingTabQuery = false; });
}

const PHASE_TEXT: Record<string, string> = {
  DRAFT: '草稿', REVIEW: '评审', EXECUTION: '执行', REPORT: '报告', PUBLISH: '发布',
};

const phase = computed(() => doc.plan.value?.phase ?? 'DRAFT');
const status = computed(() => doc.plan.value?.status ?? 'DRAFT');
const phaseText = computed(() => PHASE_TEXT[phase.value] ?? phase.value);
const statusText = computed(() => statusLabel(phase.value, status.value));
const isRunning = computed(() => phase.value === 'EXECUTION' && status.value === 'RUNNING');
const phaseBadgeClass = computed(() => `is-${phase.value.toLowerCase()}`);

onMounted(async () => {
  activeTab.value = tabOfQuery();
  const comment = route.query.comment;
  const deepLinkCommentId = typeof comment === 'string' && /^\d+$/.test(comment) ? Number(comment) : null;
  if (deepLinkCommentId != null) activeTab.value = 'document';
  await doc.load(props.plan.id);
  // 深链定位必须等文档+批注数据就绪再下发：子组件 watch 回调里 deriveAnchors 依赖
  // anchoredRoots（来自 comments 接口），若在 load 完成前置位，locate(null) 后即 emit('located')
  // 置空，深链定位会静默失效。子组件 watch 非 immediate 依然能命中——prop 由 null→N 是后续变更。
  if (deepLinkCommentId != null) pendingLocate.value = deepLinkCommentId;
});

// Tab 状态入 URL（replaceState 语义，不产生历史记录）；恢复值与 query 已一致时条件短路，直链不多余 replace。
watch(activeTab, (tab) => {
  if (syncingTabQuery || tab === tabOfQuery()) return;
  void router.replace({ query: { ...route.query, tab } });
});

watch(() => props.plan.id, (id) => void doc.load(id));

/** 文档或场景实体变更后，除刷新文档外还需重载场景列表（绑定徽标/执行状态取自场景实体）。 */
const { loadScenarios } = useTaskPlans();
function onDocChanged() {
  void doc.refresh();
  void loadScenarios(props.plan.id);
  versionRefreshTick.value += 1;
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
