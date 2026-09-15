import type { EnvCheckRunRow } from '../../types';

/** 结果矩阵行：rowKey = itemKey@host（LOCAL 行 host 为 null 取 'local'）。 */
export type EnvCheckMatrixRow = EnvCheckRunRow & {
  rowKey: string;
  module: string;
  label: string;
};

/** 可修复问题行：WARNING + fixable + 有 host 与 risk。 */
export type EnvCheckIssueRow = EnvCheckMatrixRow & {
  risk: 'LOW' | 'MEDIUM' | 'HIGH';
};

/** run 行键：itemKey@host。 */
export function rowKeyOf(row: EnvCheckRunRow): string {
  return `${row.itemKey}@${row.host ?? 'local'}`;
}

/** 修复请求拆分：rowKey → { itemKey, host }。 */
export function fixRequestOf(rowKey: string): { itemKey: string; host: string } {
  const at = rowKey.indexOf('@');
  return { itemKey: rowKey.slice(0, at), host: rowKey.slice(at + 1) };
}

/** diff 行染色：+ → add，- → del，其余 ctx。 */
export function diffLines(text: string): Array<{ cls: string; text: string }> {
  return text.split('\n').map((line) => ({
    cls: line.startsWith('+') ? 'add' : line.startsWith('-') ? 'del' : 'ctx',
    text: line,
  }));
}
