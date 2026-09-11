import { ref, type Ref } from 'vue';
import type { PlanCommentThread } from '../types';
import { deriveAnchors, findBestLine, matchScore } from '../utils/plan-anchors';

export interface ComposerTarget {
  top: number;
  text: string;
  section: string;
}

/** 可悬浮加批注的内容块（纯 DOM 判定，无任何前置模型）：表格行以行级为粒度，表头不可批注。 */
const HOVER_SELECTOR = 'p, li, tr, h1, h2, h3, h4, h5, h6, blockquote, pre, .check-item';
const ADD_BUTTON_SIZE = 28;
/** 锚定匹配阈值（spec §5.3） */
const MATCH_THRESHOLD = 0.6;

/**
 * 文档批注层（spec §5.2/§5.3，DOM 直连版）：按钮显隐只看「悬浮的是不是内容块」，零算法；
 * 批注与源码行的对应只在提交/展示两个时刻用「文本相似度行扫描」完成——渲染器怎么排版都不影响。
 */
export function useDocCommentLayer(options: {
  containerRef: Ref<HTMLElement | null>;
  threads: Ref<PlanCommentThread[]>;
  canComment: Ref<boolean>;
  /** Pretty 视图且非编辑态才生效 */
  enabled: Ref<boolean>;
  /** 当前文档正文：提交时定位源行、展示时断链判定（spec §5.3 纯派生不回写） */
  body: Ref<string | null>;
  /** 徽标点击回调（面板联动，spec §3.2；本任务不传） */
  onBadgeClick?: (line: number) => void;
}) {
  const addButton = ref({ visible: false, top: 0, left: 0 });
  const composer = ref<ComposerTarget | null>(null);
  // 悬浮目标：直接持有元素本身（提交时快照文本，无中间模型）
  const hoverTarget = ref<{ el: HTMLElement; section: string } | null>(null);
  /** 断链分组（spec §3.3）：DOM 中找不到命中块的批注按展示章归组，模板渲染折叠条 */
  const brokenGroups = ref<{ sectionTitle: string; threads: PlanCommentThread[] }[]>([]);

  const sectionEls = () =>
    [...(options.containerRef.value?.querySelectorAll<HTMLElement>('[data-section]') ?? [])];

  function sectionOf(target: HTMLElement): string {
    return target.closest<HTMLElement>('[data-section]')?.dataset.section ?? '';
  }

  // ---- 悬浮「＋批注」（spec §3.1）：纯 DOM 判定，单实例按钮跟随悬浮块 ----
  function onHover(event: MouseEvent): void {
    // 指针落在「＋批注」按钮上：保持现状（否则按钮卸载→重挂交替闪烁）
    if ((event.target as HTMLElement).closest?.('.doc-anno-add')) return;
    if (!options.enabled.value || !options.canComment.value || composer.value) return;
    const target = (event.target as HTMLElement).closest<HTMLElement>(HOVER_SELECTOR);
    // 非内容块（块间隙/表头/空白区）：保持现状——按钮只在移出文档容器或打开输入框时收起
    if (!target || !options.containerRef.value?.contains(target) || target.closest('thead')) return;
    const container = options.containerRef.value;
    const containerRect = container.getBoundingClientRect();
    const targetRect = target.getBoundingClientRect();
    // 按钮贴「文字实际结束处」：文本块用 Range 取最后一个文本行的右缘；表格行用表格右缘
    let anchorRight = targetRect.right;
    if (target.tagName !== 'TR' && target.tagName !== 'TABLE') {
      const range = document.createRange();
      range.selectNodeContents(target);
      const rects = [...range.getClientRects()].filter((r) => r.width > 1);
      const last = rects[rects.length - 1];
      if (last) anchorRight = last.right;
    }
    addButton.value = {
      visible: true,
      top: targetRect.top - containerRect.top + container.scrollTop
        + Math.min(Math.max((targetRect.height - ADD_BUTTON_SIZE) / 2, 0), 24),
      left: Math.max(container.clientWidth - ADD_BUTTON_SIZE - 4,
        Math.min(anchorRight - containerRect.left + 8, container.clientWidth - ADD_BUTTON_SIZE - 4)),
    };
    hoverTarget.value = { el: target, section: sectionOf(target) };
  }

  /** 鼠标移出文档容器：收起入口并清掉悬浮目标。 */
  function hideButton(): void {
    addButton.value.visible = false;
    hoverTarget.value = null;
  }

  function openComposerFor(): void {
    const target = hoverTarget.value;
    const container = options.containerRef.value;
    if (!target || !container) return;
    const text = (target.el.textContent ?? '').trim().slice(0, 200);
    const containerTop = container.getBoundingClientRect().top;
    composer.value = {
      top: target.el.getBoundingClientRect().bottom - containerTop + container.scrollTop + 6,
      text,
      section: target.section,
    };
    addButton.value.visible = false;
  }

  function closeComposer(): void {
    composer.value = null;
  }

  /** 章内最优命中块：按锚文本与渲染文本的匹配分选最大者。 */
  function bestElementFor(sectionEl: HTMLElement, anchorText: string): { el: HTMLElement; score: number } | null {
    let best: { el: HTMLElement; score: number } | null = null;
    for (const el of sectionEl.querySelectorAll<HTMLElement>(HOVER_SELECTOR)) {
      if (el.closest('thead')) continue;
      const score = matchScore(anchorText, el.textContent ?? '');
      if (score >= MATCH_THRESHOLD && (!best || score > best.score)) best = { el, score };
    }
    return best;
  }

  /** 徽标图标（SVG，禁 emoji）：未解决=对话气泡，已解决=对勾。 */
  const BADGE_BUBBLE_SVG = '<svg viewBox="0 0 24 24" width="11" height="11" fill="currentColor" aria-hidden="true"><path d="M4 4h16a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H9.4L5 21.4A1 1 0 0 1 3 20.6V5a1 1 0 0 1 1-1z"/></svg>';
  const BADGE_CHECK_SVG = '<svg viewBox="0 0 24 24" width="11" height="11" fill="currentColor" aria-hidden="true"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg>';

  /** 渲染已有批注（spec §3.3/§5.3）：徽标 + 高亮 + 断链分组；先清后挂，幂等。
   *  命中 = 章内按锚文本匹配分最高的内容块（DOM 直连，无中间模型）；找不到 → 断链分组。 */
  function renderAnnotations(): void {
    const container = options.containerRef.value;
    if (!container) return;
    container.querySelectorAll('.doc-anno-badge').forEach((el) => el.remove());
    container.querySelectorAll('[data-anno]').forEach((el) => el.removeAttribute('data-anno'));
    const hits = new Map<HTMLElement, PlanCommentThread[]>();
    const broken = new Map<string, PlanCommentThread[]>();
    for (const thread of options.threads.value) {
      const root = thread.root;
      if (root.anchorText == null) continue; // 无锚点（历史批注/流转附言）→ 面板与工作台呈现
      const sectionEl = sectionEls().find((el) => el.dataset.section === root.sectionTitle);
      const hit = sectionEl ? bestElementFor(sectionEl, root.anchorText) : null;
      if (!sectionEl || !hit) {
        const key = root.sectionTitle ?? '';
        broken.set(key, [...(broken.get(key) ?? []), thread]);
        continue;
      }
      hits.set(hit.el, [...(hits.get(hit.el) ?? []), thread]);
    }
    for (const [el, threadsAtEl] of hits) {
      const unresolved = threadsAtEl.filter((t) => !t.root.resolved).length;
      // 黄色背景只挂有未解决线程的行（spec §3.3：全部解决后高亮褪去，留灰色 ✓ 徽标）
      if (unresolved > 0) el.dataset.anno = String(threadsAtEl.length);
      const badge = document.createElement('span');
      badge.className = `doc-anno-badge${unresolved > 0 ? '' : ' resolved'}`;
      badge.innerHTML = `${unresolved > 0 ? BADGE_BUBBLE_SVG : BADGE_CHECK_SVG}<span class="doc-anno-badge-count">${threadsAtEl.length}</span>`;
      badge.title = threadsAtEl.map((t) => `${t.root.author}：${t.root.content}`).join('\n');
      badge.setAttribute('aria-label', `${threadsAtEl.length} 条批注`);
      badge.addEventListener('click', (event) => {
        event.stopPropagation();
        options.onBadgeClick?.(threadsAtEl[0].root.id);
      });
      // 表格行的徽标放进最后一个单元格（span 直接挂 tr 是无效 HTML，会被表格布局摆到奇怪的位置）
      const badgeHost = el.tagName === 'TR' ? (el.lastElementChild as HTMLElement | null) ?? el : el;
      if (badgeHost !== el) badge.classList.add('in-td');
      badgeHost.appendChild(badge);
    }
    brokenGroups.value = [...broken.entries()].map(([sectionTitle, threads]) => ({ sectionTitle, threads }));
  }

  async function rebuild(): Promise<void> {
    renderAnnotations();
  }

  /** 面板/工作台定位（spec §3.2）：按锚文本在章内找到命中块，滚动并闪烁。 */
  function locate(anchorText: string | null, sectionTitle: string | null): void {
    const container = options.containerRef.value;
    if (!container || !anchorText) return;
    const sectionEl = sectionEls().find((el) => el.dataset.section === sectionTitle);
    const hit = sectionEl ? bestElementFor(sectionEl, anchorText) : null;
    const el = hit?.el;
    if (!el) return;
    container.scrollTo({
      top: container.scrollTop + el.getBoundingClientRect().top - container.getBoundingClientRect().top - 96,
      behavior: 'smooth',
    });
    el.classList.add('doc-anno-flash');
    window.setTimeout(() => el.classList.remove('doc-anno-flash'), 1600);
  }

  return {
    addButton, composer, hoverTarget,
    onHover, hideButton, openComposerFor, closeComposer, rebuild,
    brokenGroups, renderAnnotations, locate,
  };
}
