import { describe, expect, it } from 'vitest';
import type { EnvCheckItemMeta } from '../../types';
import {
  groupEnvCheckItems,
  kindChip,
  parsePrecheckSettings,
  riskChip,
  selectedCountText,
} from './envCheckSettings';

/** 非挂载纯逻辑测试：项目无 @vue/test-utils（约束不新增依赖），分组/解析逻辑抽 utils 后直接断言。 */
const item = (overrides: Partial<EnvCheckItemMeta> = {}): EnvCheckItemMeta => ({
  key: 'doc.metrics-defined',
  label: '指标已定义',
  description: '文档「测试指标」章节含指标表',
  category: 'DOC',
  kind: 'LOCAL',
  appliesTo: [],
  sortOrder: 1,
  fixable: false,
  risk: null,
  ...overrides,
});

const REGISTRY: EnvCheckItemMeta[] = [
  item({ key: 'doc.metrics-defined', sortOrder: 1 }),
  item({ key: 'os.ulimit', label: '文件句柄数', category: 'OS', kind: 'REMOTE', sortOrder: 1, fixable: true, risk: 'MEDIUM' }),
  item({ key: 'os.disk-usage', label: '磁盘空间水位', category: 'OS', kind: 'REMOTE', sortOrder: 2, risk: 'LOW' }),
  item({ key: 'jvm.discovery', label: 'JVM 进程发现', category: 'JVM', kind: 'REMOTE', sortOrder: 1, risk: 'HIGH' }),
];

describe('groupEnvCheckItems（设置清单数据驱动分组）', () => {
  it('按 category 分组，标题取注册表 category，组内按 sortOrder 排序', () => {
    const groups = groupEnvCheckItems(REGISTRY);
    expect(groups.map((group) => group.categoryLabel)).toEqual(['文档核验', '操作系统', 'JVM / 中间件']);
    expect(groups[1].items.map((entry) => entry.key)).toEqual(['os.ulimit', 'os.disk-usage']);
    expect(groups[1].kind).toBe('REMOTE');
  });

  it('完全数据驱动：注册表新增任意 category/项自动成组，无前端枚举', () => {
    const groups = groupEnvCheckItems([...REGISTRY, item({ key: 'net.probe', label: '端口拨测', category: 'NET' as never })]);
    expect(groups).toHaveLength(4);
    expect(groups[3].categoryLabel).toBe('NET'); // 未知类别回退原值展示
    expect(groups[3].items[0].label).toBe('端口拨测');
  });

  it('组间顺序与组内计数标签', () => {
    const groups = groupEnvCheckItems(REGISTRY);
    expect(selectedCountText(groups[1].items, ['os.ulimit'])).toBe('1 / 2 已选');
  });
});

describe('riskChip / kindChip（行尾标识）', () => {
  it('风险 chip 三级色与可修后缀', () => {
    expect(riskChip(item({ risk: 'MEDIUM', fixable: true }))).toEqual({ level: 'medium', text: '中风险 · 可修' });
    expect(riskChip(item({ risk: 'LOW', fixable: false }))).toEqual({ level: 'low', text: '低风险 · 提示' });
    expect(riskChip(item({ risk: 'HIGH', fixable: false }))).toEqual({ level: 'high', text: '高风险 · 建议' });
    expect(riskChip(item({ risk: 'HIGH', fixable: true }))).toEqual({ level: 'high', text: '高风险 · 可修' });
  });

  it('无 risk 项回退类型 chip（LOCAL 项 risk 为 null）', () => {
    expect(riskChip(item())).toBeNull();
    expect(kindChip(item())).toBe('平台内');
    expect(kindChip(item({ kind: 'REMOTE' }))).toBe('平台内拨测');
  });
});

describe('parsePrecheckSettings（precheckJson 初始化勾选态）', () => {
  it('解析 enabled 与选中 key，并过滤注册表外 key', () => {
    const parsed = parsePrecheckSettings('{"enabled":true,"items":["doc.metrics-defined","ghost.key"]}', REGISTRY.map((entry) => entry.key));
    expect(parsed).toEqual({ enabled: true, items: ['doc.metrics-defined'] });
  });

  it('null / 非法 JSON → 未启用且未勾选', () => {
    expect(parsePrecheckSettings(null, ['a'])).toEqual({ enabled: false, items: [] });
    expect(parsePrecheckSettings('not-json', ['a'])).toEqual({ enabled: false, items: [] });
  });
});
