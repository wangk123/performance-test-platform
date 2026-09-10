/**
 * 计划文档「表格型章节」结构化编辑的列 schema 注册表。
 * 列名固定（表单表头不可改），行可增删；保存时序列化回 markdown 表格。
 */

export interface PlanTableColumnSchema {
  label: string;
  placeholder?: string;
  /** 弹窗表单栅格的弹性宽度（flex fr 值），默认 1。 */
  grow?: number;
  /** 存量表格的表头别名（如指标章旧「交易」列）。 */
  aliases?: string[];
}

export interface PlanTableSectionSchema {
  columns: PlanTableColumnSchema[];
  addLabel: string;
  hint?: string;
  /**
   * 保存时强制以规范列名重写表头。仅「三、测试指标」需要：
   * 后端 PlanAcceptanceParser 按列名（对象/指标/目标值）解析达成判定。
   * 其余章节保存时保留文档里原有表头（如排期表的「阶段/日期/内容/责任方」）。
   */
  canonicalHeader?: boolean;
}

export const PLAN_TABLE_SECTION_SCHEMAS: Record<string, PlanTableSectionSchema> = {
  '三、测试指标': {
    columns: [
      { label: '对象', placeholder: '如：查询交易', grow: 1.15, aliases: ['交易'] },
      { label: '指标', placeholder: 'TPS / P95 / 错误率…', grow: 1.05 },
      { label: '目标值', placeholder: '如：≥ 300 ms', grow: 0.85 },
      { label: '口径', placeholder: '如：5 分钟均值', grow: 1.25 },
    ],
    addLabel: '+ 添加指标',
    canonicalHeader: true,
    hint: '指标建议使用平台已知别名（TPS / 吞吐量 / 平均RT / P95 / P99 / 错误率 / 并发峰值 / 容量），已知别名参与报告自动达成判定，目标值须为可解析数值；列名固定，行可增删。',
  },
  '五、测试资源': {
    columns: [
      { label: '资源', placeholder: '如：压测执行机', grow: 1.05 },
      { label: '规格', placeholder: '如：8C16G 云主机', grow: 1.25 },
      { label: '数量', placeholder: '如：3 台', grow: 0.65 },
      { label: '用途', placeholder: '如：JMeter 注入节点', grow: 1.35 },
    ],
    addLabel: '+ 添加资源',
  },
  '九、风险与预案': {
    columns: [
      { label: '风险', placeholder: '如：环境配置低于生产', grow: 1.15 },
      { label: '影响', placeholder: '如：结论失真', grow: 0.95 },
      { label: '预案', placeholder: '如：登记差异并按容量外推', grow: 1.45 },
    ],
    addLabel: '+ 添加风险',
  },
  '十、排期与协作': {
    columns: [
      { label: '阶段', placeholder: '如：准备期', grow: 0.9 },
      { label: '日期', placeholder: '如：09-09 ~ 09-10', grow: 0.95 },
      { label: '内容', placeholder: '如：环境就绪、脚本开发', grow: 1.45 },
      { label: '责任方', placeholder: '如：测试组', grow: 0.9 },
    ],
    addLabel: '+ 添加阶段',
  },
};

export function planTableSchemaOf(sectionTitle: string): PlanTableSectionSchema | null {
  return PLAN_TABLE_SECTION_SCHEMAS[sectionTitle] ?? null;
}

/**
 * 结构化编辑接管兼容性：章节无表 → 以规范列表头空表起步；
 * 有表 → 列数一致，或表头名（含别名）命中 ≥ 2 列才接管，
 * 否则（如模板「五、测试资源」的首表是「人员：角色/姓名/职责」）回落 Markdown 编辑，避免错位改写。
 */
export function planTableCompatible(schema: PlanTableSectionSchema, header: string[] | null | undefined): boolean {
  if (!header || header.length === 0) return true;
  if (header.length === schema.columns.length) return true;
  const names = new Set(schema.columns.flatMap((col) => [col.label, ...(col.aliases ?? [])]));
  const hits = header.filter((cell) => names.has(cell.trim())).length;
  return hits >= 2;
}
