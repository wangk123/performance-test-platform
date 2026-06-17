<template>
  <aside class="step-sidebar">
    <header class="step-sidebar-header">
      <div>
        <span class="step-eyebrow">Script Steps</span>
        <h2>脚本步骤编排</h2>
        <p>拖拽调整顺序，点击节点编辑配置。</p>
      </div>
      <button class="step-add-root" type="button" @click="editor.openStepDialog('root')">
        <span class="plus">+</span>
        <span>新建线程组</span>
      </button>
    </header>

    <div class="step-legend">
      <span
        v-for="meta in legendMetas"
        :key="meta.type"
        class="step-legend-item"
        :class="`tone-${meta.tone}`"
      >
        <StepTypeIcon :type="meta.type" />
        <small>{{ meta.shortLabel }}</small>
      </span>
    </div>

    <div class="step-tree-scroll">
      <div class="step-tree">
      <div
        v-for="item in editor.flatEditorSteps.value"
        :key="item.step.id"
        class="step-node"
        :class="{
          active: editor.selectedEditorStepId.value === item.step.id,
          dragging: editor.draggingStepId.value === item.step.id,
          'drop-before':
            editor.dragOverStepId.value === item.step.id && editor.dragOverMode.value === 'before',
          'drop-after':
            editor.dragOverStepId.value === item.step.id && editor.dragOverMode.value === 'after',
          'drop-child':
            editor.dragOverStepId.value === item.step.id && editor.dragOverMode.value === 'child',
          [`tone-${toneOf(item.step.type)}`]: true,
        }"
        :style="{ '--indent': `${item.level * 18}px` }"
        role="button"
        tabindex="0"
        draggable="true"
        @click="editor.selectEditorStep(item.step.id)"
        @keydown.enter="editor.selectEditorStep(item.step.id)"
        @dragstart="editor.startStepDrag(item.step.id, $event)"
        @dragover.prevent="editor.updateStepDrop(item, $event)"
        @dragleave="editor.clearStepDrop"
        @drop.prevent="editor.dropStepOn(item, $event)"
        @dragend="editor.endStepDrag"
      >
        <div class="rail">
          <span v-for="n in item.level" :key="n" class="rail-line" />
          <button
            v-if="item.step.children.length"
            class="rail-toggle"
            type="button"
            :aria-label="editor.isStepCollapsed(item.step.id) ? '展开' : '折叠'"
            @click.stop="editor.toggleStepCollapsed(item.step.id)"
          >
            <svg viewBox="0 0 10 10" aria-hidden="true">
              <path
                :d="editor.isStepCollapsed(item.step.id) ? 'M3 2l4 3-4 3z' : 'M2 3l3 4 3-4z'"
                fill="currentColor"
              />
            </svg>
          </button>
          <span v-else class="rail-dot" />
        </div>

        <StepTypeIcon :type="item.step.type" class="node-icon" />

        <div class="node-text">
          <strong>{{ item.step.name }}</strong>
          <span v-if="item.step.type === 'THREAD_GROUP'" class="node-meta">
            <ThreadGroupSummary :thread-group="toThreadGroup(item.step)" />
          </span>
          <span v-else class="node-meta">{{ describe(item.step) }}</span>
        </div>

        <button
          v-if="editor.canAddChildStepTo(item.step.id)"
          class="node-add"
          type="button"
          title="新增子级步骤"
          @click.stop="editor.openStepDialog('child', item.step.id)"
        >
          <svg viewBox="0 0 12 12" aria-hidden="true">
            <path d="M6 2v8M2 6h8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div v-if="editor.flatEditorSteps.value.length === 0" class="step-empty">
        <div class="step-empty-glyph">∅</div>
        <strong>暂无步骤</strong>
        <span>从新建线程组开始组织脚本编排。</span>
      </div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import type { ScriptStep, ScriptStepType, ThreadGroup } from '../../types';
import { stepTypeMeta } from '../../constants';
import { useScriptEditor } from '../../composables/useScriptEditor';
import StepTypeIcon from '../scripts/StepTypeIcon.vue';
import ThreadGroupSummary from './ThreadGroupSummary.vue';

const editor = useScriptEditor();

const legendMetas = (Object.keys(stepTypeMeta) as ScriptStepType[]).map((type) => ({
  type,
  tone: stepTypeMeta[type].tone,
  shortLabel: stepTypeMeta[type].shortLabel,
}));

function toneOf(type: ScriptStepType) {
  return stepTypeMeta[type].tone;
}

function toThreadGroup(step: ScriptStep): ThreadGroup {
  return {
    name: step.name,
    threads: Number(step.config.threads ?? 1),
    rampUp: Number(step.config.rampUp ?? 0),
    loops: Number(step.config.loops ?? 1),
    duration: Number(step.config.duration ?? 0),
    scheduler: Boolean(step.config.scheduler ?? false),
    mode: step.config.mode === 'stepping' || step.config.mode === 'duration'
      ? step.config.mode
      : (step.config.scheduler ? 'duration' : 'count'),
    stepping: typeof step.config.stepping === 'object'
      ? step.config.stepping as ThreadGroup['stepping']
      : undefined,
  };
}

function describe(step: ScriptStep) {
  const config = step.config;
  switch (step.type) {
    case 'THREAD_GROUP':
      return `${config.threads ?? '-'} 线程 · Ramp ${config.rampUp ?? '-'}s · ${config.duration ?? '-'}s`;
    case 'HTTP_REQUEST':
      return `${config.method ?? 'GET'} ${config.path ?? ''}`;
    case 'ASSERTION':
      return `${assertionTargetText(String(config.target ?? 'body'))} · ${assertionMatchText(String(config.match ?? 'contains'))}`;
    case 'CSV_DATA':
      return `${config.fileName ?? '-'}`;
    case 'USER_PARAMS': {
      const text = String(config.paramsText ?? '');
      const first = text.split('\n')[0] ?? '';
      return first || '未配置参数';
    }
    case 'HEADER_CONFIG': {
      const text = String(config.headersText ?? '');
      const first = text.split('\n')[0] ?? '';
      return first || '未配置 Header';
    }
  }
  return '';
}

function assertionTargetText(value: string) {
  return value === 'statusCode' ? '响应码' : value === 'headers' ? '响应头' : '响应体';
}

function assertionMatchText(value: string) {
  return value === 'equals' ? '等于' : value === 'regex' ? '正则' : '包含';
}
</script>
