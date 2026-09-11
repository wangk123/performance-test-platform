import { describe, expect, it } from 'vitest';
import { STATUS_LABEL, STAGES, activeWarning, stageIndexOf, visibleActions } from './plan-status';

describe('plan-status 状态与阶段推导（spec 2026-09-11 §3.1/§3.2）', () => {
  it('五状态标签', () => {
    expect(STATUS_LABEL.PLANNING).toBe('计划中');
    expect(STATUS_LABEL.IN_REVIEW).toBe('评审中');
    expect(STATUS_LABEL.EXECUTING).toBe('执行中');
    expect(STATUS_LABEL.REPORTING).toBe('报告编辑中');
    expect(STATUS_LABEL.PUBLISHED).toBe('已发布');
  });

  it('阶段条五段与状态推导下标', () => {
    expect(STAGES).toEqual(['计划', '评审', '执行', '报告', '发布']);
    expect(stageIndexOf('PLANNING')).toBe(0);
    expect(stageIndexOf('IN_REVIEW')).toBe(1);
    expect(stageIndexOf('EXECUTING')).toBe(2);
    expect(stageIndexOf('REPORTING')).toBe(3);
    expect(stageIndexOf('PUBLISHED')).toBe(4);
  });

  it('各状态可见流转按钮（单行道）', () => {
    expect(visibleActions('PLANNING')).toEqual(['submit']);
    expect(visibleActions('IN_REVIEW')).toEqual(['approve']);
    expect(visibleActions('EXECUTING')).toEqual(['finish-execution']);
    expect(visibleActions('REPORTING')).toEqual(['publish']);
    expect(visibleActions('PUBLISHED')).toEqual([]);
  });

  it('软门禁告警文案', () => {
    expect(activeWarning(0)).toBeNull();
    expect(activeWarning(3)).toBe('还有 3 个场景执行未完成，确认继续？');
  });
});
