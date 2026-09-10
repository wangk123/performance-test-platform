import { ref, type Ref } from 'vue';
import type { PlanCommentThread } from '../types';
import { listItemOffsets, splitBlocks, type Section } from '../utils/plan-markdown';

export interface ComposerTarget {
  top: number;
  line: number;
  text: string;
  section: string;
}

/**
 * 文档批注层（spec §5.1/§5.2）：MdPreview 渲染后按「顶层子元素 ↔ splitBlocks 块」对齐注入 data-line；
 * 表格行/列表项细化映射。锚定状态纯派生、不回写（spec §4）。
 */
export function useDocCommentLayer(options: {
  containerRef: Ref<HTMLElement | null>;
  sections: Ref<Section[]>;
  threads: Ref<PlanCommentThread[]>;
  canComment: Ref<boolean>;
  /** Pretty 视图且非编辑态才生效 */
  enabled: Ref<boolean>;
}) {
  const addButton = ref({ visible: false, top: 0 });
  const composer = ref<ComposerTarget | null>(null);
  // 悬浮目标（实现裁决）：不用 dataset 挂载，line/section 以 ref 承载，openComposerFor 从这里读
  const hoverTarget = ref<{ line: number; section: string } | null>(null);

  const sectionEls = () =>
    [...(options.containerRef.value?.querySelectorAll<HTMLElement>('[data-section]') ?? [])];

  /** MdPreview 内容宿主：优先 .markdown-body 内层，回退 .md-editor-preview 本身。 */
  function previewHost(sectionEl: HTMLElement): HTMLElement | null {
    const preview = sectionEl.querySelector<HTMLElement>('.md-editor-preview');
    if (!preview) return null;
    return preview.querySelector<HTMLElement>(':scope > .markdown-body') ?? preview;
  }

  function blockLinesOf(raw: string, baseLine: number, el: HTMLElement): number[] {
    const blocks = splitBlocks(raw);
    const block = blocks[0];
    if (!block) return [];
    if (el.tagName === 'TABLE') {
      // 表格：数据行逐行映射（表头+分隔行占块首两行，spec §5.1）
      const rows = el.querySelectorAll<HTMLElement>('tbody tr');
      return [...rows].map((_, i) => baseLine + block.startLine + 2 + i);
    }
    if (el.tagName === 'UL' || el.tagName === 'OL') {
      const offsets = listItemOffsets(block.raw);
      const items = el.querySelectorAll<HTMLElement>('li');
      return [...items].map((_, i) => (offsets[i] == null ? -1 : baseLine + block.startLine + offsets[i]))
        .filter((line) => line >= 0);
    }
    return [baseLine + block.startLine];
  }

  /** 映射注入：数量不一致（Markdown 边界形态）则整章跳过，宁可少注入也不错位（spec §5.4）。 */
  function injectDataLines(): void {
    for (const sectionEl of sectionEls()) {
      sectionEl.querySelectorAll('[data-line]').forEach((el) => el.removeAttribute('data-line'));
      const title = sectionEl.dataset.section ?? '';
      const section = options.sections.value.find((s) => s.title === title);
      const host = previewHost(sectionEl);
      if (!section || !host || !section.content.trim()) continue;
      const blocks = splitBlocks(section.content);
      const children = [...host.children] as HTMLElement[];
      if (children.length !== blocks.length) continue;
      const baseLine = section.line + 1;
      children.forEach((child, i) => {
        const lines = blockLinesOf(blocks[i].raw, baseLine, child);
        const line = lines.length === 1 ? lines[0] : -1;
        if (line >= 0) child.dataset.line = String(line);
        // 表格行/列表项：映射到子元素
        if (lines.length > 1) {
          const targets = child.tagName === 'TABLE'
            ? ([...child.querySelectorAll<HTMLElement>('tbody tr')])
            : ([...child.querySelectorAll<HTMLElement>('li')]);
          targets.forEach((target, k) => {
            if (lines[k] >= 0) target.dataset.line = String(lines[k]);
          });
        }
      });
    }
  }

  // ---- 悬浮「＋批注」（spec §3.1）：事件委托，单实例按钮跟随悬浮块 ----
  function onHover(event: MouseEvent): void {
    if (!options.enabled.value || !options.canComment.value || composer.value) return;
    const target = (event.target as HTMLElement).closest<HTMLElement>('[data-line]');
    if (!target || !options.containerRef.value?.contains(target)) {
      addButton.value.visible = false;
      hoverTarget.value = null;
      return;
    }
    const containerTop = options.containerRef.value.getBoundingClientRect().top;
    addButton.value = { visible: true, top: target.getBoundingClientRect().top - containerTop };
    hoverTarget.value = { line: Number(target.dataset.line), section: sectionOf(target) };
  }

  /** 鼠标移出文档容器：收起入口并清掉悬浮目标。 */
  function hideButton(): void {
    addButton.value.visible = false;
    hoverTarget.value = null;
  }

  function sectionOf(target: HTMLElement): string {
    return target.closest<HTMLElement>('[data-section]')?.dataset.section ?? '';
  }

  function openComposerFor(): void {
    const target = hoverTarget.value;
    if (!target || !Number.isFinite(target.line)) return;
    const line = target.line;
    const container = options.containerRef.value;
    if (!container) return;
    const el = container.querySelector<HTMLElement>(`[data-line="${line}"]`);
    const blockText = (el?.textContent ?? '').trim().slice(0, 200);
    const containerTop = container.getBoundingClientRect().top;
    const blockBottom = el ? el.getBoundingClientRect().bottom - containerTop : addButton.value.top;
    composer.value = { top: blockBottom + 6, line, text: blockText, section: sectionOf(el ?? container) };
    addButton.value.visible = false;
  }

  function closeComposer(): void {
    composer.value = null;
  }

  async function rebuild(): Promise<void> {
    injectDataLines();
  }

  return {
    addButton, composer, hoverTarget,
    onHover, hideButton, openComposerFor, closeComposer, rebuild,
    // Task 8 扩展：renderAnnotations / brokenGroups / locate
  };
}
