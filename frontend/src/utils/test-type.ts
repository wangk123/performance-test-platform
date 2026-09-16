/** 场景测试类型字典：后端枚举 TestType → 中文（下拉/chip 共用；未知值原样回显）。 */

export const TEST_TYPE_LABEL: Record<string, string> = {
  BENCHMARK: '基准测试',
  SINGLE_TXN: '单交易并发',
  COMPOSITE: '混合交易',
  STABILITY: '稳定性',
};

export function testTypeLabel(value: string | null | undefined): string {
  return (value && TEST_TYPE_LABEL[value]) || value || '';
}
