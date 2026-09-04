<template>
  <section class="plan-document">
    <div class="doc-toolbar">
      <a-segmented v-model:value="viewMode" :options="['Pretty', 'Markdown']" />
      <div class="doc-toolbar-right">
        <a-button v-if="viewMode === 'Markdown' && canEdit" type="primary" @click="startEdit">
          {{ editing ? '保存' : '编辑' }}
        </a-button>
        <a-button v-if="viewMode === 'Markdown' && editing" @click="cancelEdit">取消</a-button>
        <a-button @click="precheckDrawerOpen = true">执行设置（环境检查）</a-button>
      </div>
    </div>

    <div class="doc-body">
      <aside class="doc-toc">
        <h4>章节导航</h4>
        <a
          v-for="section in sections"
          :key="section.title"
          class="toc-item"
          :class="{ constrained: CONSTRAINED.includes(section.title) }"
          @click="scrollTo(section.line)"
        >{{ section.title }}</a>
      </aside>

      <div class="doc-main">
        <template v-if="viewMode === 'Pretty'">
          <div v-for="section in prettySections" :key="section.title" class="panel pretty-section" :data-section="section.title">
            <div class="pretty-section-head">
              <h3>{{ section.title }}</h3>
              <a-button v-if="canEdit" size="small" @click="openSectionEditor(section.title)">编辑章节</a-button>
            </div>
            <ChecklistView
              v-if="section.title === '五、测试约束'"
              :content="section.content"
              :editable="canEdit"
              @toggle="toggleChecklist(section.content, $event)"
            />
            <ScenarioDesignModule
              v-else-if="section.title === '七、场景设计'"
              :doc-plan="doc"
              :plan="plan"
              :scenarios="scenarios"
              @changed="emit('changed')"
            />
            <MdPreview v-else :model-value="section.content || '（空）'" language="zh-CN" />
          </div>
          <p class="pretty-hint">叙述章节（背景/策略/风险/附录/结论）请切换到 Markdown 视图查看。</p>
        </template>

        <template v-else>
          <MdPreview v-if="!editing" :model-value="plan.body ?? ''" language="zh-CN" />
          <MdEditor v-else v-model="editDraft" :style="{ height: '560px' }" language="zh-CN" />
        </template>
      </div>
    </div>

    <PlanSectionEditor
      v-model:open="sectionEditorOpen"
      :title="editingSectionTitle"
      :content="editingSectionContent"
      @save="saveSection"
    />
    <PlanConflictDialog
      v-model:open="conflictOpen"
      :server-markdown="plan.body ?? ''"
      :local-markdown="conflictLocal"
      @resolve="resolveConflict"
    />

    <a-drawer v-model:open="precheckDrawerOpen" title="执行设置（环境检查）" width="420">
      <p class="drawer-hint">环境检查是测试前的执行动作，不进文档、不参与评审。</p>
      <a-form layout="vertical">
        <a-form-item label="首执行前自动运行环境检查">
          <a-switch v-model:checked="precheck.enabled" :disabled="!canPrecheck" @change="savePrecheck" />
        </a-form-item>
        <a-form-item label="检测清单（每行一项；自动项：指标已定义/场景已配置/脚本已关联）">
          <a-textarea v-model:value="precheckItemsText" :rows="8" :disabled="!canPrecheck" @blur="savePrecheck" />
        </a-form-item>
        <a-form-item v-if="plan.precheckExecutedAt" label="首次运行时间">
          <span>{{ new Date(plan.precheckExecutedAt).toLocaleString() }}</span>
        </a-form-item>
      </a-form>
    </a-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { MdEditor, MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { TaskPlan, TaskScenario } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import { extractSection, replaceSection, splitSections, toggleChecklistItem } from '../../utils/plan-markdown';
import { updatePrecheckSettingsApi } from '../../api/plan-doc';
import PlanConflictDialog from './PlanConflictDialog.vue';
import PlanSectionEditor from './PlanSectionEditor.vue';
import ChecklistView from './ChecklistView.vue';
import ScenarioDesignModule from './ScenarioDesignModule.vue';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc>; plan: TaskPlan; scenarios: TaskScenario[] }>();
const emit = defineEmits<{ (e: 'changed'): void }>();

const CONSTRAINED = ['二、测试目的与指标', '三、测试范围', '四、测试资源', '五、测试约束', '七、场景设计', '九、排期与协作'];

const viewMode = ref<'Pretty' | 'Markdown'>('Pretty');
const editing = ref(false);
const editDraft = ref('');
const conflictLocal = ref('');
const conflictOpen = ref(false);
const sectionEditorOpen = ref(false);
const editingSectionTitle = ref('');
const editingSectionContent = ref('');
const precheckDrawerOpen = ref(false);
const precheck = ref<{ enabled: boolean; items: string[] }>({ enabled: false, items: [] });
const precheckItemsText = ref('');

const sections = computed(() => splitSections(props.plan.body));
const prettySections = computed(() => sections.value.filter((s) => CONSTRAINED.includes(s.title)));
const canEdit = computed(() => Boolean(props.doc.permissions.value.EDIT));
const canPrecheck = computed(() => Boolean(props.doc.permissions.value.PRECHECK_RUN));

watch(() => props.plan.precheckJson, parsePrecheck, { immediate: true });

function parsePrecheck() {
  try {
    const parsed = props.plan.precheckJson ? JSON.parse(props.plan.precheckJson) : { enabled: false, items: [] };
    precheck.value = { enabled: Boolean(parsed.enabled), items: parsed.items ?? [] };
    precheckItemsText.value = precheck.value.items.join('\n');
  } catch {
    precheck.value = { enabled: false, items: [] };
  }
}

async function savePrecheck() {
  const items = precheckItemsText.value.split('\n').map((line) => line.trim()).filter(Boolean);
  precheck.value.items = items;
  try {
    await updatePrecheckSettingsApi(props.plan.id, { enabled: precheck.value.enabled, items });
    message.success('执行设置已保存（不影响文档 revision）');
    emit('changed');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败');
  }
}

function startEdit() {
  if (editing.value) {
    void submitWholeDocument(editDraft.value);
  } else {
    editDraft.value = props.plan.body ?? '';
    editing.value = true;
  }
}

function cancelEdit() {
  editing.value = false;
  editDraft.value = '';
}

async function submitWholeDocument(markdown: string): Promise<void> {
  const outcome = await props.doc.saveDocument(markdown);
  if (outcome === 'ok') {
    editing.value = false;
    emit('changed');
  } else if (outcome === 'conflict') {
    conflictLocal.value = markdown;
    conflictOpen.value = true;
  }
}

async function resolveConflict(kind: 'keep-server' | 'take-local' | 'manual') {
  conflictOpen.value = false;
  if (kind === 'keep-server') {
    editing.value = false;
    message.info('已保留平台版本');
  } else if (kind === 'take-local') {
    await props.doc.saveDocument(conflictLocal.value); // doc.plan 已刷新，baseRevision 为新值
    editing.value = false;
    emit('changed');
  } else {
    editDraft.value = props.plan.body ?? ''; // 以服务器版为基底手改
    editing.value = true;
    viewMode.value = 'Markdown';
  }
}

function openSectionEditor(title: string) {
  editingSectionTitle.value = title;
  editingSectionContent.value = extractSection(props.plan.body, title) ?? '';
  sectionEditorOpen.value = true;
}

async function saveSection(content: string) {
  const body = props.plan.body ?? '';
  try {
    const next = replaceSection(body, editingSectionTitle.value, content);
    await submitWholeDocument(next);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '章节写回失败');
  }
}

async function toggleChecklist(content: string, index: number) {
  const next = toggleChecklistItem(content, index);
  const body = replaceSection(props.plan.body ?? '', '五、测试约束', next);
  await submitWholeDocument(body);
}

function scrollTo(line: number) {
  const all = splitSections(props.plan.body);
  const target = all.find((s) => s.line === line);
  if (!target) return;
  const el = viewMode.value === 'Pretty'
    ? document.querySelector(`[data-section="${target.title}"]`)
    : null;
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
}
</script>

<style scoped>
.plan-document { display: flex; flex-direction: column; gap: 12px; }
.doc-toolbar { display: flex; justify-content: space-between; align-items: center; }
.doc-body { display: grid; grid-template-columns: 180px 1fr; gap: 16px; }
.doc-toc { border-right: 1px solid var(--border); padding-right: 8px; }
.doc-toc h4 { margin: 4px 0 8px; font-size: 12px; color: var(--muted); }
.toc-item { display: block; padding: 4px 6px; font-size: 13px; color: var(--muted); cursor: pointer; border-radius: 4px; text-decoration: none; }
.toc-item:hover { background: var(--canvas, #f4f6f8); }
.toc-item.constrained { color: var(--ink, inherit); font-weight: 500; }
.pretty-section { margin-bottom: 12px; }
.pretty-section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.pretty-hint { color: var(--muted); font-size: 12px; }
</style>
