<template>
  <section class="plan-document">
    <div class="doc-toolbar">
      <div class="segmented" role="tablist" aria-label="文档视图切换">
        <button
          v-for="mode in (['Pretty', 'Markdown'] as const)"
          :key="mode"
          type="button"
          role="tab"
          class="segmented-item"
          :class="{ active: viewMode === mode }"
          :aria-selected="viewMode === mode"
          @click="viewMode = mode"
        >{{ mode }}</button>
      </div>
      <div class="doc-toolbar-right">
        <span class="doc-rev">revision {{ plan.revision }}</span>
        <a-button v-if="viewMode === 'Markdown' && canEdit && !editing" size="small" type="primary" @click="beginEdit">编辑</a-button>
        <template v-if="viewMode === 'Markdown' && editing">
          <a-button size="small" @click="cancelEdit">取消</a-button>
          <a-button size="small" type="primary" :disabled="!dirty" @click="saveEdit">保存</a-button>
        </template>
        <a-button size="small" @click="precheckDrawerOpen = true">执行设置（环境检查）</a-button>
      </div>
    </div>

    <div class="doc-body">
      <nav class="doc-toc" aria-label="章节导航">
        <h4>章节导航</h4>
        <a
          v-for="section in sections"
          :key="section.title"
          class="toc-item"
          :class="{ current: currentSection === section.title }"
          :aria-current="currentSection === section.title ? 'true' : undefined"
          @click="jumpTo(section.title)"
        >
          {{ section.title }}
          <span class="toc-tag">{{ isConstrained(section.title) ? '受控' : '叙述' }}</span>
        </a>
      </nav>

      <div ref="docMainRef" class="doc-main" @scroll="onDocScroll">
        <template v-if="viewMode === 'Pretty'">
          <div class="doc-title-block">
            <h1 class="doc-title">{{ plan.name }}</h1>
            <div class="doc-title-meta">
              <span>负责人 <b>{{ plan.createdBy }}</b></span>
              <span>文档 <b class="mono">revision {{ plan.revision }}</b></span>
              <span>场景 <b>{{ scenarios.length }}</b></span>
              <span>更新于 <b>{{ formatDate(plan.updatedAt) }}</b></span>
            </div>
          </div>
          <section
            v-for="section in sections"
            :key="section.title"
            class="doc-section"
            :data-section="section.title"
          >
            <header class="doc-section-head">
              <h2>{{ section.title }}</h2>
              <span v-if="isConstrained(section.title)" class="doc-chip">受控</span>
              <a-button
                v-if="canEdit"
                class="doc-section-edit"
                size="small"
                type="text"
                @click="openSectionEditor(section.title)"
              >编辑章节</a-button>
            </header>
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
        </template>

        <template v-else>
          <div v-if="!editing" class="doc-section doc-md-wrap">
            <MdPreview
              class="plan-md"
              :model-value="plan.body ?? ''"
              :theme="mdTheme"
              :md-heading-id="headingId"
              language="zh-CN"
            />
          </div>
          <MdEditor
            v-else
            v-model="editDraft"
            class="plan-md plan-md-editor"
            :theme="mdTheme"
            :style="{ height: '100%', minHeight: '420px' }"
            language="zh-CN"
          />
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
import { computed, nextTick, ref, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { MdEditor, MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { TaskPlan, TaskScenario } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import { useTheme } from '../../composables/useTheme';
import { formatDate } from '../../utils/format';
import { extractSection, replaceSection, splitSections, toggleChecklistItem } from '../../utils/plan-markdown';
import { updatePrecheckSettingsApi } from '../../api/plan-doc';
import PlanConflictDialog from './PlanConflictDialog.vue';
import PlanSectionEditor from './PlanSectionEditor.vue';
import ChecklistView from './ChecklistView.vue';
import ScenarioDesignModule from './ScenarioDesignModule.vue';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc>; plan: TaskPlan; scenarios: TaskScenario[] }>();
const emit = defineEmits<{
  (e: 'changed'): void;
  (e: 'request-add'): void;
  (e: 'request-edit', scenario: TaskScenario): void;
}>();

const CONSTRAINED = ['二、测试目的与指标', '三、测试范围', '四、测试资源', '五、测试约束', '七、场景设计', '九、排期与协作'];

const { themeMode } = useTheme();
const mdTheme = computed(() => (themeMode.value === 'dark' ? 'dark' : 'light'));

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

function isConstrained(title: string) {
  return CONSTRAINED.includes(title);
}

watch(() => props.plan.precheckJson, parsePrecheck, { immediate: true });

watch([viewMode, () => props.plan.body, editing], () => {
  void nextTick(updateCurrentSection);
});

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
