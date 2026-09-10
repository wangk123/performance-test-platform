import type { PlanComment } from '../types';
import { blockAnchorLines, CANONICAL_HEADINGS, splitBlocks, splitSections } from './plan-markdown';

export type AnchorState = 'ok' | 'remounted' | 'broken';

export interface AnchorResolution {
  commentId: number;
  state: AnchorState;
  /** 命中块的全局行号（body 0 基）；broken 时为 null */
  line: number | null;
  /** 展示分组归属（broken 回原章；spec §5.3） */
  sectionTitle: string;
}

/** spec §5.3 阈值：归一化相似度 ≥ 0.6 视为同一内容（算法用字符 bigram Dice，O(n) 优于 LCS）。 */
const SIMILARITY_THRESHOLD = 0.6;

/** 去空白与 Markdown 修饰符（含全角逗号）、转小写——锚点匹配的归一化口径。 */
export function normalizeForMatch(text: string): string {
  return text.replace(/[\s#*>`|~_[\]()\\，-]/g, '').toLowerCase();
}

function bigramsOf(normalized: string): Set<string> {
  const grams = new Set<string>();
  for (let i = 0; i < normalized.length - 1; i++) grams.add(normalized.slice(i, i + 2));
  return grams;
}

/** 字符 bigram Dice 系数：2|A∩B|/(|A|+|B|)；短文本（<2 字符）退化为全等判断。 */
export function similarity(a: string, b: string): number {
  const na = normalizeForMatch(a);
  const nb = normalizeForMatch(b);
  if (na === nb) return 1;
  if (na.length < 2 || nb.length < 2) return 0;
  const ga = bigramsOf(na);
  const gb = bigramsOf(nb);
  let intersect = 0;
  for (const gram of ga) if (gb.has(gram)) intersect++;
  return (2 * intersect) / (ga.size + gb.size);
}

interface AnchorBlock {
  line: number;
  sectionTitle: string;
  text: string;
}

function anchorBlocks(body: string | null | undefined): AnchorBlock[] {
  const blocks: AnchorBlock[] = [];
  for (const section of splitSections(body)) {
    // 标题行块：章级批注（章级入口 hover 标题，anchorLine = 标题行、anchorText = 章标题）精确命中，不误报断链
    blocks.push({ line: section.line, sectionTitle: section.title, text: `## ${section.title}` });
    const offset = section.line + 1; // 章内容从标题行下一行开始
    for (const block of splitBlocks(section.content)) {
      // 行级展开（终审 C1）：表格数据行/列表项/清单项逐行建锚，与 useDocCommentLayer 的
      // data-line 注入共用 blockAnchorLines，两侧行号同源，行级批注不再一出生就断链
      for (const { line, text } of blockAnchorLines(block)) {
        blocks.push({ line: offset + line, sectionTitle: section.title, text });
      }
    }
  }
  return blocks;
}

/**
 * 精确行命中判定（spec §5.3，终审 I3）：相似度 ≥ 0.6 **或前缀/包含**。
 * 快照截断 200 字符，未改长块的 bigram Dice 会退化（>500 归一化字符即低于阈值），
 * 故补充归一化包含判断；较短方 ≥ 8 归一化字符才启用，防短文本误包含。
 */
function snapshotMatches(anchorText: string, blockText: string): boolean {
  if (similarity(anchorText, blockText) >= SIMILARITY_THRESHOLD) return true;
  const na = normalizeForMatch(anchorText);
  const nb = normalizeForMatch(blockText);
  if (na.length < 8 || nb.length < 8) return false;
  return na.includes(nb) || nb.includes(na);
}

/**
 * 锚定派生（spec §5.3，不回写）：精确行号 → 章内模糊 → 全文模糊 → 断链。
 * 只接受带完整锚点的根批注；返回 commentId → 分辨结果。
 */
export function deriveAnchors(body: string | null | undefined, roots: PlanComment[]): Map<number, AnchorResolution> {
  const result = new Map<number, AnchorResolution>();
  const blocks = anchorBlocks(body);
  const byLine = new Map(blocks.map((b) => [b.line, b]));
  for (const comment of roots) {
    if (comment.anchorLine == null || comment.anchorText == null || comment.sectionTitle == null) continue;
    const resolution = (state: AnchorState, line: number | null, sectionTitle: string): AnchorResolution =>
      ({ commentId: comment.id, state, line, sectionTitle });
    const exact = byLine.get(comment.anchorLine);
    if (exact && snapshotMatches(comment.anchorText, exact.text)) {
      result.set(comment.id, resolution('ok', exact.line, exact.sectionTitle));
      continue;
    }
    const inSection = blocks.filter((b) => b.sectionTitle === comment.sectionTitle);
    const globalBest = bestMatch(comment.anchorText, inSection) ?? bestMatch(comment.anchorText, blocks);
    if (globalBest) {
      result.set(comment.id, resolution('remounted', globalBest.line, globalBest.sectionTitle));
      continue;
    }
    const fallbackSection = CANONICAL_HEADINGS.includes(comment.sectionTitle)
      ? comment.sectionTitle
      : CANONICAL_HEADINGS[0];
    result.set(comment.id, resolution('broken', null, fallbackSection));
  }
  return result;
}

function bestMatch(anchorText: string, candidates: AnchorBlock[]): AnchorBlock | null {
  let best: AnchorBlock | null = null;
  let bestScore = 0; // 阈值统一在下面卡（≥ 0.6 才可入选，终审 A），0 分候选不会入选
  for (const candidate of candidates) {
    const score = similarity(anchorText, candidate.text);
    if (score >= SIMILARITY_THRESHOLD && score > bestScore) {
      best = candidate;
      bestScore = score;
    }
  }
  return best;
}
