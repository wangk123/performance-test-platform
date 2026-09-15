import type { EnvCheckItemMeta } from '../../types';

/** 设置卡渲染模型：清单完全由注册表接口驱动，前端不枚举检查项 key。 */
export type EnvCheckItemGroup = {
  category: EnvCheckItemMeta['category'];
  categoryLabel: string;
  kind: EnvCheckItemMeta['kind'];
  items: EnvCheckItemMeta[];
};

const CATEGORY_LABELS: Record<string, string> = {
  DOC: '文档核验',
  OS: '操作系统',
  JVM: 'JVM / 中间件',
  MIDDLEWARE: '中间件',
};

/** 按类别分组（组顺序 = 首个出现顺序，组内按注册表 sortOrder），kind 取组内首项。 */
export function groupEnvCheckItems(items: EnvCheckItemMeta[]): EnvCheckItemGroup[] {
  const buckets = new Map<string, EnvCheckItemMeta[]>();
  for (const item of items) {
    const bucket = buckets.get(item.category) ?? [];
    bucket.push(item);
    buckets.set(item.category, bucket);
  }
  return [...buckets.entries()].map(([category, bucket]) => ({
    category: category as EnvCheckItemMeta['category'],
    categoryLabel: CATEGORY_LABELS[category] ?? category,
    kind: bucket[0]?.kind ?? 'LOCAL',
    items: [...bucket].sort((a, b) => a.sortOrder - b.sortOrder),
  }));
}

/** 行尾 meta chip：有 risk → 风险 chip（三级色 + 可修/提示）；无 risk → 类型 chip。 */
export function riskChip(item: EnvCheckItemMeta): { level: 'low' | 'medium' | 'high'; text: string } | null {
  if (!item.risk) return null;
  const levelText = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }[item.risk];
  const tail = item.fixable ? '可修' : item.risk === 'HIGH' ? '建议' : '提示';
  return { level: item.risk.toLowerCase() as 'low' | 'medium' | 'high', text: `${levelText} · ${tail}` };
}

export function kindChip(item: EnvCheckItemMeta): string {
  return item.kind === 'LOCAL' ? '平台内' : '平台内拨测';
}

/** precheckJson 解析：null/非法 → 未启用且未勾选（对齐后端 disabled() 语义）；返回可勾选 key 集。 */
export function parsePrecheckSettings(
  precheckJson: string | null,
  registryKeys: string[],
): { enabled: boolean; items: string[] } {
  if (!precheckJson) return { enabled: false, items: [] };
  try {
    const raw = JSON.parse(precheckJson) as { enabled?: boolean; items?: unknown };
    const items = Array.isArray(raw.items)
      ? raw.items.filter((key): key is string => typeof key === 'string' && registryKeys.includes(key))
      : [];
    return { enabled: Boolean(raw.enabled), items };
  } catch {
    return { enabled: false, items: [] };
  }
}

/** 组头「N / M 已选」计数。 */
export function selectedCountText(items: EnvCheckItemMeta[], selected: string[]): string {
  const keys = new Set(selected);
  const chosen = items.filter((item) => keys.has(item.key)).length;
  return `${chosen} / ${items.length} 已选`;
}
