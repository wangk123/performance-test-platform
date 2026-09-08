<template>
  <section class="panel report-tab">
    <div class="report-actions">
      <a-button v-if="can('TO_REPORT')" type="primary" @click="doc.transition('to-report', undefined, '已进入报告阶段')">进入报告</a-button>
      <a-button v-if="can('GENERATE_REPORT')" type="primary" :loading="generating" @click="generate">生成报告</a-button>
      <span class="report-hint">生成 = 聚合执行摘要回填"十一、结论"（达成表实际列 + 结果总览）。</span>
    </div>

    <h3>验收判定</h3>
    <a-alert
      v-if="verdict && !verdict.present"
      type="info"
      show-icon
      message="本计划无验收指标（摸底/排查型），总体结论由人工填写。"
    />
    <a-alert
      v-else-if="verdict && !verdict.available"
      type="warning"
      show-icon
      message="报告未生成，判定不可读。"
    />
    <template v-else-if="verdict">
      <div class="verdict-head">
        <a-tag :color="overallMeta[verdict.overall]?.color">{{ overallMeta[verdict.overall]?.text }}</a-tag>
        <span v-if="verdict.prefillConclusion" class="verdict-prefill">{{ verdict.prefillConclusion }}</span>
      </div>
      <a-table
        v-if="missedRows.length"
        :columns="verdictColumns"
        :data-source="missedRows"
        :pagination="false"
        size="small"
        row-key="objectName"
      >
        <template #bodyCell="{ column, record }">
          <a v-if="column.key === 'drill' && record.executionId" @click="drilldown(record)">查看执行</a>
        </template>
      </a-table>
    </template>

    <h3>结论章节预览</h3>
    <MdPreview :model-value="conclusion ?? '（暂无结论章节）'" language="zh-CN" />

    <h3>场景执行概览</h3>
    <a-table
      :columns="columns"
      :data-source="rows"
      :pagination="false"
      row-key="name"
      size="small"
      :locale="{ emptyText: '暂无场景' }"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { PlanVerdict, PlanVerdictRow, TaskScenario } from '../../types';
import { getPlanVerdictApi } from '../../api/plan-doc';
import { extractSection, parseExecutionRecords } from '../../utils/plan-markdown';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc>; scenarios: TaskScenario[] }>();

const router = useRouter();
const generating = ref(false);
const verdict = ref<PlanVerdict | null>(null);

async function loadVerdict() {
  const planId = props.doc.plan.value?.id;
  if (planId) verdict.value = await getPlanVerdictApi(planId).catch(() => null);
}

onMounted(() => {
  void loadVerdict();
});

const overallMeta = computed(() => ({
  PASSED: { text: '达成', color: 'green' },
  FAILED: { text: '未达成', color: 'red' },
  INDETERMINATE: { text: '无法判定', color: 'orange' },
  NONE: { text: '—', color: 'default' },
} as Record<string, { text: string; color: string }>));

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
  { title: '脚本', dataIndex: 'script', key: 'script' },
  { title: '最新执行（回填块解析）', dataIndex: 'latest', key: 'latest' },
];

const verdictColumns = [
  { title: '对象', dataIndex: 'objectName', key: 'objectName' },
  { title: '指标', dataIndex: 'metricRaw', key: 'metricRaw' },
  { title: '目标', dataIndex: 'targetRaw', key: 'targetRaw' },
  { title: '实际', dataIndex: 'actualValue', key: 'actualValue' },
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
      script: scenario.scriptVersionId ? `#${scenario.scriptVersionId}` : '未关联',
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
.verdict-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.verdict-prefill { color: var(--muted); }
</style>
