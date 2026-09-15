import { describe, expect, it } from 'vitest';
import { splitSections, METHOD_SECTION_TITLE } from './plan-markdown';

describe('测试方法章节识别', () => {
  it('八、测试方法 独立成章且不被序号前缀吞并到场景设计', () => {
    const sections = splitSections('## 一、背景\nx\n## 八、测试方法\n内容\n## 九、风险与预案\ny');
    expect(sections.map(s => s.title)).toEqual(['一、背景', METHOD_SECTION_TITLE, '九、风险与预案']);
    expect(sections[1].heading).toBe('八、测试方法');
  });
  it('内置模板（无测试方法章节）不受影响', () => {
    const sections = splitSections('## 一、背景\nx\n## 八、场景设计\ny');
    expect(sections.map(s => s.title)).toEqual(['一、背景', '八、场景设计']);
  });
});
