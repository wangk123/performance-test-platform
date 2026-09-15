<template>
  <section class="ec-card">
    <header class="ec-card-head">
      <div>
        <h3>检查结果</h3>
        <p>矩阵 = 机器 × 检查项；问题清单勾选后批量修复，修复可回滚</p>
      </div>
    </header>

    <div class="ec-summary-bar">
      <span class="ec-pill"><span class="ec-dot ok" />通过 <b>{{ summary?.passed ?? 0 }}</b></span>
      <span class="ec-pill"><span class="ec-dot warn" />待处理 <b>{{ summary?.warned ?? 0 }}</b></span>
      <span class="ec-pill"><span class="ec-dot fixed" />已修复 <b>{{ fixedCount }}</b></span>
      <span v-if="summary" class="ec-when">{{ formatDate(summary.startedAt) }} · {{ summary.triggeredBy }} 触发</span>
      <span class="ec-spacer" />
      <a-select
        :value="runId ?? undefined"
        :options="historyOptions"
        class="ec-history"
        placeholder="历史运行"
        @change="selectRun"
      />
      <a-button type="primary" :loading="triggering" @click="trigger">运行检查</a-button>
    </div>

    <a-table
      :columns="matrixColumns"
      :data-source="matrixRows"
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 720 }"
      row-key="rowKey"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'host'">
          <span class="ec-host">{{ record.host ?? '平台内' }}</span>
        </template>
        <template v-else-if="column.key === 'module'">
          <span class="ec-module">{{ record.module }}</span>
        </template>
        <template v-else-if="column.key === 'state'">
          <span class="ec-badge" :class="badgeClass(record)">{{ badgeText(record) }}</span>
        </template>
        <template v-else-if="column.key === 'detail'">
          <span class="ec-detail">{{ record.detail || record.suggestion || '—' }}</span>
        </template>
      </template>
      <template #empty><span class="ec-module">尚无检查结果，点击「运行检查」发起一次</span></template>
    </a-table>

    <EnvCheckFixList :rows="issueRows" :fixing="fixing" @apply="applyFixes" />
    <EnvCheckFixHistory :fixes="fixes" :item-label="itemLabel" @rolled-back="reloadDetail" />
  </section>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import type { EnvCheckFixRecord, EnvCheckRunDetail, EnvCheckRunRow, EnvCheckRunSummary } from '../../types';
import {
  applyEnvCheckFixesApi,
  fetchEnvCheckFixesApi,
  fetchEnvCheckItemsApi,
  fetchEnvCheckRunDetailApi,
  fetchEnvCheckRunsApi,
  triggerEnvCheckApi,
} from '../../api/env-check';
import { formatDate } from '../../utils/format';
import { rowKeyOf, type EnvCheckIssueRow, type EnvCheckMatrixRow } from './envCheckResultModel';
import EnvCheckFixList from './EnvCheckFixList.vue';
import EnvCheckFixHistory from './EnvCheckFixHistory.vue';

const props = defineProps<{ planId: number }>();
const emit = defineEmits<{ (e: 'goto-credentials'): void }>();

const loading = ref(false);
const triggering = ref(false);
const fixing = ref(false);
const runs = ref<EnvCheckRunSummary[]>([]);
const runId = ref<number | null>(null);
const detail = ref<EnvCheckRunDetail | null>(null);
const fixes = ref<EnvCheckFixRecord[]>([]);
const labels = ref(new Map<string, string>());

const summary = computed(() => detail.value?.run ?? runs.value.find((run) => run.id === runId.value) ?? null);
const fixedCount = computed(() => detail.value?.rows.filter((row) => row.state === 'FIXED').length ?? 0);

const historyOptions = computed(() =>
  [...runs.value]
    .sort((a, b) => b.id - a.id)
    .map((run, index) => ({
      value: run.id,
      label: index === 0 ? `第 ${runs.value.length} 次运行（最新）` : `第 ${runs.value.length - index} 次运行 · ${formatDate(run.startedAt)}`,
    })),
);

const matrixColumns: TableColumnsType<EnvCheckMatrixRow> = [
  { title: '机器', dataIndex: 'host', key: 'host', width: 120 },
  { title: '模块', key: 'module', width: 130 },
  { title: '检查项', key: 'label', width: 180 },
  { title: '状态', key: 'state', width: 130 },
  { title: '详情 / 建议', key: 'detail' },
];

/** detailJson 解析 targets 得 host→模块 映射；解析失败回退「—」。 */
const moduleByHost = computed(() => {
  const map = new Map<string, string>();
  const json = detail.value?.run.detailJson;
  if (json) {
    try {
      const parsed = JSON.parse(json) as { targets?: Array<{ host: string; module: string }> };
      parsed.targets?.forEach((target) => map.set(target.host, target.module));
    } catch {
      /* detailJson 非法时模块列显示 — */
    }
  }
  return map;
});

const matrixRows = computed<EnvCheckMatrixRow[]>(() =>
  (detail.value?.rows ?? []).map((row) => ({
    ...row,
    rowKey: rowKeyOf(row),
    module: (row.host != null && moduleByHost.value.get(row.host)) || '—',
    label: labels.value.get(row.itemKey) ?? row.itemKey,
  })),
);

const issueRows = computed<EnvCheckIssueRow[]>(() =>
  matrixRows.value.filter(
    (row): row is EnvCheckIssueRow => row.state === 'WARNING' && row.fixable && row.host != null && row.risk != null,
  ),
);

function itemLabel(itemKey: string) {
  return labels.value.get(itemKey) ?? itemKey;
}

function badgeClass(row: EnvCheckMatrixRow) {
  if (row.state === 'WARNING') return `warn-${(row.risk ?? 'low').toLowerCase()}`;
  return row.state.toLowerCase();
}

function badgeText(row: EnvCheckMatrixRow) {
  if (row.state === 'OK') return '✓ 正常';
  if (row.state === 'FIXED') return '🔧 已修复';
  if (row.state === 'NA') return '不适用';
  const level = { LOW: '低', MEDIUM: '中', HIGH: '高' }[row.risk ?? 'LOW'];
  return `⚠ 需处理 · ${level}`;
}

/** 刷新历史列表；尚无选中 run 时回落最新一次（详情接口零请求时为置空）。 */
async function loadRuns() {
  const list = await fetchEnvCheckRunsApi(props.planId);
  runs.value = [...list].sort((a, b) => b.id - a.id);
  if (runId.value === null) {
    await selectRun(runs.value[0]?.id ?? null);
  }
}

async function loadDetail() {
  fixes.value = [];
  if (runId.value === null) {
    detail.value = null;
    return;
  }
  const [runDetail, fixList] = await Promise.all([
    fetchEnvCheckRunDetailApi(runId.value),
    fetchEnvCheckFixesApi(runId.value).catch(() => [] as EnvCheckFixRecord[]),
  ]);
  detail.value = runDetail;
  fixes.value = fixList;
}

async function reloadDetail() {
  loading.value = true;
  try {
    await loadDetail();
  } finally {
    loading.value = false;
  }
}

async function selectRun(id: number | null) {
  runId.value = id;
  await reloadDetail().catch(() => message.error('检查详情加载失败'));
}

async function loadAll() {
  try {
    if (!labels.value.size) {
      labels.value = new Map((await fetchEnvCheckItemsApi()).map((item) => [item.key, item.label]));
    }
    await loadRuns();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '检查历史加载失败');
  }
}

async function trigger() {
  triggering.value = true;
  try {
    const summaryResult = await triggerEnvCheckApi(props.planId);
    message.success('环境检查已完成');
    const list = await fetchEnvCheckRunsApi(props.planId);
    runs.value = [...list].sort((a, b) => b.id - a.id);
    await selectRun(summaryResult.id);
  } catch (error) {
    const err = error as Error & { code?: string; body?: { missingHosts?: string[] } };
    if (err.code === 'ENV_CREDENTIALS_MISSING' && err.body?.missingHosts?.length) {
      showMissingCredentials(err.body.missingHosts);
    } else {
      message.error(err.message || '运行检查失败');
    }
  } finally {
    triggering.value = false;
  }
}

function showMissingCredentials(hosts: string[]) {
  Modal.confirm({
    title: '以下机器未配置 SSH 凭据',
    icon: h('span', { class: 'ec-warn-icon danger', role: 'img', 'aria-label': '警告' }, '⃠'),
    content: () =>
      h('div', { class: 'ec-missing-hosts' }, [
        ...hosts.map((host) => h('div', { class: 'ec-missing-host-line' }, host)),
        h('p', { class: 'ec-missing-tip' }, '配置后才能开始环境检查'),
      ]),
    okText: '去凭据',
    cancelText: '取消',
    onOk: () => emit('goto-credentials'),
  });
}

async function applyFixes(requests: Array<{ host: string; itemKey: string }>) {
  if (runId.value === null) return;
  fixing.value = true;
  try {
    const outcome = await applyEnvCheckFixesApi(runId.value, requests);
    message.success(`已修复 ${outcome.fixed.length} · 跳过 ${outcome.skipped.length} · 失败 ${outcome.failed.length}`);
    await reloadDetail();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '批量修复失败');
  } finally {
    fixing.value = false;
  }
}

onMounted(loadAll);
</script>

<style scoped>
.ec-summary-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.ec-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12.5px;
  background: var(--surface-soft);
}
.ec-pill b {
  font-family: var(--font-data);
  font-size: 13px;
}
.ec-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.ec-dot.ok {
  background: var(--ok);
}
.ec-dot.warn {
  background: var(--warn);
}
.ec-dot.fixed {
  background: var(--accent);
}
.ec-when {
  color: var(--muted);
  font-size: 12px;
}
.ec-spacer {
  flex: 1;
}
.ec-history {
  min-width: 200px;
}
.ec-host {
  font-family: var(--font-data);
  font-size: 12px;
}
.ec-module {
  color: var(--muted);
  font-size: 11.5px;
}
.ec-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 6px;
  font-size: 11.5px;
  font-weight: 600;
  padding: 1px 8px;
  line-height: 20px;
}
.ec-badge.ok {
  color: var(--ok);
  background: var(--ok-soft);
}
.ec-badge.warn-low {
  color: var(--warn);
  background: var(--warning-soft);
}
.ec-badge.warn-medium {
  color: var(--orange);
  background: var(--orange-soft);
}
.ec-badge.warn-high {
  color: var(--danger);
  background: var(--danger-soft);
}
.ec-badge.fixed {
  color: var(--accent);
  background: var(--accent-soft);
}
.ec-badge.na {
  color: var(--muted);
  background: transparent;
  border: 1px dashed var(--line-strong);
}
.ec-detail {
  color: var(--muted);
  font-size: 11.5px;
}
</style>

<style>
/* Modal.confirm 内容挂在 body 级门户容器，scoped 选择器不可达，用全局类（前缀 ec- 防冲突）。
   红/橙分级对应原型：HIGH 勾选确认与缺凭据=红（danger），一键全选=橙（orange，默认色）。 */
.ec-warn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  font-size: 17px;
  font-weight: 700;
  color: var(--orange);
  background: var(--orange-soft);
}
.ec-warn-icon.danger {
  color: var(--danger);
  background: var(--danger-soft);
}
.ec-missing-hosts .ec-missing-host-line {
  font: 500 12px var(--font-data);
  background: var(--danger-soft);
  color: var(--danger);
  border-radius: 6px;
  padding: 3px 10px;
  margin-bottom: 4px;
}
.ec-missing-hosts .ec-missing-tip {
  color: var(--muted);
  font-size: 12.5px;
  margin: 6px 0 0;
}
</style>
