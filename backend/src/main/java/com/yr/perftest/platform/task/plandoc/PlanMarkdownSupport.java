package com.yr.perftest.platform.task.plandoc;

import java.util.ArrayList;
import java.util.List;

/** Markdown 原文的结构操作：章节定位/替换、场景块回填、清单解析。纯静态、无状态。 */
public final class PlanMarkdownSupport {

    public static final List<String> CANONICAL_HEADINGS = List.of(
            "一、背景", "二、测试目的与指标", "三、测试范围", "四、测试资源", "五、测试约束",
            "六、测试策略", "七、场景设计", "八、风险与预案", "九、排期与协作", "十、附录", "十一、结论");

    private static final String EXECUTION_RECORD_HEADING = "#### 执行记录";

    private PlanMarkdownSupport() {
    }

    public record Section(String title, String content) {
    }

    public static List<Section> splitSections(String body) {
        List<Section> sections = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return sections;
        }
        String[] lines = body.split("\n", -1);
        String currentTitle = null;
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            String title = canonicalTitleOf(line);
            if (title != null) {
                if (currentTitle != null) {
                    sections.add(new Section(currentTitle, current.toString()));
                }
                currentTitle = title;
                current = new StringBuilder();
            } else if (currentTitle != null) {
                current.append(line).append('\n');
            }
        }
        if (currentTitle != null) {
            sections.add(new Section(currentTitle, current.toString()));
        }
        return sections;
    }

    public static String extractSection(String body, String title) {
        int[] bounds = sectionBounds(body, title);
        if (bounds == null) {
            return null;
        }
        return body.substring(bounds[0], bounds[1]);
    }

    public static String replaceSection(String body, String title, String newContent) {
        int[] bounds = sectionBounds(body, title);
        if (bounds == null) {
            throw new PlanValidationException("PLAN_INVALID：文档缺少章节「" + title + "」，无法写回");
        }
        String normalized = newContent == null ? "\n" : (newContent.endsWith("\n") ? newContent : newContent + "\n");
        return body.substring(0, bounds[0]) + normalized + body.substring(bounds[1]);
    }

    public static String ensureSection(String body, String title, String content) {
        if (sectionBounds(body, title) != null) {
            return replaceSection(body, title, content);
        }
        String base = body == null ? "" : body;
        String heading = "\n## " + title + "\n" + (content == null ? "\n" : content.endsWith("\n") ? content : content + "\n");
        int insertAt = canonicalInsertIndex(base, title);
        if (insertAt < 0) {
            return base + heading;
        }
        return base.substring(0, insertAt) + heading.trim() + "\n" + base.substring(insertAt);
    }

    public static String appendExecutionRecord(String body, String scenarioName, long executionId, String entryLine) {
        String marker = "<!-- backfill:execution:" + executionId + " -->";
        int[] block = scenarioBlockBounds(body, scenarioName);
        if (block == null) {
            String generated = "### S? " + scenarioName + " · UNKNOWN\n\n**场景目的**：（待补充）\n\n"
                    + EXECUTION_RECORD_HEADING + "\n";
            String withBlock = ensureSection(body, "七、场景设计", generated);
            block = scenarioBlockBounds(withBlock, scenarioName);
            body = withBlock;
        }
        String blockText = body.substring(block[0], block[1]);
        if (blockText.contains(marker)) {
            return body; // 幂等：场景块内查重（设计 §8.1）
        }
        int recordHeading = blockText.indexOf(EXECUTION_RECORD_HEADING);
        if (recordHeading < 0) {
            String before = body.substring(0, block[1]);
            String after = body.substring(block[1]);
            return before + EXECUTION_RECORD_HEADING + "\n" + marker + "\n" + entryLine + "\n" + after;
        }
        int insertAt = block[0] + blockText.length(); // 追加到块尾（即执行记录小节末尾）
        return body.substring(0, insertAt) + marker + "\n" + entryLine + "\n" + body.substring(insertAt);
    }

    public static List<String> parseExecutionRecords(String body, String scenarioName) {
        int[] block = scenarioBlockBounds(body, scenarioName);
        if (block == null) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (String line : body.substring(block[0], block[1]).split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ") && !trimmed.startsWith("- [ ]") && !trimmed.startsWith("- [x]")) {
                entries.add(trimmed.substring(2));
            }
        }
        return entries;
    }

    public static String renderTemplate(String templateMarkdown, String planName) {
        if (templateMarkdown == null) {
            return null;
        }
        return templateMarkdown.replace("{{planName}}", planName == null ? "" : planName);
    }

    public static List<String> parseChecklistItems(String sectionContent) {
        List<String> items = new ArrayList<>();
        if (sectionContent == null) {
            return items;
        }
        for (String line : sectionContent.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ")) {
                items.add(trimmed.substring(6).trim());
            }
        }
        return items;
    }

    public static String replaceScenarioBusinessBlock(String body, String scenarioName, String generatedBlock) {
        int[] block = scenarioBlockBounds(body, scenarioName);
        if (block == null) {
            return appendExecutionlessBlock(body, generatedBlock);
        }
        String blockText = body.substring(block[0], block[1]);
        int recordHeading = blockText.indexOf(EXECUTION_RECORD_HEADING);
        if (recordHeading < 0) {
            return body.substring(0, block[0]) + generatedBlock + body.substring(block[1]);
        }
        String preserved = blockText.substring(recordHeading); // #### 执行记录 及其后内容原样保留
        return body.substring(0, block[0]) + generatedBlock + preserved + body.substring(block[1]);
    }

    /**
     * 场景事实回写：只替换 标题行 / 场景目的行 / 场景设置表 三处实体事实，
     * 保留两处之间的自由文本（测试方法/交易范围）与执行记录小节（设计 §3.1"场景章节由实体渲染生成并随实体变更回写"）。
     * 块缺失 → 追加新块；块内缺少约定标记 → 兜底整块替换业务部分。
     */
    public static String upsertScenarioFacts(String body, String scenarioName, String generatedBlock) {
        int[] block = scenarioBlockBounds(body, scenarioName);
        if (block == null) {
            return appendExecutionlessBlock(body, generatedBlock);
        }
        String blockText = body.substring(block[0], block[1]);
        String genHeading = firstLine(generatedBlock);
        String genPurpose = markerLine(generatedBlock, "**场景目的**");
        String genSettings = settingsRegion(generatedBlock);
        if (genPurpose == null || genSettings == null
                || !blockText.contains("**场景目的**") || !blockText.contains("**场景设置**")) {
            return replaceScenarioBusinessBlock(body, scenarioName, generatedBlock);
        }
        StringBuilder out = new StringBuilder();
        boolean inSettings = false;
        for (String line : blockText.split("\n", -1)) {
            if (line.startsWith("### ")) {
                out.append(genHeading).append('\n');
            } else if (line.startsWith("**场景目的**")) {
                out.append(genPurpose).append('\n');
            } else if (line.startsWith("**场景设置**")) {
                out.append(genSettings);
                if (!genSettings.endsWith("\n")) {
                    out.append('\n');
                }
                inSettings = true;
            } else if (line.startsWith("#### 执行记录")) {
                inSettings = false;
                out.append(line).append('\n');
            } else if (!inSettings) {
                out.append(line).append('\n'); // 自由文本与执行记录内容原样保留
            }
        }
        String rebuilt = out.toString().stripTrailing();
        return body.substring(0, block[0]) + rebuilt + "\n" + body.substring(block[1]);
    }

    public static String removeScenarioBlock(String body, String scenarioName) {
        int[] block = scenarioBlockBounds(body, scenarioName);
        if (block == null) {
            return body;
        }
        return body.substring(0, block[0]) + body.substring(block[1]);
    }

    private static String firstLine(String text) {
        int i = text.indexOf('\n');
        return i < 0 ? text : text.substring(0, i);
    }

    private static String markerLine(String text, String marker) {
        for (String line : text.split("\n", -1)) {
            if (line.startsWith(marker)) {
                return line;
            }
        }
        return null;
    }

    /** generatedBlock 中从 **场景设置** 标记行到块尾的区域。 */
    private static String settingsRegion(String generatedBlock) {
        int i = generatedBlock.indexOf("**场景设置**");
        return i < 0 ? null : generatedBlock.substring(i);
    }

    private static String appendExecutionlessBlock(String body, String generatedBlock) {
        String section = extractSection(body, "七、场景设计");
        String block = generatedBlock + "\n" + EXECUTION_RECORD_HEADING + "\n";
        if (section == null) {
            return ensureSection(body, "七、场景设计", "\n" + block);
        }
        return replaceSection(body, "七、场景设计", section + block);
    }

    /** 返回 [contentStart, contentEnd)：标题行之后到下一 `## ` 标题行之前。 */
    private static int[] sectionBounds(String body, String title) {
        if (body == null) {
            return null;
        }
        String[] lines = body.split("\n", -1);
        int lineStart = 0;
        int contentStart = -1;
        for (String line : lines) {
            if (contentStart < 0) {
                if (canonicalTitleOf(line) != null && canonicalTitleOf(line).equals(title)) {
                    contentStart = lineStart + line.length() + 1;
                }
            } else if (line.startsWith("## ")) {
                return new int[]{contentStart, lineStart};
            }
            lineStart += line.length() + 1;
        }
        return contentStart < 0 ? null : new int[]{contentStart, body.length()};
    }

    /** 场景块 = 七章节内以 `### ` 开头且包含场景名的行，到下一 `### `/`## ` 或文末。 */
    private static int[] scenarioBlockBounds(String body, String scenarioName) {
        int[] section = sectionBounds(body, "七、场景设计");
        if (section == null) {
            return null;
        }
        String sectionText = body.substring(section[0], section[1]);
        int offset = section[0];
        String[] lines = sectionText.split("\n", -1);
        int lineStart = 0;
        int blockStart = -1;
        for (String line : lines) {
            if (line.startsWith("### ")) {
                if (blockStart >= 0) {
                    return new int[]{blockStart, offset + lineStart};
                }
                if (line.contains(scenarioName)) {
                    blockStart = offset + lineStart;
                }
            }
            lineStart += line.length() + 1;
        }
        return blockStart < 0 ? null : new int[]{blockStart, section[1]};
    }

    /** `## 二、测试目的与指标` → `二、测试目的与指标`；非规范标题返回 null。 */
    private static String canonicalTitleOf(String line) {
        if (line == null || !line.startsWith("## ")) {
            return null;
        }
        String text = line.substring(3).trim();
        for (String heading : CANONICAL_HEADINGS) {
            if (text.equals(heading) || (text.startsWith(heading) && text.length() > heading.length()
                    && isSeparator(text.charAt(heading.length())))) {
                return heading;
            }
            // 容错：仅序号前缀匹配（如「## 二、xxx」改名场景），按序号取第一个规范标题
            String numeral = heading.substring(0, heading.indexOf('、') + 1);
            if (!numeral.equals("十一、") && text.startsWith(numeral)) {
                return heading;
            }
        }
        return null;
    }

    private static boolean isSeparator(char c) {
        return c == ' ' || c == '　' || c == '：' || c == ':' || c == '-';
    }

    private static int canonicalInsertIndex(String body, String title) {
        int target = CANONICAL_HEADINGS.indexOf(title);
        for (int i = target + 1; i < CANONICAL_HEADINGS.size(); i++) {
            int[] bounds = sectionBounds(body, CANONICAL_HEADINGS.get(i));
            if (bounds != null) {
                return body.lastIndexOf("## " + CANONICAL_HEADINGS.get(i));
            }
        }
        return -1;
    }
}
