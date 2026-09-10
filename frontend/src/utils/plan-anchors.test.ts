import { describe, expect, it } from 'vitest';
import { deriveAnchors, normalizeForMatch, similarity } from './plan-anchors';
import type { PlanComment } from '../types';

const BODY = [
  '# 计划',
  '## 三、测试指标',
  '登录接口 TPS ≥ 1000，CPU < 70%。',
  '',
  '下单接口 P95 ≤ 200ms。',
  '## 四、测试范围',
  '覆盖核心交易链路。',
].join('\n');

function root(overrides: Partial<PlanComment>): PlanComment {
  return {
    id: 1, planId: 1, author: 'reviewer', content: '批注', kind: 'REVIEW', createdAt: '',
    parentId: null, anchorLine: null, anchorText: null, sectionTitle: null, bodyRevision: null,
    resolved: false, resolvedBy: null, resolvedAt: null, canResolve: true, canDelete: true,
    ...overrides,
  };
}

describe('normalizeForMatch / similarity', () => {
  it('去空白与 Markdown 修饰符并小写', () => {
    expect(normalizeForMatch('**登录接口** TPS ≥ 1000，`CPU`')).toBe('登录接口tps≥1000cpu');
  });
  it('相同文本相似度 1，无关文本低于阈值', () => {
    expect(similarity('登录接口tps', '登录接口tps')).toBe(1);
    expect(similarity('登录接口tps', 'zzzzzzzz')).toBeLessThan(0.6);
  });
});

describe('deriveAnchors', () => {
  it('精确命中 → ok', () => {
    const map = deriveAnchors(BODY, [root({ id: 1, anchorLine: 2, anchorText: '登录接口 TPS ≥ 1000', sectionTitle: '三、测试指标' })]);
    expect(map.get(1)).toMatchObject({ state: 'ok', line: 2, sectionTitle: '三、测试指标' });
  });

  it('章标题行锚定（章级入口）→ ok', () => {
    const map = deriveAnchors(BODY, [root({ id: 6, anchorLine: 1, anchorText: '三、测试指标', sectionTitle: '三、测试指标' })]);
    expect(map.get(6)).toMatchObject({ state: 'ok', line: 1, sectionTitle: '三、测试指标' });
  });

  it('行号漂移但章内文本可匹配 → remounted 到新行', () => {
    const map = deriveAnchors(BODY, [root({ id: 2, anchorLine: 6, anchorText: '下单接口 P95 ≤ 200ms', sectionTitle: '三、测试指标' })]);
    expect(map.get(2)?.state).toBe('remounted');
    expect(map.get(2)?.line).toBe(4);
  });

  it('章内找不到、全文可找 → remounted；带新章节归属', () => {
    const map = deriveAnchors(BODY, [root({ id: 3, anchorLine: 20, anchorText: '覆盖核心交易链路', sectionTitle: '三、测试指标' })]);
    expect(map.get(3)?.state).toBe('remounted');
    expect(map.get(3)?.line).toBe(6);
  });

  it('彻底找不到 → broken，归属回原章', () => {
    const map = deriveAnchors(BODY, [root({ id: 4, anchorLine: 20, anchorText: '这段话已被删除干净', sectionTitle: '九、风险与预案' })]);
    expect(map.get(4)?.state).toBe('broken');
    expect(map.get(4)?.line).toBeNull();
    expect(map.get(4)?.sectionTitle).toBe('九、风险与预案');
  });

  it('无锚点批注不入结果', () => {
    const map = deriveAnchors(BODY, [root({ id: 5, anchorLine: null, anchorText: null, sectionTitle: null })]);
    expect(map.size).toBe(0);
  });
});

// ---- 终审 C1：表格数据行/清单项/列表项的行级锚点（anchorBlocks 与 DOM 注入同粒度） ----

const TABLE_BODY = [
  '# 计划',
  '## 五、测试资源',
  '| 角色 | 人员 |',
  '| --- | --- |',
  '| 压测执行 | 张三 |',
  '| 环境保障 | 李四 |',
].join('\n');

const CHECKLIST_BODY = [
  '# 计划',
  '## 六、测试约束',
  '### 环境准备',
  '- [ ] 准备压测数据',
  '- [x] 清理历史数据',
].join('\n');

const LIST_BODY = [
  '# 计划',
  '## 七、测试策略',
  '- 甲',
  '- 乙',
].join('\n');

describe('deriveAnchors 行级锚点（终审 C1）', () => {
  it('表格第一个数据行（第 3 源行）锚定 → ok', () => {
    const map = deriveAnchors(TABLE_BODY, [root({ id: 11, anchorLine: 4, anchorText: '压测执行张三', sectionTitle: '五、测试资源' })]);
    expect(map.get(11)).toMatchObject({ state: 'ok', line: 4, sectionTitle: '五、测试资源' });
  });

  it('清单项行（checklistItemLines 口径）锚定 → ok', () => {
    const map = deriveAnchors(CHECKLIST_BODY, [root({ id: 12, anchorLine: 3, anchorText: '准备压测数据', sectionTitle: '六、测试约束' })]);
    expect(map.get(12)).toMatchObject({ state: 'ok', line: 3, sectionTitle: '六、测试约束' });
  });

  it('列表项行锚定 → ok', () => {
    const map = deriveAnchors(LIST_BODY, [root({ id: 13, anchorLine: 2, anchorText: '甲', sectionTitle: '七、测试策略' })]);
    expect(map.get(13)).toMatchObject({ state: 'ok', line: 2, sectionTitle: '七、测试策略' });
  });

  it('长块快照截断：Dice 退化但前缀包含 → ok（终审 I3）', () => {
    // 无跨句重复的自然长文（572 归一化字符 > spec §5.3 的 ~500 退化线），快照截 200 字符
    const longBlock = [
      '登录接口在早高峰承载全站六成流量，需要单独评估容量上限。',
      '支付回调到达时间存在秒级抖动，压测期间应模拟真实的乱序与重试。',
      '库存扣减依赖数据库行级锁，热点商品的并发写会拉长事务等待。',
      '搜索联想命中缓存的概率与输入长度强相关，冷启动表现需单独观察。',
      '购物车合并逻辑在多端登录场景会触发隐式写冲突，脚本须保留校验步骤。',
      '优惠券核销链路涉及风控规则计算，响应时间呈现明显的长尾分布。',
      '消息推送的下游限流窗口为一分钟，超过阈值后会出现排队延迟。',
      '报表模块在整点触发定时聚合任务，与压测流量叠加时资源竞争显著。',
      '文件导出占用带宽较大，建议安排独立执行窗口以免干扰核心链路。',
      '权限校验每次请求访问令牌服务，缓存命中率直接影响吞吐上限。',
      '网关限流策略按租户维度生效，脚本需模拟多租户混合流量。',
      '会话续期请求集中在整点前后，容易与定时任务形成周期性尖峰。',
      '队列消费能力受制于消费者实例数，扩容后需重新验证堆积消化速度。',
      '图片压缩任务占用大量CPU，与交易接口混部时相互干扰明显。',
      '短信通道对接第三方网关，压测环境应以挡板服务代替真实外呼。',
      '风控规则命中后进入人工审核队列，该分支的转化耗时不计入同步链路。',
      '客服工单模块读写比约为九比一，缓存预热能显著降低数据库压力。',
      '订单归档任务每日凌晨执行，白天时段可视为只读背景负载。',
      '评价列表存在深度分页问题，翻到两百页之后的响应会急剧恶化。',
      '优惠券预算扣减走独立账户服务，跨服务调用放大了整体延迟。',
    ].join('');
    const body = ['# 计划', '## 三、测试指标', longBlock].join('\n');
    expect(similarity(longBlock.slice(0, 200), longBlock)).toBeLessThan(0.6); // 前置：Dice 确已退化
    const map = deriveAnchors(body, [root({ id: 14, anchorLine: 2, anchorText: longBlock.slice(0, 200), sectionTitle: '三、测试指标' })]);
    expect(map.get(14)).toMatchObject({ state: 'ok', line: 2, sectionTitle: '三、测试指标' });
  });
});
