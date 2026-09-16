<template>
  <div v-if="!preview || visibleRows.length" class="method-exec">
    <div class="tbl-wrap">
      <table class="exec">
        <thead>
          <tr>
            <th class="idx-h">#</th>
            <th class="num-h">用户数</th>
            <th class="num-h">Ramp-up(s)</th>
            <th class="num-h">压测时间</th>
            <th class="num-h">样本数</th>
            <th class="num-h">成功率</th>
            <th class="num-h">平均RT(ms)</th>
            <th class="num-h">P95(ms)</th>
            <th class="num-h">TPS</th>
            <th>状态</th>
            <th>执行时间</th>
            <th v-if="!preview" class="ops-h">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(row, index) in visibleRows"
            :key="row.executionId"
            class="data-row"
            @click="openDetail($event, row)"
          >
            <td class="row-no">{{ index + 1 }}</td>
            <td class="num">{{ row.threads }}</td>
            <td class="num">{{ row.rampUpSec }}</td>
            <td class="num dim">{{ durationText(row.durationSec) }}</td>
            <td class="num" :class="{ dim: row.samples == null }">{{ formatSamples(row.samples) }}</td>
            <td class="num" :class="{ dim: row.successRate == null }">{{ formatRate(row.successRate) }}</td>
            <td class="num" :class="{ dim: row.avgRtMs == null }">{{ formatMs(row.avgRtMs) }}</td>
            <td class="num" :class="{ dim: row.p95Ms == null }">{{ formatMs(row.p95Ms) }}</td>
            <td class="num strong" :class="{ dim: row.tps == null }">{{ formatTps(row.tps) }}</td>
            <td>
              <span class="badge" :class="statusClass(row)">
                <span v-if="isRunning(row)" class="spinner" />{{ statusText(row) }}
              </span>
            </td>
            <td class="dim">{{ startedAtText(row) }}</td>
            <td v-if="!preview">
              <span class="ops-cell">
                <button
                  v-if="!isRunning(row)"
                  class="icon-op"
                  type="button"
                  :title="props.canExecute ? `重新执行（${row.threads}并发 / ${row.rampUpSec}s / ${durationText(row.durationSec)}）` : '需进入执行阶段'"
                  :disabled="!props.canExecute || triggeringId !== null"
                  @click.stop="rerun(row)"
                >
                  <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M13.6 8A5.6 5.6 0 1 1 11 3.3" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/><path d="M11.2 1.2l.2 2.5-2.5.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </button>
                <button class="icon-op" type="button" title="查看执行详情" @click.stop="openRow(row)">
                  <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M1.6 8S4 3.8 8 3.8 14.4 8 14.4 8 12 12.2 8 12.2 1.6 8 1.6 8z" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><circle cx="8" cy="8" r="2.1" fill="none" stroke="currentColor" stroke-width="1.4"/></svg>
                </button>
                <button
                  v-if="!isRunning(row)"
                  class="icon-op danger"
                  type="button"
                  title="删除"
                  @click.stop="askDelete(row)"
                >
                  <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M3 4.5h10M6.5 4.2V3h3v1.2M4.8 4.5l.6 8.2h5.2l.6-8.2M6.7 7v3.3M9.3 7v3.3" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </button>
              </span>
            </td>
          </tr>
          <tr
            v-for="(m, mi) in manualRows"
            :key="m.key"
            class="data-row manual-row"
          >
            <td class="row-no">{{ visibleRows.length + mi + 1 }}</td>
            <td class="num">{{ m.threads }}</td>
            <td class="num">{{ m.rampUpSec }}</td>
            <td class="num">{{ durationText(m.durationSec) }}</td>
            <td class="num dim">—</td>
            <td class="num dim">—</td>
            <td class="num dim">—</td>
            <td class="num dim">—</td>
            <td class="num dim">—</td>
            <td><span class="badge pending">待执行</span></td>
            <td class="dim">—</td>
            <td v-if="!preview">
              <span class="ops-cell">
                <button
                  class="icon-op"
                  type="button"
                  :title="props.canExecute ? '执行此行' : '需进入执行阶段'"
                  :disabled="!props.canExecute || triggeringId !== null"
                  @click.stop="executeManualRow(m)"
                >
                  <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M5.4 3.5v9l7.6-4.5z" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg>
                </button>
                <button class="icon-op danger" type="button" title="移除此行" @click.stop="removeManualRow(m.key)">
                  <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M3 4.5h10M6.5 4.2V3h3v1.2M4.8 4.5l.6 8.2h5.2l.6-8.2M6.7 7v3.3M9.3 7v3.3" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </button>
              </span>
            </td>
          </tr>
          <tr v-if="!preview" class="new-run">
            <td />
            <td class="input-col">
              <a-input-number v-model:value="form.threads" class="cell-input" size="small" :min="1" :precision="0" :controls="false" :disabled="inputsDisabled" />
            </td>
            <td class="input-col">
              <a-input-number v-model:value="form.rampUpSec" class="cell-input" size="small" :min="0" :precision="0" :controls="false" :disabled="inputsDisabled" />
            </td>
            <td class="input-col">
              <a-input-number v-model:value="form.durationSec" class="cell-input" size="small" :min="1" :precision="0" :controls="false" :disabled="inputsDisabled" />
            </td>
            <td class="dim new-run-hint" colspan="5">结果列执行完成后自动回填，无需手填</td>
            <td colspan="3" class="run-cell">
              <button
                class="btn-add"
                type="button"
                :disabled="inputsDisabled"
                title="按当前参数添加一行（不执行）"
                @click="addManualRow"
              >＋ 添加行</button>
              <button
                class="icon-op"
                type="button"
                :disabled="runDisabled || triggering"
                :title="runTooltip || '发起执行'"
                @click="run"
              >
                <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M5.4 3.5v9l7.6-4.5z" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="!preview && hiddenCount > 0" class="removed-bar">
      <span>已从表格移出 {{ hiddenCount }} 条记录（数据保留，执行详情页历史可见）</span>
      <button type="button" @click="showHidden = !showHidden">{{ showHidden ? '收起' : '恢复展示' }}</button>
    </div>
    <div v-if="!preview && showHidden && hiddenRows.length" class="hidden-list">
      <div v-for="row in hiddenRows" :key="row.executionId" class="hidden-row">
        <span class="mono">#{{ row.executionId }}</span>
        <span class="hidden-name">{{ row.executionName }}</span>
        <span class="mono">{{ row.threads }} 并发</span>
        <span class="dim">{{ startedAtText(row) }}</span>
        <button type="button" :disabled="restoringId === row.executionId" @click="restore(row)">恢复展示</button>
      </div>
    </div>

    <MethodDeleteModal
      v-model:open="delOpen"
      :row="delRow"
      :scenario-no="scenarioNo"
      :scenario-name="scenario.name"
      :started-at-text="delRow ? startedAtText(delRow) : '—'"
      @done="emit('refresh')"
    />
  </div>
</template>

<script lang="ts">
/** 手动参数行按场景缓存（模块级，会话内跨导航保留；不落库，刷新页面后清空）。 */
interface ManualRow { key: number; threads: number; rampUpSec: number; durationSec: number }
const manualRowStore = reactive(new Map<number, ManualRow[]>());
let manualKeySeed = 0;
</script>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { useRouter } from 'vue-router';
import type { MethodExecutionRow, MethodScenarioData } from '../../../api/plan-method';
import type { ScenarioExecution, TaskPlan } from '../../../types';
import { triggerExecutionApi, toUiStatus, executionStatusText } from '../../../api/task-plans';
import { precheckSkipApi } from '../../../api/plan-doc';
import { setExecutionVisibilityApi } from '../../../api/plan-method';
import MethodDeleteModal from './MethodDeleteModal.vue';

const props = defineProps<{
  plan: TaskPlan;
  scenario: MethodScenarioData;
  /** 合并展示顺序号（S1…），行序号与执行命名共用。 */
  scenarioNo: string;
  /** 无历史执行时的参数缺省（场景实体 threads/rampUp/duration）。 */
  preset: { threads: number; rampUpSec: number; durationSec: number };
  canExecute: boolean;
  /** 只读预览（Markdown 视图）：隐藏新增执行行/操作列/移出管理，仅展示记录。 */
  preview?: boolean;
}>();
const emit = defineEmits<{ (e: 'refresh'): void }>();

// 跳转路径同 useTaskPlans().openExecution；这里直接 router.push，避免每表格实例重复注册组合式的 loadPlans 轮询
const router = useRouter();

const NON_TERMINAL = ['QUEUED', 'PENDING', 'RUNNING', 'STOPPING'];
const isRunning = (row: MethodExecutionRow) => NON_TERMINAL.includes(row.status);

const visibleRows = computed(() => props.scenario.executions.filter((row) => !row.hidden));
const hiddenRows = computed(() => props.scenario.executions.filter((row) => row.hidden));
const hiddenCount = computed(() => Math.max(props.scenario.hiddenCount, hiddenRows.value.length));
const showHidden = ref(false);

/* ---------- 格式化（原型口径：数字右对齐等宽，未终态显示 —） ---------- */

function durationText(sec: number) {
  if (!sec) return '—';
  return sec % 60 === 0 ? `${sec / 60}min` : `${sec}s`;
}

function formatSamples(value: number | null) {
  return value == null ? '—' : value.toLocaleString('en-US');
}

function formatRate(value: number | null) {
  return value == null ? '—' : `${value.toFixed(1)}%`;
}

function formatMs(value: number | null) {
  return value == null ? '—' : String(Math.round(value));
}

function formatTps(value: number | null) {
  return value == null ? '—' : value.toFixed(1);
}

function statusText(row: MethodExecutionRow) {
  return executionStatusText(toUiStatus(row.status as ScenarioExecution['status']));
}

function statusClass(row: MethodExecutionRow) {
  const ui = toUiStatus(row.status as ScenarioExecution['status']);
  if (ui === 'SUCCESS') return 'ok';
  if (ui === 'FAILED' || ui === 'INTERRUPTED') return 'fail';
  return 'running';
}

function startedAtText(row: MethodExecutionRow) {
  // 后端 yyyy-MM-dd HH:mm，表格按原型取 MM-dd HH:mm
  return row.startedAtText ? row.startedAtText.slice(5) : '—';
}

/* ---------- 行跳转 / 删行入口 ---------- */

function openDetail(event: MouseEvent, row: MethodExecutionRow) {
  if ((event.target as HTMLElement).closest('button, input, .ant-input-number')) return;
  openRow(row);
}

function openRow(row: MethodExecutionRow) {
  void router.push(`/projects/${props.plan.projectId}/executions/${row.executionId}`);
}

const delOpen = ref(false);
const delRow = ref<MethodExecutionRow | null>(null);

function askDelete(row: MethodExecutionRow) {
  delRow.value = row;
  delOpen.value = true;
}

const restoringId = ref<number | null>(null);

async function restore(row: MethodExecutionRow) {
  restoringId.value = row.executionId;
  try {
    await setExecutionVisibilityApi(row.executionId, false);
    message.success('已恢复展示');
    emit('refresh');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败');
  } finally {
    restoringId.value = null;
  }
}

/* ---------- 新增执行行：默认参数 = 最近一次执行，无历史取场景 preset ---------- */

const form = reactive({ threads: 1, rampUpSec: 0, durationSec: 600 });

/** 当前场景的手动参数行（模块级缓存，跨导航保留）。 */
const manualRows = computed<ManualRow[]>(() => manualRowStore.get(props.scenario.scenarioId) ?? []);

function setManualRows(rows: ManualRow[]) {
  manualRowStore.set(props.scenario.scenarioId, rows);
}

function addManualRow() {
  if (!form.threads || !form.durationSec) {
    message.warning('请填写用户数与压测时间');
    return;
  }
  setManualRows([...manualRows.value, { key: ++manualKeySeed, threads: form.threads, rampUpSec: form.rampUpSec ?? 0, durationSec: form.durationSec }]);
}

function removeManualRow(key: number) {
  setManualRows(manualRows.value.filter((m) => m.key !== key));
}

watch(() => props.scenario.scenarioId, resetForm, { immediate: true });

function resetForm() {
  const last = props.scenario.executions.at(-1);
  form.threads = last?.threads || props.preset.threads;
  form.rampUpSec = last?.rampUpSec ?? props.preset.rampUpSec;
  form.durationSec = last?.durationSec || props.preset.durationSec;
}

const inputsDisabled = computed(() => !props.scenario.scriptVersionId || !props.canExecute);
const runTooltip = computed(() => {
  if (!props.scenario.scriptVersionId) return '先绑定压测脚本';
  if (!props.canExecute) return '需进入执行阶段';
  return '';
});
const runDisabled = computed(() => Boolean(runTooltip.value));
const triggering = ref(false);

async function run() {
  if (runDisabled.value || triggering.value) return;
  if (!form.threads || !form.durationSec) {
    message.warning('请填写用户数与压测时间');
    return;
  }
  triggering.value = true;
  try {
    await doTrigger({ threads: form.threads, rampUpSec: form.rampUpSec ?? 0, durationSec: form.durationSec });
  } finally {
    triggering.value = false;
  }
}

/** 预检失败跳过确认（与 ScenarioDesignModule.run 同模式）。 */
function confirmSkipPrecheck(text: string) {
  Modal.confirm({
    title: '环境检查未通过，是否跳过并继续执行？',
    content: `${text}\n跳过将记录系统批注。`,
    okText: '跳过并执行',
    cancelText: '取消',
    onOk: async () => {
      await precheckSkipApi(props.plan.id);
      await run();
    },
  });
}

/** 统一触发入口：就地刷新聚合（新执行行立即出现，进度由轮询跟进），不跳转；预检失败走跳过确认，其余报错 toast。返回是否成功。 */
async function doTrigger(overrides: { threads: number; rampUpSec: number; durationSec: number }): Promise<boolean> {
  try {
    await triggerExecutionApi(props.scenario.scenarioId, {
      executionName: `${props.scenarioNo} ${overrides.threads}并发 ${timeLabel()}`,
      idempotencyKey: `ui-${Date.now()}`,
      overrides,
    });
    emit('refresh');
    return true;
  } catch (error) {
    const text = error instanceof Error ? error.message : '';
    if (text.includes('PLAN_PRECHECK_FAILED')) {
      confirmSkipPrecheck(text);
      return false;
    }
    message.error(text || '执行失败');
    return false;
  }
}

/** 行级重新执行：按该行参数快照再发起，成功后旧行移出表格（数据保留可恢复），新行原地顶替——覆盖语义，不限次数。 */
const triggeringId = ref<number | null>(null);

async function rerun(row: MethodExecutionRow) {
  if (!props.canExecute || triggeringId.value !== null) return;
  triggeringId.value = row.executionId;
  try {
    if (await doTrigger({ threads: row.threads, rampUpSec: row.rampUpSec, durationSec: row.durationSec })) {
      await setExecutionVisibilityApi(row.executionId, true); // 覆盖原行：旧行移出（可恢复展示），新执行顶位
      emit('refresh');
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败');
  } finally {
    triggeringId.value = null;
  }
}

async function executeManualRow(m: ManualRow) {
  if (!props.canExecute || triggeringId.value !== null) return;
  triggeringId.value = m.key;
  try {
    if (await doTrigger({ threads: m.threads, rampUpSec: m.rampUpSec, durationSec: m.durationSec })) {
      removeManualRow(m.key); // 该参数行已升级为真实执行记录，避免重复
    }
  } finally {
    triggeringId.value = null;
  }
}
function timeLabel() {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}
</script>

<style scoped>
/* 规格：test-method-optimization-prototype.html 执行记录表家族（scenario-module 系命名） */
.tbl-wrap {
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow-x: auto;
}

table.exec {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}

.exec thead th {
  text-align: left;
  font-weight: 600;
  color: var(--muted);
  font-size: 11.5px;
  background: var(--surface-soft);
  padding: 8px 10px;
  border-bottom: 1px solid var(--line);
  white-space: nowrap;
}

.exec thead th.num-h,
.exec thead th.idx-h { text-align: right; }
.exec thead th.ops-h { text-align: right; width: 64px; }

.exec tbody td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--line);
  white-space: nowrap;
}

.exec tbody tr:last-child td { border-bottom: none; }

.exec .num {
  font-family: var(--font-data);
  text-align: right;
  font-size: 12px;
}

.exec .num.strong { font-weight: 600; }
.exec .dim { color: var(--muted); font-size: 11.5px; }

.exec tr.data-row {
  cursor: pointer;
  transition: background .12s;
}

.exec tr.data-row:hover { background: var(--surface-soft); }
.exec .row-no { color: var(--muted); font: 600 11.5px var(--font-data); text-align: right; }

.ops-cell {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  vertical-align: middle;
  float: right;
}

.icon-op {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  background: none;
  color: var(--muted);
  cursor: pointer;
  border-radius: 6px;
  padding: 0;
}

.icon-op svg { width: 15px; height: 15px; }
.icon-op:hover { color: var(--accent); background: var(--accent-soft); }
.icon-op.danger:hover { color: var(--danger); background: var(--danger-soft); }
.icon-op:disabled { opacity: .35; cursor: not-allowed; }
.icon-op:disabled:hover { color: var(--muted); background: none; }

.badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 999px;
  font-size: 11px;
  padding: 1px 9px;
  font-weight: 600;
}

.badge.ok { color: var(--ok); background: var(--ok-soft); }
.badge.running { color: var(--accent); background: var(--accent-soft); }
.badge.fail { color: var(--danger); background: var(--danger-soft); }
.badge.pending { color: var(--muted); background: var(--surface-soft); border: 1px solid var(--line); }

.spinner {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1.5px solid currentColor;
  border-top-color: transparent;
  animation: spin .8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.exec tr.new-run td {
  background: var(--surface-soft);
  padding: 6px 8px;
}

/* 手动添加的参数行：略微弱化，与真实执行记录区分 */
.exec tr.manual-row td { color: var(--ink); }

.btn-add {
  margin-right: 10px;
  padding: 3px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: var(--surface);
  color: var(--ink);
  font-family: inherit;
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
}

.btn-add:hover { color: var(--plan-accent-text); border-color: var(--accent); }
.btn-add:disabled { opacity: .45; cursor: not-allowed; }

/* 输入框贴单元格右缘，与右对齐的列头/数据列对齐 */
.exec tr.new-run td.input-col {
  text-align: right;
}

.exec :deep(.cell-input) {
  width: 76px;
}

.exec :deep(.cell-input .ant-input-number-input) {
  text-align: right;
  font-family: var(--font-data);
  font-size: 12px;
}

.new-run-hint { text-align: left; }
.run-cell { text-align: right; }

.removed-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--muted);
  margin-top: 8px;
}

.removed-bar button,
.hidden-row button {
  border: none;
  background: none;
  color: var(--accent);
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
  padding: 0;
}

.removed-bar button:hover,
.hidden-row button:hover { text-decoration: underline; }

.hidden-list {
  margin-top: 6px;
  padding: 6px 10px;
  border: 1px dashed var(--line-strong);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hidden-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--ink);
}

.hidden-row .hidden-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
