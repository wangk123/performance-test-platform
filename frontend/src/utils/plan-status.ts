/** 计划单一状态 → UI 推导（spec 2026-09-11 §3.1/§3.2/§4.3）。纯函数，供步骤条/按钮区/告警共用。 */

export const STATUS_LABEL: Record<string, string> = {
  PLANNING: '计划中',
  IN_REVIEW: '评审中',
  EXECUTING: '执行中',
  REPORTING: '报告编辑中',
  PUBLISHED: '已发布',
};

/** 阶段步骤条（纯展示，不落库）：计划 → 评审 → 执行 → 报告 → 发布。 */
export const STAGES = ['计划', '评审', '执行', '报告', '发布'] as const;

const STAGE_ORDER = ['PLANNING', 'IN_REVIEW', 'EXECUTING', 'REPORTING', 'PUBLISHED'];

export function stageIndexOf(status: string): number {
  const index = STAGE_ORDER.indexOf(status);
  return index < 0 ? 0 : index;
}

/** 状态 → 可见流转按钮（单行道，每状态至多一个；「新增版本」为全局常驻，不在此列）。 */
export function visibleActions(status: string): string[] {
  const map: Record<string, string[]> = {
    PLANNING: ['submit'],
    IN_REVIEW: ['approve'],
    EXECUTING: ['finish-execution'],
    REPORTING: ['publish'],
    PUBLISHED: [],
  };
  return map[status] ?? [];
}

/** 软门禁：执行完成/发布前活跃执行告警文案；无活跃执行返回 null。 */
export function activeWarning(activeExecutions: number): string | null {
  if (activeExecutions <= 0) return null;
  return `还有 ${activeExecutions} 个场景执行未完成，确认继续？`;
}
