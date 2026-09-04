<template>
  <section class="panel report-tab">
    <div class="report-actions">
      <a-button v-if="can('TO_REPORT')" type="primary" @click="doc.transition('to-report', undefined, '已进入报告阶段')">进入报告</a-button>
      <a-button v-if="can('GENERATE_REPORT')" type="primary" :loading="generating" @click="generate">生成报告</a-button>
      <span class="report-hint">生成 = 聚合执行摘要回填"十一、结论"（达成表实际列 + 结果总览）。</span>
    </div>

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
import { computed, ref } from 'vue';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { TaskScenario } from '../../types';
import { extractSection, parseExecutionRecords } from '../../utils/plan-markdown';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc>; scenarios: TaskScenario[] }>();

const generating = ref(false);
const columns = [
  { title: '场景', dataIndex: 'name', key: 'name' },
  { title: '测试类型', dataIndex: 'testType', key: 'testType' },
  { title: '脚本', dataIndex: 'script', key: 'script' },
  { title: '最新执行（回填块解析）', dataIndex: 'latest', key: 'latest' },
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
    await props.doc.transition('generate-report', undefined, '报告已生成');
  } finally {
    generating.value = false;
  }
}
</script>
