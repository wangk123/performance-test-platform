import { describe, expect, it } from 'vitest';
import {
  aggregateServiceShare,
  computeSpanLevels,
  panelStateOf,
  traceServiceColor,
} from './traces-view';
import type { ExecutionTracesPage } from '../types';

function page(overrides: Partial<ExecutionTracesPage>): ExecutionTracesPage {
  return { available: true, missingReason: null, traces: [], total: 0, page: 1, size: 20, ...overrides };
}

describe('traces-view 链路面板纯函数（trace-integration Phase 1）', () => {
  it('maps missingReason to panel state', () => {
    expect(panelStateOf(page({ available: true }))).toBe('ok');
    expect(panelStateOf(page({ available: false, missingReason: 'unconfigured' }))).toBe('unconfigured');
    expect(panelStateOf(page({ available: false, missingReason: 'source-unavailable' }))).toBe('down');
  });

  it('aggregates entry-service share', () => {
    const agg = aggregateServiceShare([
      { service: 'a', durationMs: 300, error: false },
      { service: 'b', durationMs: 100, error: true },
    ]);
    expect(agg).toEqual([
      { service: 'a', totalMs: 300, sharePct: 75, errorTraces: 0 },
      { service: 'b', totalMs: 100, sharePct: 25, errorTraces: 1 },
    ]);
  });

  it('derives span levels from parent chain', () => {
    const levels = computeSpanLevels([
      { spanId: 0, parentSpanId: -1 },
      { spanId: 1, parentSpanId: 0 },
    ]);
    expect(levels).toEqual([0, 1]);
  });

  it('handles out-of-order and orphan parents when deriving span levels', () => {
    const levels = computeSpanLevels([
      { spanId: 2, parentSpanId: 1 },
      { spanId: 0, parentSpanId: -1 },
      { spanId: 1, parentSpanId: 0 },
    ]);
    expect(levels).toEqual([2, 0, 1]);
  });

  it('colors known services deterministically and unknown services with a stable fallback', () => {
    expect(traceServiceColor('MySQL')).toBe(traceServiceColor('MySQL'));
    expect(traceServiceColor('unknown-service-x')).toMatch(/^#/);
    expect(traceServiceColor('unknown-service-x')).toBe(traceServiceColor('unknown-service-x'));
  });
});
