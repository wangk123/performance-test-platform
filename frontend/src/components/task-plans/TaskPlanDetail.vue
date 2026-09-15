<template>
  <section class="task-detail plan-detail">
    <div class="plan-head-card">
      <div class="page-head">
        <div class="plan-head-info">
          <h1 class="plan-head-title">
            {{ doc.plan.value?.name ?? plan.name }}
            <span class="phase-badge" :class="statusBadgeClass">
              <span v-if="isExecuting" class="dot" />
              {{ statusText }}
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
            <a-button
              v-for="action in flowActions"
              :key="action"
              type="primary"
              @click="onFlowAction(action)"
            >{{ ACTION_TEXT[action] ?? action }}</a-button>
          </div>
          <PlanPhaseStepper :status="status" />
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
      <a-tab-pane key="envcheck" tab="环境检查">
        <EnvCheckTab :plan="doc.plan.value ?? plan" :doc-plan="doc" />
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
    <a-modal
      v-model:open="publishTransitionOpen"
      title="发布"
      :width="520"
      ok-text="确认发布"
      cancel-text="取消"
      :confirm-loading="publishing"
      @ok="confirmPublish"
    >
      <a-form layout="vertical" class="publish-transition-form">
        <a-form-item label="版本号" required>
          <a-input v-model:value="publishVersionNo" placeholder="如：V1.0" :maxlength="32" aria-label="版本号" />
        </a-form-item>
        <a-form-item label="总体结论" required>
          <a-textarea
            v-model:value="publishConclusion"
            :rows="4"
            :maxlength="1000"
            show-count
            placeholder="发布人确认的总体结论（已预填自动判定文本，可修改）"
            aria-label="总体结论"
          />
        </a-form-item>
      </a-form>
      <p class="publish-transition-hint">
        发布将按版本号登记「发布」版本并固化当时正文快照；文档不冻结，仍可编辑与新增版本。
      </p>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Modal, message } from 'ant-design-vue';
import type { TaskPlan, TaskScenario } from '../../types';
import type { TransitionAction } from '../../api/plan-doc';
import { getPlanVerdictApi } from '../../api/plan-doc';
import { usePlanDoc } from '../../composables/usePlanDoc';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { activeWarning, STATUS_LABEL, visibleActions } from '../../utils/plan-status';
import { formatDate } from '../../utils/format';
import PlanPhaseStepper from './PlanPhaseStepper.vue';
import PlanDetailDocument from './PlanDetailDocument.vue';
import PlanDetailReview from './PlanDetailReview.vue';
import TaskPlanDialog from './TaskPlanDialog.vue';
import ScenarioDialog from './ScenarioDialog.vue';
import PlanDetailVersions from './PlanDetailVersions.vue';
import PublishVersionModal from './PublishVersionModal.vue';
import EnvCheckTab from '../env-check/EnvCheckTab.vue';

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

/** query 同步（Task 11）：?tab= 二键白名单（report 已删除、publish 已并入 versions；versions 不入 URL，刷新回落 document）；?comment= 深链。 */
const TAB_KEYS = ['document', 'review'] as const;

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

/** 单一状态 → 徽标文案与执行中脉冲点（spec 2026-09-11 §3.1）。 */
const status = computed(() => doc.plan.value?.status ?? 'PLANNING');
const statusText = computed(() => STATUS_LABEL[status.value] ?? status.value);
const statusBadgeClass = computed(() => `is-${status.value.toLowerCase()}`);
const isExecuting = computed(() => status.value === 'EXECUTING');

/** 流转按钮区（单行道）：由状态推导，每状态至多一个；「新增版本」入口仅保留版本 Tab 一处。 */
const ACTION_TEXT: Record<string, string> = {
  submit: '提交评审',
  approve: '评审通过',
  'finish-execution': '执行完成',
  publish: '发布',
};
const flowActions = computed(() => visibleActions(status.value) as TransitionAction[]);

/** 执行完成是单行道不可逆流转：总是二次确认；发布保持原软门禁（有活跃执行先告警）。 */
function onFlowAction(action: TransitionAction) {
  const warn = (action === 'finish-execution' || action === 'publish')
    ? activeWarning(doc.activeExecutions.value) : null;
  if (action === 'finish-execution' || warn) {
    Modal.confirm({
      title: action === 'finish-execution' ? '执行完成确认' : '未完成执行告警',
      content: warn ?? '确认执行完成？将进入报告编辑阶段。',
      okText: '继续',
      cancelText: '取消',
      onOk: () => runFlowAction(action),
    });
    return;
  }
  void runFlowAction(action);
}

async function runFlowAction(action: TransitionAction) {
  if (action === 'publish') {
    await openPublishTransition();
    return;
  }
  const successText = { submit: '已提交评审', approve: '评审已通过', 'finish-execution': '已进入报告编辑' }[action];
  await doc.transition(action, undefined, successText ?? '操作成功');
}

// ---- 发布弹窗（总体结论 + 版本号，均必填；版本号后端 publishForWorkflow 仍校验） ----
const publishTransitionOpen = ref(false);
const publishVersionNo = ref('');
const publishConclusion = ref('');
const publishing = ref(false);

async function openPublishTransition() {
  publishVersionNo.value = '';
  if (!publishConclusion.value.trim()) {
    const verdict = await getPlanVerdictApi(props.plan.id).catch(() => null);
    if (verdict?.prefillConclusion) publishConclusion.value = verdict.prefillConclusion; // 预填自动判定文本，可修改
  }
  publishTransitionOpen.value = true;
}

async function confirmPublish() {
  const versionNo = publishVersionNo.value.trim();
  const conclusion = publishConclusion.value.trim();
  if (!versionNo) {
    message.warning('请填写版本号');
    return;
  }
  if (!conclusion) {
    message.warning('请填写总体结论');
    return;
  }
  publishing.value = true;
  try {
    const ok = await doc.transition('publish', { conclusion, versionNo }, '已发布');
    if (ok) {
      publishTransitionOpen.value = false;
      publishConclusion.value = '';
      versionRefreshTick.value += 1; // 发布登记了「报告发布」版本，刷新版本 Tab
    }
  } finally {
    publishing.value = false;
  }
}

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
</script>
