// 链路面板纯视图逻辑（trace-integration Phase 1）：三态映射 / 入口服务聚合 / span 层级推导 / 服务配色。
// 纪律：不依赖 Vue 运行时，vitest 直测；组件只做数据装配与交互。
import type { ExecutionTracesPage, TraceSpan } from '../types';

export type TracePanelState = 'ok' | 'unconfigured' | 'down';

/** 列表响应 → 面板三态。missingReason=retention-expired 只出现在详情，列表兜底按 down 处理。 */
export function panelStateOf(page: ExecutionTracesPage): TracePanelState {
  if (page.available) return 'ok';
  return page.missingReason === 'unconfigured' ? 'unconfigured' : 'down';
}

export type TraceServiceShareInput = {
  service: string;
  durationMs: number;
  error: boolean;
};

export type TraceServiceShare = {
  service: string;
  totalMs: number;
  sharePct: number;
  errorTraces: number;
};

/** 入口服务维度聚合（Phase 1 口径；span 维度 Phase 2 升级）：耗时占比 + 错误 trace 计数，按耗时降序。 */
export function aggregateServiceShare(traces: TraceServiceShareInput[]): TraceServiceShare[] {
  const stats = new Map<string, { totalMs: number; errorTraces: number }>();
  for (const trace of traces) {
    const entry = stats.get(trace.service) ?? { totalMs: 0, errorTraces: 0 };
    entry.totalMs += trace.durationMs;
    if (trace.error) entry.errorTraces += 1;
    stats.set(trace.service, entry);
  }
  const grand = [...stats.values()].reduce((sum, v) => sum + v.totalMs, 0) || 1;
  return [...stats.entries()]
    .map(([service, v]) => ({ service, totalMs: v.totalMs, sharePct: Math.round((v.totalMs / grand) * 100), errorTraces: v.errorTraces }))
    .sort((a, b) => b.totalMs - a.totalMs);
}

export type SpanLevelInput = Pick<TraceSpan, 'spanId' | 'parentSpanId'>;

/** 按父链推导每个 span 的缩进层级：根（parentSpanId=-1 或父缺失）为 0；乱序/环均有界。 */
export function computeSpanLevels(spans: SpanLevelInput[]): number[] {
  const byId = new Map(spans.map(span => [span.spanId, span]));
  const cache = new Map<number, number>();
  const levelOf = (spanId: number): number => {
    const cached = cache.get(spanId);
    if (cached !== undefined) return cached;
    cache.set(spanId, 0); // 先落 0 防环：环上节点按根处理
    const span = byId.get(spanId);
    const level = span && span.parentSpanId !== -1 && byId.has(span.parentSpanId) && span.parentSpanId !== spanId
      ? levelOf(span.parentSpanId) + 1
      : 0;
    cache.set(spanId, level);
    return level;
  };
  return spans.map(span => levelOf(span.spanId));
}

const TRACE_SERVICE_COLORS: Record<string, string> = {
  'api-gateway': '#0b7f8a',
  'order-service': '#3b82f6',
  'cart-service': '#6366f1',
  'inventory-service': '#f59e0b',
  'user-service': '#8b5cf6',
  'MySQL': '#16a34a',
  'Redis': '#ec4899',
};

const FALLBACK_COLORS = ['#0e7490', '#7c3aed', '#b45309', '#15803d', '#be185d', '#4d7c0f', '#9333ea', '#0f766e'];

/** 服务配色：常见服务固定色，其余按名称哈希取稳定回退色（同服务同色）。 */
export function traceServiceColor(service: string): string {
  const known = TRACE_SERVICE_COLORS[service];
  if (known) return known;
  let hash = 0;
  for (let i = 0; i < service.length; i += 1) {
    hash = (hash * 31 + service.charCodeAt(i)) >>> 0;
  }
  return FALLBACK_COLORS[hash % FALLBACK_COLORS.length];
}
