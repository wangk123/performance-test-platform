<template>
  <section class="plan-document">
    <div class="doc-shell" :class="{ 'with-panel': doc.panelEffective.value }">
      <div class="doc-body">
        <nav class="doc-toc" aria-label="章节导航">
          <h4>章节导航</h4>
          <a
            v-for="section in sections"
            :key="section.line"
            class="toc-item"
            :class="{ current: currentSection === section.title }"
            href="#"
            :aria-current="currentSection === section.title ? 'true' : undefined"
            @click.prevent="jumpTo(section)"
          >
            <span class="toc-label">{{ section.heading }}</span>
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
                  <h3>{{ section.heading }}</h3>
                  <a-button
                    v-if="canEdit && !isModuleSection(section) && inlineTitle !== section.title"
                    class="doc-section-edit"
                    size="small"
                    type="text"
                    title="编辑本章"
                    aria-label="编辑本章"
                    @click="startInlineEdit(section)"
                  >
                    <template #icon>
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z" /></svg>
                    </template>
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
                <PlanSectionInlineEditor
                  v-if="inlineTitle === section.title"
                  :key="`inline-${section.title}`"
                  :plan-id="plan.id"
                  :title="section.title"
                  :heading="section.heading"
                  :content="inlineContent"
                  :busy="inlineSaving || conflictOpen"
                  @save="saveInline"
                  @cancel-request="cancelInlineRequest"
                  @update:dirty="inlineDirty = $event"
                />
                <ChecklistView
                  v-else-if="section.title === '六、测试约束'"
                  :content="section.content"
                  :editable="canEdit"
                  @toggle="toggleChecklist(section.content, $event)"
                />
                <ScenarioDesignModule
                  v-else-if="section.title === '八、场景设计' && section.heading.startsWith('八、场景设计')"
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
        @edit="(t, c) => doc.editComment(t.root.id, c)"
        @remove="removeComment"
      />
    </div>

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
import { CANONICAL_HEADINGS, extractSection, replaceSection, splitSections, toggleChecklistItem } from '../../utils/plan-markdown';
import type { Section } from '../../utils/plan-markdown';
import { findBestLine } from '../../utils/plan-anchors';
import { deleteCommentApi } from '../../api/plan-doc';
import { useDocCommentLayer } from '../../composables/useDocCommentLayer';
import PlanConflictDialog from './PlanConflictDialog.vue';
import PlanCommentComposer from './PlanCommentComposer.vue';
import PlanCommentPanel from './PlanCommentPanel.vue';
import PlanSectionInlineEditor from './PlanSectionInlineEditor.vue';
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
/** 行内编辑中的章节（整章编辑模式，一次一章）；null = 无。 */
const inlineTitle = ref<string | null>(null);
const inlineContent = ref('');
const inlineDirty = ref(false);
const inlineSaving = ref(false);

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

/** 行内编辑有脏稿时切去 Markdown 视图会静默卸载编辑器：先弹确认，确认前回退 Pretty 保持草稿。 */
watch(viewMode, (mode) => {
  if (mode === 'Pretty' || !inlineTitle.value || !inlineDirty.value) return;
  viewMode.value = 'Pretty';
  const heading = sections.value.find((s) => s.title === inlineTitle.value)?.heading;
  confirmDiscard(`切换视图将丢失「${heading ?? ''}」的草稿。`, cancelInline);
});

/* ---------- 行级批注层：悬浮「＋批注」入口（DOM 直连，spec §5.2） ---------- */

const commentLayer = useDocCommentLayer({
  containerRef: docMainRef,
  threads: props.doc.threads,
  canComment,
  enabled: computed(() => viewMode.value === 'Pretty' && !editing.value && inlineTitle.value === null),
  body: computed(() => props.plan.body),
});
const composerBusy = ref(false);

watch([() => props.plan.body, viewMode, editing, props.doc.threads], () => {
  // 行内编辑中冻结批注层：编辑器容器不是渲染块，重对齐会把该章批注误判为断链
  if (inlineTitle.value) return;
  void nextTick(() => window.requestAnimationFrame(() => void commentLayer.rebuild()));
}, { immediate: true });

async function submitAnchorComment(content: string) {
  const target = commentLayer.composer.value;
  if (!target) return;
  composerBusy.value = true;
  // 锚定源行在提交时用文本相似度行扫描解析（spec §5.3）——DOM 悬浮不携带任何行号
  const section = sections.value.find((s) => s.title === target.section);
  const best = findBestLine(props.plan.body, target.text);
  const line = best?.line ?? section?.line ?? 0;
  const ok = await props.doc.addAnchoredComment({
    content,
    anchor: { line, text: target.text, section: target.section },
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
  const sectionOf = (thread: PlanCommentThread): string | null => {
    if (thread.root.anchorText == null) return null; // 未锚定
    return findBestLine(props.plan.body, thread.root.anchorText)?.sectionTitle ?? null;
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
  if (viewMode.value !== 'Pretty') viewMode.value = 'Pretty';
  // Markdown→Pretty 切换后渲染要等一个渲染帧，与 rebuild 的 nextTick+rAF 约定一致
  void nextTick(() => window.requestAnimationFrame(
    () => commentLayer.locate(thread.root.anchorText, thread.root.sectionTitle),
  ));
}

/** 评审工作台跨 Tab 定位（Task 10）：切 Pretty 后等一个渲染帧再按锚文本定位。 */
watch(() => props.locateCommentId, (id) => {
  if (id == null) return;
  const thread = props.doc.threads.value.find((t) => t.root.id === id);
  if (!thread) {
    emit('located');
    return;
  }
  if (viewMode.value !== 'Pretty') viewMode.value = 'Pretty';
  void nextTick(() => window.requestAnimationFrame(() => {
    commentLayer.locate(thread.root.anchorText, thread.root.sectionTitle);
    emit('located');
  }));
});

async function removeComment(thread: PlanCommentThread, comment: PlanComment) {
  if (!props.doc.plan.value) return;
  await deleteCommentApi(props.doc.plan.value.id, comment.id);
  await props.doc.refresh();
  message.success('批注已删除');
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
  inlineTitle.value = null; // 冲突三选一均退出行内编辑：草稿基底已过期
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

/** 交互模块章（清单勾选 / 场景卡片）自带编辑能力，不提供行内 markdown 编辑入口。 */
function isModuleSection(section: Section): boolean {
  return section.title === '六、测试约束'
    || (section.title === '八、场景设计' && section.heading.startsWith('八、场景设计'));
}

/** 放弃草稿确认（唯一出口）：切换他章、Esc/✕ 取消、切换视图三条路径共用。 */
function confirmDiscard(context: string, onOk: () => void) {
  Modal.confirm({
    title: '放弃未保存的修改？',
    content: context,
    okText: '放弃修改',
    okType: 'danger',
    cancelText: '继续编辑',
    onOk,
  });
}

function startInlineEdit(section: Section) {
  if (inlineTitle.value === section.title) return;
  const open = () => {
    inlineTitle.value = section.title;
    inlineContent.value = extractSection(props.plan.body, section.title) ?? '';
    inlineDirty.value = false;
  };
  // 已有他章在编辑且未保存：先确认放弃，避免静默丢稿
  if (inlineTitle.value && inlineDirty.value) {
    confirmDiscard(`「${sections.value.find((s) => s.title === inlineTitle.value)?.heading ?? inlineTitle.value}」的草稿尚未保存。`, open);
    return;
  }
  open();
}

/** 编辑器取消请求（✕/Esc）：脏稿确认后关闭。 */
function cancelInlineRequest() {
  if (!inlineDirty.value) {
    cancelInline();
    return;
  }
  const heading = sections.value.find((s) => s.title === inlineTitle.value)?.heading;
  confirmDiscard(`「${heading ?? ''}」的草稿尚未保存，取消后修改将丢失。`, cancelInline);
}

async function saveInline(content: string) {
  const title = inlineTitle.value;
  if (!title || conflictOpen.value) return; // 冲突裁决期间禁止重复提交，防止覆盖 conflictLocal
  let next: string;
  try {
    next = replaceSection(props.plan.body ?? '', title, content);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '章节写回失败');
    return;
  }
  inlineSaving.value = true;
  const outcome = await props.doc.saveDocument(next);
  inlineSaving.value = false;
  if (outcome === 'ok') {
    inlineTitle.value = null;
    inlineDirty.value = false;
    emit('changed');
  } else if (outcome === 'conflict') {
    // 保持行内编辑态打开（草稿仍在），由冲突弹窗三选一
    conflictLocal.value = next;
    conflictOpen.value = true;
  }
}

function cancelInline() {
  inlineTitle.value = null;
  inlineDirty.value = false;
}

async function toggleChecklist(content: string, index: number) {
  const next = toggleChecklistItem(content, index);
  const body = replaceSection(props.plan.body ?? '', '六、测试约束', next);
  await submitWholeDocument(body);
}

/* ---------- TOC 跳转与 scrollspy（相对 .doc-main 唯一滚动容器定位） ---------- */

/** Pretty 视图按规范标题的 data-section 定位；Markdown 视图 h2 锚点由真实标题生成。 */
function jumpTo(section: Section) {
  const main = docMainRef.value;
  if (!main) return;
  let el: HTMLElement | null = null;
  if (viewMode.value === 'Pretty') {
    el = main.querySelector(`[data-section="${section.title}"]`);
  } else if (!editing.value) {
    el = document.getElementById(anchorDomId(section.heading));
  }
  if (!el || !main.contains(el)) return;
  main.scrollTo({
    top: main.scrollTop + el.getBoundingClientRect().top - main.getBoundingClientRect().top - 8,
    behavior: 'smooth',
  });
  currentSection.value = section.title;
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
      .map((el) => {
        const heading = decodeURIComponent(el.id.slice(ANCHOR_PREFIX.length));
        // Markdown 视图 h2 锚点由真实标题生成，scrollspy 归一到规范标题（与 TOC 高亮同口径）
        const section = sections.value.find((s) => s.heading === heading);
        return { el, title: section?.title ?? heading };
      });
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
