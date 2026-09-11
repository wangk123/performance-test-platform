<template>
  <section class="plan-document">
    <div class="doc-shell" :class="{ 'with-panel': doc.panelEffective.value }">
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

        <div
          ref="docMainRef"
          class="doc-main"
          tabindex="0"
          role="region"
          aria-label="计划文档内容"
          @scroll="onDocScroll"
          @mouseover="commentLayer.onHover"
          @mouseleave="commentLayer.hideButton"
        >
          <template v-if="viewMode === 'Pretty'">
            <div id="doc-panel-Pretty" class="doc-flow">
              <section
                v-for="section in sections"
                :key="section.title"
                class="doc-section"
                :data-section="section.title"
              >
                <header class="doc-section-head">
                  <h3 :data-line="section.title === '八、场景设计' ? section.line : undefined">{{ section.title }}</h3>
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
                <div
                  v-for="group in commentLayer.brokenGroups.value.filter((g) => g.sectionTitle === section.title)"
                  :key="`broken-${section.title}`"
                  class="doc-anno-broken"
                >
                  <details>
                    <summary>⚠ {{ group.threads.length }} 条批注的锚点因内容变更失效</summary>
                    <div v-for="thread in group.threads" :key="thread.root.id" class="doc-anno-broken-item">
                      <b>{{ thread.root.author }}</b>：{{ thread.root.content }}
                      <span class="doc-anno-broken-quote">原位置「{{ thread.root.anchorText }}」</span>
                    </div>
                  </details>
                </div>
                <ChecklistView
                  v-if="section.title === '六、测试约束'"
                  :content="section.content"
                  :editable="canEdit"
                  :anchor-lines="checklistAnchorLines(section)"
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
              <!-- 整章被删的断链组兜底（spec §5.3，终审 I2）：所属章不存在于正文时挂文档最顶部 -->
              <div
                v-for="group in orphanBrokenGroups"
                :key="`broken-orphan-${group.sectionTitle}`"
                class="doc-anno-broken"
              >
                <details>
                  <summary>⚠ 以下批注所属章节已不存在：{{ group.sectionTitle }}（{{ group.threads.length }} 条）</summary>
                  <div v-for="thread in group.threads" :key="thread.root.id" class="doc-anno-broken-item">
                    <b>{{ thread.root.author }}</b>：{{ thread.root.content }}
                    <span class="doc-anno-broken-quote">原位置「{{ thread.root.anchorText }}」</span>
                  </div>
                </details>
              </div>
            </div>

            <template v-if="commentLayer.addButton.value.visible && canComment">
              <button
                type="button"
                class="doc-anno-add"
                aria-label="添加批注"
                title="添加批注"
                :style="{ top: `${commentLayer.addButton.value.top}px`, left: `${commentLayer.addButton.value.left}px` }"
                @click="commentLayer.openComposerFor"
              >
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /><path d="M12 7v6M9 10h6" /></svg>
              </button>
            </template>
            <template v-if="commentLayer.composer.value">
              <div class="doc-anno-composer-wrap" :style="{ top: `${commentLayer.composer.value.top}px` }">
                <PlanCommentComposer
                  :busy="composerBusy"
                  @submit="submitAnchorComment"
                  @cancel="commentLayer.closeComposer"
                />
              </div>
            </template>
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

      <PlanCommentPanel
        v-if="doc.panelEffective.value"
        class="doc-anno-panel-col"
        :groups="panelGroups"
        :unresolved="doc.unresolvedCount.value"
        :resolved="resolvedCount"
        :doc="doc"
        @close="doc.togglePanel"
        @locate="locateThread"
        @resolve="(t, r) => doc.resolveComment(t.root.id, r)"
        @reply="(t, c) => doc.addAnchoredComment({ content: c, parentId: t.root.id })"
        @remove="removeComment"
      />
    </div>

    <PlanSectionEditor
      v-model:open="sectionEditorOpen"
      :plan-id="plan.id"
      :title="editingSectionTitle"
      :content="editingSectionContent"
      @save="saveSection"
    />
    <SectionTableEditor
      v-model:open="tableEditorOpen"
      :section-title="editingSectionTitle"
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
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { MdEditor, MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { PlanComment, PlanCommentThread, TaskPlan, TaskScenario } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import type { PlanCommentPanelGroup } from './PlanCommentPanel.vue';
import { useTheme } from '../../composables/useTheme';
import { CANONICAL_HEADINGS, checklistItemLines, extractSection, parseMarkdownTable, replaceSection, splitSections, toggleChecklistItem, type Section } from '../../utils/plan-markdown';
import { deriveAnchors } from '../../utils/plan-anchors';
import { planTableCompatible, planTableSchemaOf } from '../../utils/plan-table-schemas';
import { deleteCommentApi } from '../../api/plan-doc';
import { useDocCommentLayer } from '../../composables/useDocCommentLayer';
import PlanConflictDialog from './PlanConflictDialog.vue';
import PlanCommentComposer from './PlanCommentComposer.vue';
import PlanCommentPanel from './PlanCommentPanel.vue';
import PlanSectionEditor from './PlanSectionEditor.vue';
import SectionTableEditor from './SectionTableEditor.vue';
import ChecklistView from './ChecklistView.vue';
import ScenarioDesignModule from './ScenarioDesignModule.vue';

const props = defineProps<{
  doc: ReturnType<typeof usePlanDoc>;
  plan: TaskPlan;
  scenarios: TaskScenario[];
  /** 视图状态由父级 Tabs 行工具条持有（v-model:view-mode）。 */
  viewMode: 'Pretty' | 'Markdown';
  /** 评审工作台「↧ 定位」下发的待定位批注 id（跨 Tab，定位完成后置空）。 */
  locateCommentId?: number | null;
}>();
const emit = defineEmits<{
  (e: 'changed'): void;
  (e: 'request-add'): void;
  (e: 'request-edit', scenario: TaskScenario): void;
  (e: 'update:viewMode', value: 'Pretty' | 'Markdown'): void;
  (e: 'located'): void;
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
const tableEditorOpen = ref(false);
const editingSectionTitle = ref('');
const editingSectionContent = ref('');

const sections = computed(() => splitSections(props.plan.body));
const canEdit = computed(() => Boolean(props.doc.permissions.value.EDIT));
const canComment = computed(() => Boolean(props.doc.permissions.value.COMMENT));
const docMainRef = ref<HTMLElement | null>(null);
const currentSection = ref('');
const dirty = computed(() => editing.value && editDraft.value !== (props.plan.body ?? ''));

/** md-editor-v3 h2 锚点：encodeURIComponent 保证中文标题可直接 getElementById。 */
const ANCHOR_PREFIX = 'plan-mdh-';
const headingId = (options: { text: string }) => ANCHOR_PREFIX + encodeURIComponent(options.text.trim());
function anchorDomId(title: string) {
  return ANCHOR_PREFIX + encodeURIComponent(title.trim());
}

watch([viewMode, () => props.plan.body, editing], () => {
  void nextTick(updateCurrentSection);
}, { immediate: true });

/* ---------- 行级批注层（Task 7）：data-line 注入 + 悬浮「＋批注」入口 ---------- */

const commentLayer = useDocCommentLayer({
  containerRef: docMainRef,
  sections,
  threads: props.doc.threads,
  canComment,
  enabled: computed(() => viewMode.value === 'Pretty' && !editing.value),
  body: computed(() => props.plan.body),
});
const composerBusy = ref(false);

watch([() => props.plan.body, viewMode, editing, props.doc.threads], () => {
  void nextTick(() => window.requestAnimationFrame(() => void commentLayer.rebuild()));
}, { immediate: true });

async function submitAnchorComment(content: string) {
  const target = commentLayer.composer.value;
  if (!target) return;
  composerBusy.value = true;
  const ok = await props.doc.addAnchoredComment({
    content,
    anchor: { line: target.line, text: target.text, section: target.section },
  });
  composerBusy.value = false;
  if (ok) commentLayer.closeComposer();
}

/* ---------- 批注面板（Task 9）：未锚定置顶 → 章节序 → 已解决折叠 ---------- */

const resolvedCount = computed(() => props.doc.threads.value.filter((t) => t.root.resolved).length);

/** 断链组展示章不存在于正文（整章被删）时兜底渲染到 doc-flow 末尾（spec §5.3，终审 I2）。 */
const orphanBrokenGroups = computed(() =>
  commentLayer.brokenGroups.value.filter((g) => !sections.value.some((s) => s.title === g.sectionTitle)),
);

const panelGroups = computed<PlanCommentPanelGroup[]>(() => {
  const resolutions = deriveAnchors(props.plan.body, props.doc.anchoredRoots.value);
  const sectionOf = (thread: PlanCommentThread): string | null => {
    const r = resolutions.get(thread.root.id);
    if (!r) return null; // 未锚定
    return r.sectionTitle;
  };
  const unanchored = props.doc.unanchoredThreads.value;
  const bySection = new Map<string, PlanCommentThread[]>();
  const resolvedThreads: PlanCommentThread[] = [];
  for (const thread of props.doc.threads.value) {
    if (unanchored.includes(thread)) continue;
    if (thread.root.resolved) { resolvedThreads.push(thread); continue; }
    const section = sectionOf(thread) ?? CANONICAL_HEADINGS[0];
    bySection.set(section, [...(bySection.get(section) ?? []), thread]);
  }
  const groups: PlanCommentPanelGroup[] = [];
  if (unanchored.length) {
    groups.push({ key: 'unanchored', title: '未锚定', tone: 'grey', threads: unanchored });
  }
  for (const heading of CANONICAL_HEADINGS) {
    const threads = bySection.get(heading);
    if (threads?.length) groups.push({ key: heading, title: heading, tone: 'normal', threads });
  }
  if (resolvedThreads.length) {
    groups.push({ key: 'resolved', title: `已解决 ${resolvedThreads.length}`, tone: 'grey', threads: resolvedThreads });
  }
  return groups;
});

function locateThread(thread: PlanCommentThread) {
  const resolutions = deriveAnchors(props.plan.body, props.doc.anchoredRoots.value);
  const resolution = resolutions.get(thread.root.id);
  if (viewMode.value !== 'Pretty') viewMode.value = 'Pretty';
  // Markdown→Pretty 切换后 data-line 注入要等 rebuild（nextTick+rAF），与 locateCommentId watch 同约定（终审 I1）
  void nextTick(() => window.requestAnimationFrame(() => commentLayer.locate(resolution?.line ?? null)));
}

/** 评审工作台跨 Tab 定位（Task 10）：切 Pretty 后等 data-line 注入（同 rebuild 的 nextTick+rAF 约定）再滚动闪烁。 */
watch(() => props.locateCommentId, (id) => {
  if (id == null) return;
  if (viewMode.value !== 'Pretty') viewMode.value = 'Pretty';
  void nextTick(() => window.requestAnimationFrame(() => {
    const resolutions = deriveAnchors(props.plan.body, props.doc.anchoredRoots.value);
    commentLayer.locate(resolutions.get(id)?.line ?? null);
    emit('located');
  }));
});

async function removeComment(thread: PlanCommentThread, comment: PlanComment) {
  if (!props.doc.plan.value) return;
  await deleteCommentApi(props.doc.plan.value.id, comment.id);
  await props.doc.refresh();
  message.success('批注已删除');
}

/** 六章清单项的全局行号（spec §5.1）：章标题下一行起 + 清单项局部行号。 */
function checklistAnchorLines(section: Section): number[] {
  return checklistItemLines(section.content).map((local) => section.line + 1 + local);
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
  // 表格型章节走结构化表单（固定列、行增删）；首表与列 schema 不兼容（如模板「人员」子表）回落 Markdown 编辑
  const schema = planTableSchemaOf(title);
  if (schema && planTableCompatible(schema, parseMarkdownTable(editingSectionContent.value)?.header ?? null)) {
    tableEditorOpen.value = true;
    return;
  }
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
