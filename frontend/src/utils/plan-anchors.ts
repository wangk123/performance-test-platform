import type { PlanComment } from '../types';
import { CANONICAL_HEADINGS, normalizeForMatch, splitSections } from './plan-markdown';

// 归一化口径定义在 plan-markdown（与历史测试的导入路径保持兼容）
export { normalizeForMatch };

export type AnchorState = 'ok' | 'broken';

export interface AnchorResolution {
  commentId: number;
  state: AnchorState;
  /** 命中源行（body 0 基）；broken 时为 null */
  line: number | null;
  /** 展示分组归属（broken 回原章；spec §5.3） */
  sectionTitle: string;
}

/** spec §5.3 阈值：归一化相似度 ≥ 0.6 视为同一内容（算法用字符 bigram Dice，O(n) 优于 LCS）。 */
const SIMILARITY_THRESHOLD = 0.6;
/** 归一化包含判定的最短长度：双向包含且较短方 ≥ 8 字符视为完全一致（有序列表标记、徽标尾部等噪声免疫）。 */
const CONTAINMENT_MIN = 8;

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

/**
 * 锚定匹配评分（spec §5.3）：双向归一化包含（较短方 ≥ 8 字符）直接满分——
 * 渲染文本与源行的差异（列表标记「1. / - 」、徽标尾部计数）天然被包含关系吸收；
否则落入 bigram Dice。
 */
export function matchScore(a: string, b: string): number {
  const na = normalizeForMatch(a);
  const nb = normalizeForMatch(b);
  if (na.length < CONTAINMENT_MIN || nb.length < CONTAINMENT_MIN) return similarity(a, b);
  if (na.includes(nb) || nb.includes(na)) return 1;
  return similarity(a, b);
}

/**
 * 全文行扫描：为渲染文本找最相似的源行（spec §5.3 锚定匹配的唯一实现——
 * 不依赖任何中间块模型，与 markdown-it 渲染语义零耦合）。
 */
export function findBestLine(
  body: string | null | undefined,
  text: string,
): { line: number; sectionTitle: string; score: number } | null {
  if (!text.trim()) return null;
  let best: { line: number; sectionTitle: string; score: number } | null = null;
  for (const section of splitSections(body)) {
    const offset = section.line + 1; // 章内容从标题行下一行开始
    const lines = section.content.split('\n');
    for (let i = 0; i < lines.length; i++) {
      if (!lines[i].trim()) continue;
      const score = matchScore(text, lines[i]);
      if (score >= SIMILARITY_THRESHOLD && (!best || score > best.score)) {
        best = { line: offset + i, sectionTitle: section.title, score };
      }
    }
  }
  return best;
}

/**
 * 锚定解析（spec §5.3，不回写）：对每个带完整锚点的根批注做全文行扫描；
 * 找不到即断链，归属回原章（章标题也失效时回首章）。返回 commentId → 分辨结果。
 */
export function deriveAnchors(body: string | null | undefined, roots: PlanComment[]): Map<number, AnchorResolution> {
  const result = new Map<number, AnchorResolution>();
  for (const comment of roots) {
    if (comment.anchorLine == null || comment.anchorText == null || comment.sectionTitle == null) continue;
    const fallbackSection = CANONICAL_HEADINGS.includes(comment.sectionTitle)
      ? comment.sectionTitle
      : CANONICAL_HEADINGS[0];
    const best = findBestLine(body, comment.anchorText);
    if (best) {
      result.set(comment.id, { commentId: comment.id, state: 'ok', line: best.line, sectionTitle: best.sectionTitle });
    } else {
      result.set(comment.id, { commentId: comment.id, state: 'broken', line: null, sectionTitle: fallbackSection });
    }
  }
  return result;
}
