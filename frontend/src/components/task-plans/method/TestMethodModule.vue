<template>
  <div class="scenario-module method-module">
    <div class="scenario-module-head">
      <span class="scenario-module-hint">场景小节由场景实体同步生成 · 增删改名自动跟进；绑定脚本后可在执行记录表发起压测。</span>
      <a-button v-if="canEditScenario" type="primary" size="small" @click="emit('request-add')">+ 新增场景</a-button>
    </div>

    <template v-if="sections.length">
      <article v-for="item in sections" :key="item.key" class="scenario-card" :class="{ plain: !item.data }">
        <header class="sc-head">
          <span class="sc-id">{{ item.scenarioNo }}</span>
          <div class="sc-title">
            <strong class="sc-name">{{ item.name }}</strong>
            <span v-if="item.testType" class="sc-type">{{ item.testType }}</span>
          </div>
          <div v-if="item.data" class="sc-script">
            <span class="sc-script-label">关联脚本</span>
            <a-select
              v-if="canBindScript"
              class="sc-script-select"
              size="small"
              show-search
              option-filter-prop="label"
              placeholder="选择压测脚本…"
              :options="scriptOptions"
              :value="item.data.scriptVersionId ?? undefined"
              @change="(value: unknown) => onScriptChange(item.data!, Number(value))"
            />
            <template v-else>
              <span v-if="item.data.scriptVersionId" class="sc-bind ok" :title="`脚本版本 #${item.data.scriptVersionId}`">
                已关联脚本 <b class="mono">#{{ item.data.scriptVersionId }}</b>
              </span>
              <span v-else class="sc-bind warn">未关联脚本</span>
            </template>
          </div>
        </header>

        <!-- 方法说明本迭代只读展示，「编辑」切 Markdown 视图；行内编辑（PlanSectionInlineEditor 模式）留后续迭代（spec §3 降级裁定）。 -->
        <div v-if="item.methodText" class="sc-method">
          <div class="sc-method-head">
            <span class="sc-label">方法说明</span>
            <a-button
              v-if="item.data && canEdit"
              size="small"
              type="text"
              class="sc-method-edit"
              title="跳转 Markdown 视图编辑"
              @click="emit('request-markdown')"
            >编辑</a-button>
          </div>
          <MdPreview
            class="method-desc plan-md"
            :model-value="item.methodText"
            :theme="mdTheme"
            language="zh-CN"
          />
        </div>

        <MethodExecTable
          v-if="item.data"
          :plan="plan"
          :scenario="item.data"
          :scenario-no="item.scenarioNo"
          :preset="presetOf(item.data.scenarioId)"
          :can-execute="canExecute"
          @refresh="load"
        />
      </article>
    </template>

    <div v-else-if="loaded" class="plan-empty">
      暂无场景。在评审前添加，或在文档中手写「测试方法」小节（与场景实体按名称对齐后可执行）。
      <div v-if="canEditScenario" class="plan-empty-action">
        <a-button type="primary" size="small" @click="emit('request-add')">+ 新增场景</a-button>
      </div>
    </div>

    <!-- 监控证据区（MethodEvidence，按场景归档 TPS/RT/CPU/内存趋势 + 补充截图）为下一任务组件，此处预留挂载点 -->
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { MethodScenarioData, MethodSectionData } from '../../../api/plan-method';
import { getPlanMethodApi } from '../../../api/plan-method';
import type { TaskPlan, TaskScenario } from '../../../types';
import type { usePlanDoc } from '../../../composables/usePlanDoc';
import { useTheme } from '../../../composables/useTheme';
import { useWorkspace } from '../../../composables/useWorkspace';
import { parseMethodSections } from '../../../utils/plan-markdown';
import { bindScenarioScriptApi } from '../../../api/plan-doc';
import MethodExecTable from './MethodExecTable.vue';

const props = defineProps<{ docPlan: ReturnType<typeof usePlanDoc>; plan: TaskPlan; scenarios: TaskScenario[] }>();
const emit = defineEmits<{
  (e: 'changed'): void;
  (e: 'request-add'): void;
  (e: 'request-edit', scenario: TaskScenario): void;
  (e: 'request-markdown'): void;
}>();

const { themeMode } = useTheme();
const mdTheme = computed(() => (themeMode.value === 'dark' ? 'dark' : 'light'));
const { currentProjectScripts } = useWorkspace();

/* ---------- 聚合数据 + 活跃执行轮询 ---------- */

const data = ref<MethodSectionData | null>(null);
const loaded = ref(false);
let pollTimer: number | null = null;
const POLL_MS = 5000;
const NON_TERMINAL = ['QUEUED', 'PENDING', 'RUNNING', 'STOPPING'];

async function load() {
  try {
    data.value = await getPlanMethodApi(props.plan.id);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '测试方法数据加载失败');
  } finally {
    loaded.value = true;
    syncPolling();
  }
}

/** 存在非终态执行时 5s 轮询聚合，全部终态后停止（组件卸载兜底清理）。 */
function syncPolling() {
  const active = (data.value?.scenarios ?? []).some((s) => s.executions.some((e) => NON_TERMINAL.includes(e.status)));
  if (active && pollTimer === null) pollTimer = window.setInterval(() => void load(), POLL_MS);
  if (!active && pollTimer !== null) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
}

onMounted(() => void load());
watch(() => props.plan.id, () => void load());
onUnmounted(() => {
  if (pollTimer !== null) window.clearInterval(pollTimer);
  pollTimer = null;
});

/* ---------- 门禁（口径对齐 ScenarioDesignModule / PlanDetailDocument） ---------- */

const canEditScenario = computed(() => {
  const status = props.docPlan.plan.value?.status;
  const active = props.docPlan.activeExecutions.value;
  // 与后端一致（TaskScenarioService.requireScenarioMutationAllowed 仅在有活跃执行时冻结）
  return status === 'PLANNING' || status === 'IN_REVIEW' || status === 'REPORTING'
    || ((status === 'EXECUTING' || status === 'PUBLISHED') && active === 0);
});
const canBindScript = canEditScenario;
const canEdit = computed(() => Boolean(props.docPlan.permissions.value.EDIT));
const canExecute = computed(() => Boolean(props.docPlan.permissions.value.EXECUTE));

/* ---------- 手写小节 × 场景实体合并视图（按名称对齐，实体驱动顺序） ---------- */

interface MethodSectionView {
  key: string;
  scenarioNo: string;
  name: string;
  testType: string;
  methodText: string;
  data: MethodScenarioData | null;
}

const sections = computed<MethodSectionView[]>(() => {
  const blocks = parseMethodSections(props.plan.body);
  const entities = data.value?.scenarios ?? [];
  const merged = entities.map((entity, index) => {
    const block = blocks.find((b) => b.name === entity.name);
    return {
      key: `entity-${entity.scenarioId}`,
      scenarioNo: `S${index + 1}`,
      name: entity.name,
      testType: entity.testType ?? block?.testType ?? '',
      methodText: block?.methodText ?? '',
      data: entity,
    };
  });
  const alignedNames = new Set(merged.map((item) => item.name));
  const orphans = blocks
    .filter((block) => !alignedNames.has(block.name))
    .map((block) => ({
      key: `doc-${block.heading}`,
      scenarioNo: blockNo(block.heading),
      name: block.name,
      testType: block.testType,
      methodText: block.methodText,
      data: null,
    }));
  return [...merged, ...orphans];
});

function blockNo(heading: string) {
  return heading.match(/^S\d+/)?.[0] ?? '·';
}

function presetOf(scenarioId: number) {
  const entity = props.scenarios.find((s) => s.id === scenarioId);
  return {
    threads: entity?.threads ?? 1,
    rampUpSec: entity?.rampUp ?? 0,
    durationSec: entity?.duration ?? 600,
  };
}

/* ---------- 关联脚本：小节头内联下拉，change 即绑定（数据源同 BindScriptDialog） ---------- */

const scriptOptions = computed(() =>
  currentProjectScripts.value.map((script) => ({
    value: script.id,
    label: `${script.name} · v${script.latestVersion}（#${script.id}）`,
  })),
);

async function onScriptChange(scenario: MethodScenarioData, scriptVersionId: number) {
  if (!scriptVersionId || scriptVersionId === scenario.scriptVersionId) return;
  try {
    await bindScenarioScriptApi(scenario.scenarioId, scriptVersionId);
    message.success('脚本已关联');
    emit('changed');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '关联失败');
  } finally {
    await load(); // 成功刷新脚本名 / 失败回显绑定前的值
  }
}
</script>

<style scoped>
/* 规格：test-method-optimization-prototype.html（scenario-module 系命名，与 ScenarioDesignModule 同族） */
.scenario-module-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.scenario-module-hint {
  color: var(--muted);
  font-size: 12px;
}

.scenario-card {
  margin-bottom: 10px;
  padding: 14px 16px;
  background: var(--canvas);
  border: 1px solid var(--line);
  border-radius: 10px;
}

.sc-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sc-id {
  flex: none;
  min-width: 34px;
  padding: 3px 6px;
  border-radius: 6px;
  background: var(--accent-soft);
  border: 1px solid var(--accent);
  color: var(--plan-accent-text);
  font-family: var(--font-data);
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}

.sc-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.sc-name {
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sc-type {
  flex: none;
  padding: 1px 8px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface);
  color: var(--muted);
  font-family: var(--font-data);
  font-size: 11px;
}

.sc-script {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  min-width: 0;
}

.sc-script-label {
  flex: none;
  color: var(--muted);
  font-size: 11.5px;
}

.sc-script-select {
  width: 240px;
}

.sc-bind {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.sc-bind.ok {
  background: var(--ok-soft);
  color: var(--plan-ok-text);
}

.sc-bind.warn {
  background: var(--warning-soft);
  color: var(--plan-warn-text);
}

.sc-method {
  margin-top: 10px;
}

.sc-method-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.sc-method-head .sc-label {
  color: var(--muted);
  font-size: 11px;
}

.sc-method-edit {
  color: var(--accent);
  font-size: 12px;
}

.method-desc {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  padding: 8px 12px;
}

.scenario-card.plain .method-desc,
.scenario-card.plain .sc-method {
  opacity: 0.92;
}

.method-desc :deep(.md-editor-previewWrapper) {
  padding: 0;
}

.method-desc :deep(.md-editor-preview) {
  font-size: 12.5px;
  color: var(--ink);
}
</style>
