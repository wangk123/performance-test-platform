<template>
  <section class="report-tab">
    <div class="report-layout">
      <div class="report-main">
        <div class="card report-stage-card">
          <h4>报告阶段</h4>
          <p class="report-stage-note">
            当前阶段 <b>{{ phaseText }} · {{ statusText }}</b>。生成 = 聚合执行摘要回填"十一、结论"（达成表实际列 + 结果总览），
            同一份文档从计划走到报告。
          </p>
          <div class="report-actions">
            <a-button v-if="can('TO_REPORT')" type="primary" @click="doc.transition('to-report', undefined, '已进入报告阶段')">进入报告</a-button>
            <a-button v-if="can('GENERATE_REPORT')" type="primary" :loading="generating" @click="generate">生成报告</a-button>
          </div>
        </div>

        <h3 class="report-block-title">验收判定</h3>
        <div v-if="verdict && !verdict.present" class="plan-note info">
          本计划无验收指标（摸底/排查型），总体结论由人工填写。
        </div>
        <div v-else-if="verdict && !verdict.available" class="plan-note warn">
          报告未生成，判定不可读（需 REPORT · 已生成）。
        </div>
        <template v-else-if="verdict">
          <div class="card verdict-card">
            <div class="verdict-head">
              <span class="verdict-pill" :class="verdictClass(verdict.overall)">{{ verdictText(verdict.overall) }}</span>
              <span v-if="verdict.prefillConclusion" class="verdict-prefill">{{ verdict.prefillConclusion }}</span>
            </div>
            <a-table
              v-if="missedRows.length"
              class="verdict-table"
              :columns="verdictColumns"
              :data-source="missedRows"
              :pagination="false"
              size="small"
              row-key="objectName"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'status'">
                  <span class="verdict-pill sm" :class="rowClass(record.status)">{{ rowText(record.status) }}</span>
                </template>
                <a v-else-if="column.key === 'drill' && record.executionId" @click="drilldown(record)">查看执行</a>
              </template>
            </a-table>
          </div>
        </template>

        <h3 class="report-block-title">结论章节预览</h3>
        <div class="card report-conclusion">
          <MdPreview class="plan-md" :model-value="conclusion ?? '（暂无结论章节）'" :theme="mdTheme" language="zh-CN" />
        </div>

        <h3 class="report-block-title">场景执行概览</h3>
        <div class="card report-scenarios">
          <a-table
            :columns="columns"
            :data-source="rows"
            :pagination="false"
            row-key="name"
            size="small"
            :locale="{ emptyText: '暂无场景' }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'script'">
                <span v-if="record.scriptVersionId" class="bind-chip ok mono">#{{ record.scriptVersionId }}</span>
                <span v-else class="bind-chip warn">未关联</span>
              </template>
              <template v-else-if="column.key === 'latest'">
                <span class="mono latest-cell" :title="record.latest">{{ record.latest }}</span>
              </template>
            </template>
          </a-table>
        </div>
      </div>

      <aside class="report-side">
        <div class="card side-card">
          <h4>判等说明</h4>
          <p class="side-note">
            达成 = ok / 未达成 = danger / 无法判定 = warn；<br />
            未达标清单来自 P0-3 判等引擎（场景级 + 交易级含 P99），复测重置后需重新生成报告。
          </p>
        </div>
        <div class="card side-card side-accent">
          <h4>一稿走到头</h4>
          <p class="side-note">
            报告不是独立文档：执行结果回填同一份文档的执行记录与结论章节，发布时这份文档就是报告。新执行启动作废旧报告。
          </p>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { PlanVerdict, PlanVerdictRow, TaskScenario } from '../../types';
import { useTheme } from '../../composables/useTheme';
import { statusLabel } from '../../composables/usePlanDoc';
import { getPlanVerdictApi } from '../../api/plan-doc';
import { extractSection, parseExecutionRecords } from '../../utils/plan-markdown';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc>; scenarios: TaskScenario[] }>();

const router = useRouter();
const { themeMode } = useTheme();
const mdTheme = computed(() => (themeMode.value === 'dark' ? 'dark' : 'light'));
const generating = ref(false);
const verdict = ref<PlanVerdict | null>(null);

const PHASE_TEXT: Record<string, string> = {
  DRAFT: '草稿', REVIEW: '评审', EXECUTION: '执行', REPORT: '报告', PUBLISH: '发布',
};
const phase = computed(() => props.doc.plan.value?.phase ?? 'DRAFT');
const status = computed(() => props.doc.plan.value?.status ?? 'DRAFT');
const phaseText = computed(() => PHASE_TEXT[phase.value] ?? phase.value);
const statusText = computed(() => statusLabel(phase.value, status.value));

async function loadVerdict() {
  const planId = props.doc.plan.value?.id;
  if (planId) verdict.value = await getPlanVerdictApi(planId).catch(() => null);
}

onMounted(() => {
  void loadVerdict();
});

/* verdict → token 语义色（替代 Ant green/red/orange 预设色） */
function verdictClass(overall: string) {
  return { PASSED: 'passed', FAILED: 'failed', INDETERMINATE: 'indeterminate' }[overall] ?? 'none';
}

function verdictText(overall: string) {
  return { PASSED: '达成', FAILED: '未达成', INDETERMINATE: '无法判定', NONE: '—' }[overall] ?? overall;
}

function rowClass(rowStatus: string) {
  return { ACHIEVED: 'passed', MISSED: 'failed', INDETERMINATE: 'indeterminate' }[rowStatus] ?? 'none';
}

function rowText(rowStatus: string) {
  return { ACHIEVED: '达成', MISSED: '未达标', INDETERMINATE: '无法判定' }[rowStatus] ?? rowStatus;
}

const missedRows = computed(() => verdict.value?.rows.filter((r) => r.status === 'MISSED') ?? []);

function drilldown(row: PlanVerdictRow) {
  const plan = props.doc.plan.value;
  if (plan && row.executionId) {
    void router.push({ name: 'project-execution-detail', params: { projectId: plan.projectId, executionId: row.executionId } });
  }
}

const columns = [
  { title: '场景', dataIndex: 'name', key: 'name' },
  { title: '测试类型', dataIndex: 'testType', key: 'testType' },
  { title: '脚本', key: 'script' },
  { title: '最新执行（回填块解析）', dataIndex: 'latest', key: 'latest' },
];

const verdictColumns = [
  { title: '对象', dataIndex: 'objectName', key: 'objectName' },
  { title: '指标', dataIndex: 'metricRaw', key: 'metricRaw' },
  { title: '目标', dataIndex: 'targetRaw', key: 'targetRaw' },
  { title: '实际', dataIndex: 'actualValue', key: 'actualValue' },
  { title: '状态', key: 'status', width: 96 },
  { title: '下钻', key: 'drill' },
];

const conclusion = computed(() => extractSection(props.doc.plan.value?.body, '十一、结论'));
const rows = computed(() =>
  props.scenarios.map((scenario) => {
    // 执行记录按时间正序追加，最新一条在末尾。
    const records = parseExecutionRecords(props.doc.plan.value?.body, scenario.name);
    return {
      name: scenario.name,
      testType: scenario.testType ?? '—',
      scriptVersionId: scenario.scriptVersionId,
      latest: records.at(-1) ?? '未执行',
    };
  }),
);

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

async function generate() {
  generating.value = true;
  try {
    const ok = await props.doc.transition('generate-report', undefined, '报告已生成');
    if (ok) await loadVerdict();
  } finally {
    generating.value = false;
  }
}
</script>

<style scoped>
/* 规格：plan-document-prototype.html .report-layout / .side-card / token 色 verdict 家族 */
.report-tab {
  display: flex;
  flex-direction: column;
}

.report-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: start;
}

.report-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
}

.report-stage-card {
  padding: 16px 18px;
  margin-bottom: 16px;
}

.report-stage-card h4 {
  margin: 0 0 8px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.6px;
  text-transform: uppercase;
}

.report-stage-note {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8;
}

.report-stage-note b {
  color: var(--ink);
}

.report-actions {
  display: flex;
  gap: 8px;
}

.report-block-title {
  margin: 0 0 8px;
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
}

.report-block-title + .card,
.report-block-title + .plan-note {
  margin-bottom: 16px;
}

/* token 色信息/警告条（替代 a-alert 预设色） */
.plan-note {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  margin-bottom: 16px;
}

.plan-note.info {
  background: var(--accent-soft);
  border: 1px solid var(--accent);
  color: var(--accent);
}

.plan-note.warn {
  background: var(--warning-soft);
  border: 1px solid var(--warn);
  color: var(--warn);
}

.plan-note.ok {
  background: var(--ok-soft);
  border: 1px solid var(--ok);
  color: var(--ok);
}

.verdict-card {
  padding: 14px 16px;
  margin-bottom: 16px;
}

.verdict-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.verdict-prefill {
  color: var(--muted);
  font-size: 12.5px;
}

/* verdict token 色卡 */
.verdict-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.verdict-pill.sm {
  padding: 1px 8px;
  font-size: 11px;
}

.verdict-pill.passed {
  background: var(--ok-soft);
  color: var(--ok);
}

.verdict-pill.failed {
  background: var(--danger-soft);
  color: var(--danger);
}

.verdict-pill.indeterminate {
  background: var(--warning-soft);
  color: var(--warn);
}

.verdict-pill.none {
  background: var(--canvas);
  border: 1px solid var(--line);
  color: var(--muted);
}

.report-conclusion {
  padding: 16px 20px;
  margin-bottom: 16px;
}

.report-scenarios {
  padding: 4px 12px;
}

.bind-chip {
  display: inline-flex;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.bind-chip.ok {
  background: var(--ok-soft);
  color: var(--ok);
}

.bind-chip.warn {
  background: var(--warning-soft);
  color: var(--warn);
}

.latest-cell {
  color: var(--muted);
  font-size: 12px;
}

.report-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-card {
  padding: 16px 18px;
}

.side-card h4 {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.6px;
  text-transform: uppercase;
}

.side-note {
  margin: 0;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.8;
}

.side-accent {
  background: var(--accent-soft);
  border-color: var(--accent);
}

.side-accent h4 {
  color: var(--accent);
}
</style>
