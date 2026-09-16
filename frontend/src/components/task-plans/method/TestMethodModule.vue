<template>
  <div class="scenario-module method-module">
    <div v-if="!preview" class="scenario-module-head">
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

        <div v-if="item.methodText || (item.data && canEdit && !preview)" class="sc-method">
          <div class="sc-method-head">
            <span class="sc-label">方法说明</span>
            <a-button
              v-if="item.data && canEdit && !preview && editingKey !== item.key"
              size="small"
              type="text"
              class="sc-method-edit"
              @click="startEdit(item)"
            >编辑</a-button>
          </div>
          <PlanSectionInlineEditor
            v-if="editingKey === item.key"
            :plan-id="plan.id"
            :title="METHOD_SECTION_TITLE"
            :heading="`${item.scenarioNo} ${item.name} · 方法说明`"
            :content="editingText"
            :busy="methodSaving"
            @save="(content: string) => saveMethod(item, content)"
            @cancel-request="requestCancelEdit"
            @update:dirty="methodDirty = $event"
          />
          <MdPreview
            v-else
            class="method-desc plan-md"
            :model-value="item.methodText || '（未填写，点「编辑」补充）'"
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
          :preview="preview"
          @refresh="load"
        />

        <!-- 监控证据区：按场景归档 TPS/RT/CPU/内存趋势 + 补充截图 -->
        <MethodEvidence
          v-if="item.data"
          :plan-id="plan.id"
          :scenario="item.data"
          :can-edit="canEdit && !preview"
          @refresh="load"
        />
      </article>
    </template>

    <div v-else-if="loaded && !preview" class="plan-empty">
      暂无场景。在评审前添加，或在文档中手写「测试方法」小节（与场景实体按名称对齐后可执行）。
      <div v-if="canEditScenario" class="plan-empty-action">
        <a-button type="primary" size="small" @click="emit('request-add')">+ 新增场景</a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { MethodScenarioData, MethodSectionData } from '../../../api/plan-method';
import { getPlanMethodApi } from '../../../api/plan-method';
import type { TaskPlan, TaskScenario } from '../../../types';
import type { usePlanDoc } from '../../../composables/usePlanDoc';
import { useTheme } from '../../../composables/useTheme';
import { useWorkspace } from '../../../composables/useWorkspace';
import { parseMethodSections, METHOD_SECTION_TITLE } from '../../../utils/plan-markdown';
import { bindScenarioScriptApi } from '../../../api/plan-doc';
import MethodExecTable from './MethodExecTable.vue';
import MethodEvidence from './MethodEvidence.vue';
import PlanSectionInlineEditor from '../PlanSectionInlineEditor.vue';

const props = withDefaults(
  defineProps<{
    docPlan: ReturnType<typeof usePlanDoc>;
    plan: TaskPlan;
    scenarios: TaskScenario[];
    /** 只读预览（Markdown 视图）：隐藏新增场景/绑定/编辑/执行入口，仅保留记录与证据展示。 */
    preview?: boolean;
  }>(),
  { preview: false },
);
const emit = defineEmits<{
  (e: 'changed'): void;
  (e: 'request-add'): void;
  (e: 'request-edit', scenario: TaskScenario): void;
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
const canBindScript = computed(() => !props.preview && canEditScenario.value);
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
  // 场景实体可能缺省（0/空），兜底到可执行的最小默认，避免新增执行行默认值非法被拦截
  return {
    threads: entity?.threads || 1,
    rampUpSec: entity?.rampUp || 0,
    durationSec: entity?.duration || 600,
  };
}

/* ---------- 关联脚本：小节头内联下拉，change 即绑定（数据源同 BindScriptDialog） ---------- */

/* ---------- 方法说明就地编辑：复用章节行内编辑器（格式工具条/AI 润色同其他章节），保存走整篇文档链路（含 409 冲突处理） ---------- */

const editingKey = ref<string | null>(null);
const editingText = ref('');
const methodSaving = ref(false);
const methodDirty = ref(false);

/** 编辑基底 = 自由文本：剥掉首行 `**方法说明**：` 标记与块尾「> 执行记录」指引行（展示仍走完整 methodText）。 */
function editableMethodText(methodText: string): string {
  const lines = methodText.split('\n');
  if (lines[0]?.trim().startsWith('**方法说明**')) lines.shift();
  if (lines.length && lines[lines.length - 1].trim().startsWith('> ')) lines.pop();
  return lines.join('\n').replace(/^\n+|\n+$/g, '');
}

function startEdit(item: MethodSectionView) {
  editingKey.value = item.key;
  editingText.value = editableMethodText(item.methodText ?? '');
  methodDirty.value = false;
}

/** 取消请求：脏稿先确认放弃（文案与 PlanDetailDocument 同口径），干净直接关闭。 */
function requestCancelEdit() {
  if (!methodDirty.value) {
    cancelEdit();
    return;
  }
  Modal.confirm({
    title: '放弃未保存的修改？',
    content: '方法说明草稿尚未保存，取消后修改将丢失。',
    okText: '放弃修改',
    okType: 'danger',
    cancelText: '继续编辑',
    onOk: cancelEdit,
  });
}

function cancelEdit() {
  editingKey.value = null;
  editingText.value = '';
  methodDirty.value = false;
}

async function saveMethod(item: MethodSectionView, content: string) {
  const body = props.docPlan.plan.value?.body;
  if (!body) return;
  const next = replaceMethodText(body, item.name, content.trim());
  if (next === null) {
    message.error('未能在文档中定位该小节的方法说明区');
    return;
  }
  methodSaving.value = true;
  try {
    const result = await props.docPlan.saveDocument(next);
    if (result === 'ok') {
      cancelEdit();
    } else if (result === 'conflict') {
      message.warning('文档已被他人修改，已加载最新内容，请重新编辑');
      cancelEdit();
    }
  } finally {
    methodSaving.value = false;
  }
}

/** 在 `### S{n} {name} · TYPE` 小节内替换方法说明自由区（保留标题与「> 执行记录」指引行）。 */
function replaceMethodText(body: string, scenarioName: string, text: string): string | null {
  const headRe = new RegExp(`^### S\\d+ ${escapeRegExp(scenarioName)} ·.*$`, 'm');
  const head = body.match(headRe);
  if (head === null) return null;
  const blockStart = head.index!;
  // 从标题行之后开始找下一小节/章边界（若从标题中间截断，`### ` 会变成 `## ` 误命中）
  const bodyStart = body.indexOf('\n', blockStart);
  const contentStart = bodyStart === -1 ? body.length : bodyStart + 1;
  const nextSep = body.slice(contentStart).search(/^### |^## /m);
  const blockEnd = nextSep === -1 ? body.length : contentStart + nextSep;
  const block = body.slice(blockStart, blockEnd);
  const marker = '**方法说明**：';
  const markerIdx = block.indexOf(marker);
  if (markerIdx === -1) return null;
  const tailStart = markerIdx + marker.length;
  const guideIdx = block.indexOf('\n> ', tailStart);
  const tailEnd = guideIdx === -1 ? block.length : guideIdx;
  const newBlock = block.slice(0, tailStart) + (text ? `\n${text}\n` : '\n') + (guideIdx === -1 ? '' : block.slice(guideIdx));
  return body.slice(0, blockStart) + newBlock + body.slice(blockEnd);
}

function escapeRegExp(text: string) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

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
