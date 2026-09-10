<template>
  <section class="plan-document">
    <div class="doc-body">
      <nav class="doc-toc" aria-label="章节导航">
        <h4>章节导航</h4>
        <a
          v-for="section in sections"
          :key="section.title"
          class="toc-item"
          :class="{ current: currentSection === section.title }"
          href="#"
          :aria-current="currentSection === section.title ? 'true' : undefined"
          @click.prevent="jumpTo(section.title)"
        >
          <span class="toc-label">{{ section.title }}</span>
        </a>
      </nav>

      <div ref="docMainRef" class="doc-main" tabindex="0" role="region" aria-label="计划文档内容" @scroll="onDocScroll">
        <template v-if="viewMode === 'Pretty'">
          <div id="doc-panel-Pretty" class="doc-flow">
            <section
              v-for="section in sections"
              :key="section.title"
              class="doc-section"
              :data-section="section.title"
            >
              <header class="doc-section-head">
                <h3>{{ section.title }}</h3>
                <a-button
                  v-if="canEdit"
                  class="doc-section-edit"
                  size="small"
                  type="text"
                  title="编辑章节"
                  @click="openSectionEditor(section.title)"
                >
                  <template #icon>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z" /></svg>
                  </template>
                  编辑
                </a-button>
              </header>
              <ChecklistView
                v-if="section.title === '六、测试约束'"
                :content="section.content"
                :editable="canEdit"
                @toggle="toggleChecklist(section.content, $event)"
              />
              <ScenarioDesignModule
                v-else-if="section.title === '八、场景设计'"
                :doc-plan="doc"
                :plan="plan"
                :scenarios="scenarios"
                @changed="emit('changed')"
                @request-add="emit('request-add')"
                @request-edit="(scenario) => emit('request-edit', scenario)"
              />
              <MdPreview
                v-else
                class="plan-md"
                :model-value="section.content || '（本章暂无内容）'"
                :theme="mdTheme"
                :md-heading-id="headingId"
                language="zh-CN"
              />
            </section>
          </div>
        </template>

        <template v-else>
          <div id="doc-panel-Markdown" class="doc-md-panel">
            <div v-if="canEdit" class="md-edit-actions">
              <a-button v-if="!editing" size="small" type="primary" @click="beginEdit">编辑全文</a-button>
              <template v-else>
                <a-button size="small" @click="cancelEdit">取消</a-button>
                <a-button size="small" type="primary" :disabled="!dirty" @click="saveEdit">保存全文</a-button>
              </template>
            </div>
            <MdPreview
              v-if="!editing"
              class="plan-md"
              :model-value="plan.body ?? ''"
              :theme="mdTheme"
              :md-heading-id="headingId"
              language="zh-CN"
            />
            <MdEditor
              v-else
              v-model="editDraft"
              class="plan-md plan-md-editor"
              :theme="mdTheme"
              :style="{ flex: '1', minHeight: '420px' }"
              language="zh-CN"
            />
          </div>
        </template>
      </div>
    </div>

    <PlanSectionEditor
      v-model:open="sectionEditorOpen"
      :title="editingSectionTitle"
      :content="editingSectionContent"
      @save="saveSection"
    />
    <MetricsEditorModal
      v-model:open="metricsEditorOpen"
      :content="editingSectionContent"
      @save="saveSection"
    />
    <PlanConflictDialog
      v-model:open="conflictOpen"
      :server-markdown="plan.body ?? ''"
      :local-markdown="conflictLocal"
      :server-revision="plan.revision"
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
import { computed, nextTick, ref, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { MdEditor, MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { TaskPlan, TaskScenario } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import { useTheme } from '../../composables/useTheme';
import { extractSection, replaceSection, splitSections, toggleChecklistItem } from '../../utils/plan-markdown';
import { updatePrecheckSettingsApi } from '../../api/plan-doc';
import PlanConflictDialog from './PlanConflictDialog.vue';
import PlanSectionEditor from './PlanSectionEditor.vue';
import MetricsEditorModal from './MetricsEditorModal.vue';
import ChecklistView from './ChecklistView.vue';
import ScenarioDesignModule from './ScenarioDesignModule.vue';

const props = defineProps<{
  doc: ReturnType<typeof usePlanDoc>;
  plan: TaskPlan;
  scenarios: TaskScenario[];
  /** 视图状态由父级 Tabs 行工具条持有（v-model:view-mode）。 */
  viewMode: 'Pretty' | 'Markdown';
}>();
const emit = defineEmits<{
  (e: 'changed'): void;
  (e: 'request-add'): void;
  (e: 'request-edit', scenario: TaskScenario): void;
  (e: 'update:viewMode', value: 'Pretty' | 'Markdown'): void;
}>();

const { themeMode } = useTheme();
const mdTheme = computed(() => (themeMode.value === 'dark' ? 'dark' : 'light'));

/** 可写计算属性：读写皆透传父级，组件内既有 viewMode.value 读写零改动。 */
const viewMode = computed({
  get: () => props.viewMode,
  set: (value) => emit('update:viewMode', value),
});
const editing = ref(false);
const editDraft = ref('');
const conflictLocal = ref('');
const conflictOpen = ref(false);
const sectionEditorOpen = ref(false);
const metricsEditorOpen = ref(false);
const editingSectionTitle = ref('');
const editingSectionContent = ref('');
const precheckDrawerOpen = ref(false);
const precheck = ref<{ enabled: boolean; items: string[] }>({ enabled: false, items: [] });
const precheckItemsText = ref('');

const sections = computed(() => splitSections(props.plan.body));
const canEdit = computed(() => Boolean(props.doc.permissions.value.EDIT));
const canPrecheck = computed(() => Boolean(props.doc.permissions.value.PRECHECK_RUN));
const docMainRef = ref<HTMLElement | null>(null);
const currentSection = ref('');
const dirty = computed(() => editing.value && editDraft.value !== (props.plan.body ?? ''));

/** md-editor-v3 h2 锚点：encodeURIComponent 保证中文标题可直接 getElementById。 */
const ANCHOR_PREFIX = 'plan-mdh-';
const headingId = (options: { text: string }) => ANCHOR_PREFIX + encodeURIComponent(options.text.trim());
function anchorDomId(title: string) {
  return ANCHOR_PREFIX + encodeURIComponent(title.trim());
}

watch(() => props.plan.precheckJson, parsePrecheck, { immediate: true });

watch([viewMode, () => props.plan.body, editing], () => {
  void nextTick(updateCurrentSection);
}, { immediate: true });

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

function beginEdit() {
  editDraft.value = props.plan.body ?? '';
  editing.value = true;
}

function cancelEdit() {
  if (dirty.value) {
    Modal.confirm({
      title: '放弃未保存的修改？',
      content: '本地草稿尚未保存，取消后修改将丢失。',
      okText: '放弃修改',
      okType: 'danger',
      cancelText: '继续编辑',
      onOk: () => {
        editing.value = false;
        editDraft.value = '';
      },
    });
    return;
  }
  editing.value = false;
  editDraft.value = '';
}

function saveEdit() {
  void submitWholeDocument(editDraft.value);
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
  // 指标章走结构化表单（固定字段、行增删），其余章节维持 Markdown 编辑
  if (title === '三、测试指标') {
    metricsEditorOpen.value = true;
    return;
  }
  sectionEditorOpen.value = true;
}

/** 执行设置抽屉由父级 Tabs 行工具条触发（按钮已上移，抽屉仍属文档组件）。 */
function openPrecheck() {
  precheckDrawerOpen.value = true;
}

defineExpose({ openPrecheck });

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
  const body = replaceSection(props.plan.body ?? '', '六、测试约束', next);
  await submitWholeDocument(body);
}

/* ---------- TOC 跳转与 scrollspy（相对 .doc-main 唯一滚动容器定位） ---------- */

function jumpTo(title: string) {
  const main = docMainRef.value;
  if (!main) return;
  let el: HTMLElement | null = null;
  if (viewMode.value === 'Pretty') {
    el = main.querySelector(`[data-section="${title}"]`);
  } else if (!editing.value) {
    el = document.getElementById(anchorDomId(title));
  }
  if (!el || !main.contains(el)) return;
  main.scrollTo({
    top: main.scrollTop + el.getBoundingClientRect().top - main.getBoundingClientRect().top - 8,
    behavior: 'smooth',
  });
  currentSection.value = title;
}

let scrollRaf: number | null = null;
function onDocScroll() {
  if (scrollRaf !== null) return;
  scrollRaf = window.requestAnimationFrame(() => {
    scrollRaf = null;
    updateCurrentSection();
  });
}

function anchorElements(): { el: HTMLElement; title: string }[] {
  const main = docMainRef.value;
  if (!main) return [];
  if (viewMode.value === 'Pretty') {
    return [...main.querySelectorAll<HTMLElement>('[data-section]')].map((el) => ({
      el,
      title: el.dataset.section ?? '',
    }));
  }
  if (!editing.value) {
    return [...main.querySelectorAll<HTMLElement>('.md-editor-preview h2')]
      .filter((el) => el.id.startsWith(ANCHOR_PREFIX))
      .map((el) => ({ el, title: decodeURIComponent(el.id.slice(ANCHOR_PREFIX.length)) }));
  }
  return [];
}

function updateCurrentSection() {
  const main = docMainRef.value;
  if (!main) return;
  const anchors = anchorElements();
  if (!anchors.length) return;
  const mainTop = main.getBoundingClientRect().top;
  let current = anchors[0].title;
  for (const anchor of anchors) {
    if (anchor.el.getBoundingClientRect().top - mainTop <= 96) current = anchor.title;
    else break;
  }
  currentSection.value = current;
}
</script>

<style scoped>
/* 骨架与滚动布局见全局 plan-module.css；组件内仅留结构钩子 */
</style>
