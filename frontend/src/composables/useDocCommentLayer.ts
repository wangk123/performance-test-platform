import { ref, type Ref } from 'vue';
import type { PlanCommentThread } from '../types';
import { listItemOffsets, splitBlocks, type Section } from '../utils/plan-markdown';
import { deriveAnchors } from '../utils/plan-anchors';

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
  /** 当前文档正文：deriveAnchors 的输入（spec §5.3 纯派生不回写） */
  body: Ref<string | null>;
  /** 徽标点击回调（面板联动，spec §3.2；本任务不传） */
  onBadgeClick?: (line: number) => void;
}) {
  const addButton = ref({ visible: false, top: 0 });
  const composer = ref<ComposerTarget | null>(null);
  // 悬浮目标（实现裁决）：不用 dataset 挂载，line/section 以 ref 承载，openComposerFor 从这里读
  const hoverTarget = ref<{ line: number; section: string } | null>(null);
  /** 断链分组（spec §3.3）：deriveAnchors 判 broken 的批注按展示章归组，模板渲染折叠条 */
  const brokenGroups = ref<{ sectionTitle: string; threads: PlanCommentThread[] }[]>([]);

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
      const host = previewHost(sectionEl);
      // 无 MdPreview 的章节（六章清单/八章场景模块）：行锚点由模板绑定（h3/check-item data-line），
      // 注入层不得触碰——旧实现整节清除会抹掉 Vue 绑定的 data-line
      if (!host) continue;
      host.querySelectorAll('[data-line]').forEach((el) => el.removeAttribute('data-line'));
      const title = sectionEl.dataset.section ?? '';
      const section = options.sections.value.find((s) => s.title === title);
      if (!section || !section.content.trim()) continue;
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
    // 指针落在「＋批注」按钮上：保持现状，避免 closest('[data-line]') 落空导致按钮卸载→重挂交替闪烁
    if ((event.target as HTMLElement).closest?.('.doc-anno-add')) return;
    if (!options.enabled.value || !options.canComment.value || composer.value) return;
    const target = (event.target as HTMLElement).closest<HTMLElement>('[data-line]');
    if (!target || !options.containerRef.value?.contains(target)) {
      addButton.value.visible = false;
      hoverTarget.value = null;
      return;
    }
    // .doc-main 既是滚动容器又是定位容器：绝对定位 top 属内容坐标，
    // getBoundingClientRect 差值是可视偏移（内容坐标 − scrollTop），需补回 scrollTop
    const containerTop = options.containerRef.value.getBoundingClientRect().top;
    addButton.value = {
      visible: true,
      top: target.getBoundingClientRect().top - containerTop + options.containerRef.value.scrollTop,
    };
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
    // 同 onHover：补回 scrollTop 换算到内容坐标，否则滚动后 composer 渲染在可视区外
    const blockBottom = el
      ? el.getBoundingClientRect().bottom - containerTop + container.scrollTop
      : addButton.value.top;
    composer.value = { top: blockBottom + 6, line, text: blockText, section: sectionOf(el ?? container) };
    addButton.value.visible = false;
  }

  function closeComposer(): void {
    composer.value = null;
  }

  /** 渲染已有批注（spec §3.3/§5.3）：徽标 + 高亮 + 断链分组；先清后挂，幂等。 */
  function renderAnnotations(): void {
    const container = options.containerRef.value;
    if (!container) return;
    container.querySelectorAll('.doc-anno-badge').forEach((el) => el.remove());
    container.querySelectorAll('[data-anno]').forEach((el) => el.removeAttribute('data-anno'));
    container.querySelectorAll('.doc-anno-hl').forEach((el) => el.classList.remove('doc-anno-hl'));
    const roots = options.threads.value.map((t) => t.root);
    const resolutions = deriveAnchors(options.body.value, roots);
    const byLine = new Map<number, PlanCommentThread[]>();
    const broken: Map<string, PlanCommentThread[]> = new Map();
    for (const thread of options.threads.value) {
      const resolution = resolutions.get(thread.root.id);
      if (!resolution || resolution.line == null) continue; // 无锚点/断链 → 面板与断链条呈现
      if (resolution.state === 'broken') {
        broken.set(resolution.sectionTitle, [...(broken.get(resolution.sectionTitle) ?? []), thread]);
        continue;
      }
      byLine.set(resolution.line, [...(byLine.get(resolution.line) ?? []), thread]);
    }
    for (const [line, threadsAtLine] of byLine) {
      const el = container.querySelector<HTMLElement>(`[data-line="${line}"]`);
      if (!el) continue;
      const unresolved = threadsAtLine.filter((t) => !t.root.resolved).length;
      el.dataset.anno = String(threadsAtLine.length);
      if (unresolved > 0) el.classList.add('doc-anno-hl');
      const badge = document.createElement('span');
      badge.className = `doc-anno-badge${unresolved > 0 ? '' : ' resolved'}`;
      badge.textContent = unresolved > 0 ? `💬 ${threadsAtLine.length}` : `✓ ${threadsAtLine.length}`;
      badge.title = threadsAtLine.map((t) => `${t.root.author}：${t.root.content}`).join('\n');
      badge.addEventListener('click', (event) => {
        event.stopPropagation();
        options.onBadgeClick?.(line);
      });
      el.appendChild(badge);
    }
    brokenGroups.value = [...broken.entries()].map(([sectionTitle, threads]) => ({ sectionTitle, threads }));
  }

  async function rebuild(): Promise<void> {
    injectDataLines();
    renderAnnotations();
  }

  /** 面板/工作台定位（spec §3.2）：滚动到块并闪烁高亮。 */
  function locate(line: number | null): void {
    const container = options.containerRef.value;
    if (!container || line == null) return;
    const el = container.querySelector<HTMLElement>(`[data-line="${line}"]`);
    if (!el) return;
    container.scrollTo({ top: container.scrollTop + el.getBoundingClientRect().top - container.getBoundingClientRect().top - 96, behavior: 'smooth' });
    el.classList.add('doc-anno-flash');
    window.setTimeout(() => el.classList.remove('doc-anno-flash'), 1600);
  }

  return {
    addButton, composer, hoverTarget,
    onHover, hideButton, openComposerFor, closeComposer, rebuild,
    brokenGroups, renderAnnotations, locate,
  };
}
