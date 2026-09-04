<template>
  <div class="scenario-module">
    <div class="scenario-module-head">
      <span>场景 = 文档章节（业务内容）+ 执行配置实体；脚本不进文档，评审通过后关联。</span>
      <a-button v-if="canEditScenario" type="primary" size="small" @click="emit('request-add')">添加场景</a-button>
    </div>

    <div v-for="block in blocks" :key="block.heading" class="scenario-card">
      <div class="scenario-card-head">
        <strong>{{ block.heading }}</strong>
        <span v-if="latestRecord(block.name)" class="scenario-latest" :title="latestRecord(block.name)">
          最新执行：{{ latestRecord(block.name) }}
        </span>
        <span v-else class="scenario-latest none">未执行</span>
      </div>
      <p class="scenario-purpose">目的：{{ block.purpose || '（待填写）' }}</p>
      <div class="scenario-actions">
        <a-button size="small" @click="requestEdit(block.name)" :disabled="!scenarioOf(block.name)">编辑</a-button>
        <a-button
          v-if="!scriptBound(block.name) && canBindScript"
          size="small" type="primary"
          @click="bindScript(block.name)"
        >关联脚本</a-button>
        <a-button
          v-if="scriptBound(block.name) && canExecute"
          size="small" type="primary"
          @click="run(block.name)"
        >执行</a-button>
      </div>
      <details v-if="block.records.length" class="scenario-records">
        <summary>执行记录（{{ block.records.length }}）</summary>
        <ul><li v-for="(record, index) in block.records" :key="index">{{ record }}</li></ul>
      </details>
    </div>
    <p v-if="blocks.length === 0" class="scenario-empty">暂无场景。在评审前添加，或在评审通过后编写脚本并关联。</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { message } from 'ant-design-vue';
import { useRouter } from 'vue-router';
import type { TaskPlan, TaskScenario } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import { parseScenarioBlocks } from '../../utils/plan-markdown';
import { bindScenarioScriptApi, precheckSkipApi } from '../../api/plan-doc';
import { triggerExecutionApi } from '../../api/task-plans';

const props = defineProps<{ docPlan: ReturnType<typeof usePlanDoc>; plan: TaskPlan; scenarios: TaskScenario[] }>();
const emit = defineEmits<{ (e: 'changed'): void; (e: 'request-add'): void; (e: 'request-edit', scenario: TaskScenario): void }>();

const router = useRouter();
const blocks = computed(() => parseScenarioBlocks(props.plan.body));

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

function latestRecord(name: string) {
  // 执行记录按时间正序追加，最新一条在末尾。
  const records = parseScenarioBlocks(props.plan.body).find((b) => b.name === name)?.records ?? [];
  return records.at(-1) ?? '';
}

async function bindScript(name: string) {
  const scenario = scenarioOf(name);
  if (!scenario) return;
  const input = window.prompt(`关联脚本版本 ID（场景：${name}）`);
  const scriptVersionId = Number(input);
  if (!input || Number.isNaN(scriptVersionId) || scriptVersionId <= 0) return;
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
      if (window.confirm(`${text}\n\n是否跳过环境检查继续执行？（将记录系统批注）`)) {
        await precheckSkipApi(props.plan.id);
        await run(name);
      }
      return;
    }
    message.error(text || '执行失败');
  }
}
</script>

<style scoped>
.scenario-module-head { display: flex; justify-content: space-between; align-items: center; color: var(--muted); margin-bottom: 8px; }
.scenario-card { border: 1px solid var(--border); border-radius: 8px; padding: 12px; margin-bottom: 8px; }
.scenario-card-head { display: flex; gap: 12px; align-items: baseline; }
.scenario-latest { font-size: 12px; color: var(--muted); }
.scenario-latest.none { color: var(--muted); opacity: 0.7; }
.scenario-purpose { margin: 6px 0; color: var(--muted); }
.scenario-actions { display: flex; gap: 8px; margin: 6px 0; }
.scenario-records summary { cursor: pointer; font-size: 13px; }
.scenario-empty { color: var(--muted); }
</style>
