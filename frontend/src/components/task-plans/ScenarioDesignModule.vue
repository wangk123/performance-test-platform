<template>
  <div class="scenario-module">
    <div class="scenario-module-head">
      <span class="scenario-module-hint">场景 = 文档章节（业务内容）+ 执行配置实体；脚本不进文档，评审通过后关联。</span>
      <a-button v-if="canEditScenario" type="primary" size="small" @click="emit('request-add')">添加场景</a-button>
    </div>

    <article v-for="block in blocks" :key="block.heading" class="scenario-card">
      <header class="sc-head">
        <span class="sc-id">{{ scenarioNo(block.heading) }}</span>
        <div class="sc-title">
          <strong class="sc-name">{{ block.name }}</strong>
          <span v-if="block.testType" class="sc-type">{{ block.testType }}</span>
        </div>
        <div class="sc-latest">
          <span class="sc-status-pill" :class="latestStatusClass(block.name)">{{ latestStatusText(block.name) }}</span>
          <span
            v-if="latestRecord(block.name)"
            class="sc-latest-meta mono"
            :title="latestRecord(block.name)"
          >{{ latestRecord(block.name) }}</span>
        </div>
      </header>

      <div class="sc-body">
        <div class="sc-field">
          <div class="sc-label">场景目的</div>
          <p class="sc-purpose">{{ block.purpose || '（待填写）' }}</p>
        </div>
        <div v-if="block.settings" class="sc-field">
          <div class="sc-label">场景设置（业务语言 → 自动翻译线程组）</div>
          <MdPreview
            class="scenario-settings plan-md"
            :model-value="block.settings"
            :theme="mdTheme"
            language="zh-CN"
          />
        </div>
      </div>

      <footer class="sc-footer">
        <span v-if="!scenarioOf(block.name)" class="sc-bind muted" title="文档有块但无场景实体（可编辑阶段保存后自动同步）">未建场景实体</span>
        <span
          v-else-if="scriptBound(block.name)"
          class="sc-bind ok"
          :title="`脚本版本 #${scenarioOf(block.name)?.scriptVersionId}`"
        >已关联脚本 <b class="mono">#{{ scenarioOf(block.name)?.scriptVersionId }}</b></span>
        <span v-else class="sc-bind warn">未关联脚本</span>
        <div class="sc-actions">
          <a-button size="small" :disabled="!scenarioOf(block.name)" @click="requestEdit(block.name)">编辑</a-button>
          <a-button
            v-if="!scriptBound(block.name) && canBindScript"
            size="small"
            @click="bindScript(block.name)"
          >关联脚本</a-button>
          <a-button
            v-if="scriptBound(block.name) && canExecute"
            size="small"
            type="primary"
            @click="run(block.name)"
          >执行</a-button>
        </div>
      </footer>

      <details v-if="block.records.length" class="sc-records">
        <summary>执行记录（{{ block.records.length }}）<span class="sc-records-chip">系统自动回填</span></summary>
        <ul class="sc-records-list">
          <li v-for="(record, index) in block.records" :key="index">
            <span class="rec-dot" :class="recordStatusClass(record)" />
            <span class="mono">{{ record }}</span>
          </li>
        </ul>
      </details>
    </article>

    <div v-if="blocks.length === 0" class="plan-empty">
      暂无场景。在评审前添加，或在评审通过后编写脚本并关联。
      <div v-if="canEditScenario" class="plan-empty-action">
        <a-button type="primary" size="small" @click="emit('request-add')">添加场景</a-button>
      </div>
    </div>

    <BindScriptDialog
      v-model:open="bindDialogOpen"
      :scenario-name="bindDialogScenario"
      :scripts="projectScripts"
      @confirm="confirmBind"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { useRouter } from 'vue-router';
import type { ScriptAsset, TaskPlan, TaskScenario } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import { useTheme } from '../../composables/useTheme';
import { useWorkspace } from '../../composables/useWorkspace';
import { parseScenarioBlocks } from '../../utils/plan-markdown';
import { bindScenarioScriptApi, precheckSkipApi } from '../../api/plan-doc';
import { triggerExecutionApi } from '../../api/task-plans';
import BindScriptDialog from './BindScriptDialog.vue';

const props = defineProps<{ docPlan: ReturnType<typeof usePlanDoc>; plan: TaskPlan; scenarios: TaskScenario[] }>();
const emit = defineEmits<{ (e: 'changed'): void; (e: 'request-add'): void; (e: 'request-edit', scenario: TaskScenario): void }>();

const router = useRouter();
const { themeMode } = useTheme();
const mdTheme = computed(() => (themeMode.value === 'dark' ? 'dark' : 'light'));
const { currentProjectScripts } = useWorkspace();
const projectScripts = computed<ScriptAsset[]>(() => currentProjectScripts.value);
const blocks = computed(() => parseScenarioBlocks(props.plan.body));

/* 关联脚本弹窗（替代 window.prompt，批次 E 原生弹窗清零） */
const bindDialogOpen = ref(false);
const bindDialogScenario = ref('');

const canEditScenario = computed(() => {
  const phase = props.docPlan.plan.value?.phase;
  const status = props.docPlan.plan.value?.status;
  // PUBLISH（终态）与未知阶段不在可编辑列表中，天然排除。
  return phase === 'DRAFT' || phase === 'REVIEW' || (phase === 'EXECUTION' && status !== 'RUNNING') || phase === 'REPORT';
});
const canBindScript = computed(() => canEditScenario.value);
const canExecute = computed(() => {
  const phase = props.docPlan.plan.value?.phase;
  return phase === 'EXECUTION' || phase === 'REPORT';
});

function scenarioOf(name: string) {
  return props.scenarios.find((s) => s.name === name) ?? null;
}

function requestEdit(name: string) {
  const scenario = scenarioOf(name);
  if (scenario) emit('request-edit', scenario);
}

function scriptBound(name: string) {
  return Boolean(scenarioOf(name)?.scriptVersionId);
}

function scenarioNo(heading: string) {
  return heading.match(/^S\d+/)?.[0] ?? 'S?';
}

function latestRecord(name: string) {
  // 执行记录按时间正序追加，最新一条在末尾。
  return blocks.value.find((b) => b.name === name)?.records.at(-1) ?? '';
}

function latestStatusOf(name: string): string | null {
  const entity = scenarioOf(name);
  if (entity?.latestExecutionStatus) return entity.latestExecutionStatus;
  const record = latestRecord(name);
  return record.match(/SUCCESS|FAILED|INTERRUPTED|CANCELLED/)?.[0] ?? null;
}

function latestStatusClass(name: string) {
  const status = latestStatusOf(name);
  if (!status) return 'none';
  if (status === 'SUCCESS') return 'ok';
  if (status === 'FAILED' || status === 'INTERRUPTED') return 'danger';
  if (status === 'CANCELLED') return 'none';
  return 'run';
}

function latestStatusText(name: string) {
  const status = latestStatusOf(name);
  if (!status) return '未执行';
  const text: Record<string, string> = { SUCCESS: '执行成功', FAILED: '执行失败', INTERRUPTED: '已中断', CANCELLED: '已取消' };
  return text[status] ?? status;
}

function recordStatusClass(record: string) {
  if (record.includes('SUCCESS')) return 'ok';
  if (record.includes('FAILED') || record.includes('INTERRUPTED')) return 'danger';
  return 'muted';
}

function bindScript(name: string) {
  const scenario = scenarioOf(name);
  if (!scenario) return;
  bindDialogScenario.value = name;
  bindDialogOpen.value = true;
}

async function confirmBind(scriptVersionId: number) {
  const scenario = scenarioOf(bindDialogScenario.value);
  if (!scenario) return;
  try {
    await bindScenarioScriptApi(scenario.id, scriptVersionId);
    message.success('脚本已关联');
    emit('changed');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '关联失败');
  }
}

async function run(name: string) {
  const scenario = scenarioOf(name);
  if (!scenario) return;
  try {
    const execution = await triggerExecutionApi(scenario.id, { idempotencyKey: `ui-${Date.now()}` });
    await router.push(`/projects/${props.plan.projectId}/executions/${execution.id}`);
  } catch (error) {
    const text = error instanceof Error ? error.message : '';
    if (text.includes('PLAN_PRECHECK_FAILED')) {
      confirmSkipPrecheck(text, name);
      return;
    }
    message.error(text || '执行失败');
  }
}

/** 跳过环境检查改 Modal.confirm（替代 window.confirm，批次 E 原生弹窗清零）。 */
function confirmSkipPrecheck(text: string, scenarioName: string) {
  Modal.confirm({
    title: '环境检查未通过，是否跳过并继续执行？',
    content: `${text}\n跳过将记录系统批注。`,
    okText: '跳过并执行',
    cancelText: '取消',
    onOk: async () => {
      await precheckSkipApi(props.plan.id);
      await run(scenarioName);
    },
  });
}
</script>

<style scoped>
/* 规格：plan-document-prototype.html 场景卡家族（sd-* / preset-table / 状态色徽章） */
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
  margin-bottom: 10px;
}

.sc-id {
  flex: none;
  min-width: 34px;
  padding: 3px 6px;
  border-radius: 6px;
  background: var(--accent-soft);
  border: 1px solid var(--accent);
  color: var(--accent);
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

.sc-latest {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  min-width: 0;
}

.sc-status-pill {
  flex: none;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  font-family: var(--font-data);
  white-space: nowrap;
}

.sc-status-pill.ok {
  background: var(--ok-soft);
  color: var(--ok);
}

.sc-status-pill.danger {
  background: var(--danger-soft);
  color: var(--danger);
}

.sc-status-pill.run {
  background: var(--accent-soft);
  color: var(--accent);
}

.sc-status-pill.none {
  background: var(--surface);
  border: 1px solid var(--line);
  color: var(--muted);
}

.sc-latest-meta {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 340px;
}

.sc-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sc-label {
  margin-bottom: 4px;
  color: var(--muted);
  font-size: 11px;
}

.sc-purpose {
  margin: 0;
  color: var(--ink);
  font-size: 13px;
  line-height: 1.7;
}

.scenario-settings {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  padding: 8px 12px;
}

.scenario-settings :deep(.md-editor-previewWrapper) {
  padding: 0;
}

.sc-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
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

.sc-bind b {
  font-weight: 600;
}

.sc-bind.ok {
  background: var(--ok-soft);
  color: var(--ok);
}

.sc-bind.warn {
  background: var(--warning-soft);
  color: var(--warn);
}

.sc-bind.muted {
  background: var(--surface);
  border: 1px dashed var(--line-strong);
  color: var(--muted);
}

.sc-actions {
  display: flex;
  gap: 8px;
}

.sc-records {
  margin-top: 10px;
  border-top: 1px dashed var(--line);
  padding-top: 8px;
}

.sc-records summary {
  color: var(--muted);
  font-size: 12.5px;
  cursor: pointer;
  user-select: none;
}

.sc-records-chip {
  margin-left: 8px;
  padding: 0 8px;
  border: 1px solid var(--accent);
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
}

.sc-records-list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sc-records-list li {
  display: flex;
  align-items: baseline;
  gap: 8px;
  color: var(--muted);
  font-size: 12px;
}

.rec-dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--muted);
  align-self: center;
}

.rec-dot.ok {
  background: var(--ok);
}

.rec-dot.danger {
  background: var(--danger);
}
</style>
