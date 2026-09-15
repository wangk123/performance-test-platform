package com.yr.perftest.platform.task.plandoc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Markdown 原文的结构操作：章节定位/替换、场景块回填、清单解析。纯静态、无状态。 */
public final class PlanMarkdownSupport {

    /** 「测试方法」章节小节骨架（设计 §3）：标题行 + 方法说明自由区占位 + 证据指引行。 */
    static final String METHOD_NOTE_PLACEHOLDER = "**方法说明**：（自由编辑，实体同步不触碰此处）";
    static final String EVIDENCE_HINT_LINE = "> 执行记录与监控证据：见 Pretty 视图 / 报告";

    public static final List<String> CANONICAL_HEADINGS = List.of(
            "一、背景", "二、测试目的", "三、测试指标", "四、测试范围", "五、测试资源", "六、测试约束",
            "七、测试策略", "八、场景设计", "九、风险与预案", "十、排期与协作", "十一、附录", "十二、结论");

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
            if (scenarioDesignSectionBounds(body) == null && testMethodSectionBounds(body) != null) {
                return body; // 「测试方法」章节文档：执行记录为引用式（设计 §2 D2），不写入 body
            }
            String generated = "### S? " + scenarioName + " · UNKNOWN\n\n**场景目的**：（待补充）\n\n"
                    + EXECUTION_RECORD_HEADING + "\n";
            String withBlock = ensureSection(body, "八、场景设计", generated);
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
        int[] bounds = scenarioDesignSectionBounds(body);
        String block = generatedBlock + "\n" + EXECUTION_RECORD_HEADING + "\n";
        if (bounds == null) {
            if (testMethodSectionBounds(body) != null) {
                return body; // 「测试方法」章节文档：场景小节由骨架同步维护，不落地场景设计块
            }
            return ensureSection(body, "八、场景设计", "\n" + block);
        }
        String normalized = block.endsWith("\n") ? block : block + "\n";
        return body.substring(0, bounds[0]) + body.substring(bounds[0], bounds[1]) + normalized + body.substring(bounds[1]);
    }

    /**
     * 「测试方法」章节骨架回写（设计 §3）：按入参顺序（= sortOrder）维护章节内 `### ` 场景小节。
     * 已存在小节（标题行含场景名）仅以骨架标题行替换原标题，`**方法说明**：` 行至块尾的用户自由区逐字保留，
     * 标题与自由区之间的内容不保留；缺失小节以骨架追加在章节导语之后；未匹配任何场景的既有 `### `
     * 小节原样保留在已匹配小节之后。章节不存在 → body 原样返回。
     */
    public static String upsertTestMethodSkeletons(String body, LinkedHashMap<String, String> skeletonByScenarioName) {
        int[] section = testMethodSectionBounds(body);
        if (section == null) {
            return body;
        }
        List<int[]> ranges = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        int lineStart = 0;
        int blockStart = -1;
        for (String line : body.substring(section[0], section[1]).split("\n", -1)) {
            if (line.startsWith("### ")) {
                if (blockStart >= 0) {
                    ranges.add(new int[]{blockStart, section[0] + lineStart});
                    texts.add(body.substring(blockStart, section[0] + lineStart));
                }
                blockStart = section[0] + lineStart;
            }
            lineStart += line.length() + 1;
        }
        if (blockStart >= 0) {
            ranges.add(new int[]{blockStart, section[1]});
            texts.add(body.substring(blockStart, section[1]));
        }
        StringBuilder out = new StringBuilder(body.substring(0, ranges.isEmpty() ? section[1] : ranges.get(0)[0])); // 导语
        boolean[] consumed = new boolean[ranges.size()];
        for (var entry : skeletonByScenarioName.entrySet()) {
            int matched = -1;
            for (int i = 0; i < ranges.size() && matched < 0; i++) {
                if (!consumed[i] && firstLine(texts.get(i)).contains(entry.getKey())) {
                    matched = i;
                }
            }
            if (matched < 0) {
                out.append(entry.getValue());
            } else {
                consumed[matched] = true;
                out.append(firstLine(entry.getValue())).append('\n').append(methodNoteRegion(texts.get(matched)));
            }
        }
        for (int i = 0; i < ranges.size(); i++) {
            if (!consumed[i]) {
                out.append(texts.get(i)); // 与实体无关的手写小节原样保留
            }
        }
        return out.append(body.substring(section[1])).toString();
    }

    /** 移除「测试方法」章节内标题行含场景名的 `### ` 小节；章节或小节缺失 → body 原样返回。 */
    public static String removeTestMethodSkeleton(String body, String scenarioName) {
        int[] section = testMethodSectionBounds(body);
        if (section == null) {
            return body;
        }
        int lineStart = 0;
        int blockStart = -1;
        for (String line : body.substring(section[0], section[1]).split("\n", -1)) {
            if (line.startsWith("### ")) {
                if (blockStart >= 0) {
                    return body.substring(0, blockStart) + body.substring(section[0] + lineStart);
                }
                if (line.contains(scenarioName)) {
                    blockStart = section[0] + lineStart;
                }
            }
            lineStart += line.length() + 1;
        }
        return blockStart < 0 ? body : body.substring(0, blockStart) + body.substring(section[1]);
    }

    /**
     * 「测试方法」章节定位：`## ` 标题行以「测试方法」结尾（如「## 八、测试方法」，自定义模板序号不限）。
     * 返回 [contentStart, contentEnd)，章节缺失返回 null。不依赖规范标题表——「测试方法」不在
     * CANONICAL_HEADINGS 中，序号前缀容错会把「## 八、测试方法」误吞为「八、场景设计」。
     */
    public static int[] testMethodSectionBounds(String body) {
        if (body == null) {
            return null;
        }
        String[] lines = body.split("\n", -1);
        int lineStart = 0;
        int contentStart = -1;
        for (String line : lines) {
            if (contentStart < 0) {
                if (line.startsWith("## ") && line.substring(3).trim().endsWith("测试方法")) {
                    contentStart = lineStart + line.length() + 1;
                }
            } else if (line.startsWith("## ")) {
                return new int[]{contentStart, lineStart};
            }
            lineStart += line.length() + 1;
        }
        return contentStart < 0 ? null : new int[]{contentStart, body.length()};
    }

    /** 小节内从 `**方法说明**：` 行到块尾的自由区；无该标记行时返回标准占位自由区。 */
    private static String methodNoteRegion(String blockText) {
        int lineStart = 0;
        for (String line : blockText.split("\n", -1)) {
            if (line.startsWith("**方法说明**")) {
                return blockText.substring(lineStart);
            }
            lineStart += line.length() + 1;
        }
        return METHOD_NOTE_PLACEHOLDER + "\n" + EVIDENCE_HINT_LINE + "\n";
    }

    /** 场景设计章节定位：命中「八、场景设计」且原始标题非「测试方法」结尾（排除序号容错误吞）。 */
    private static int[] scenarioDesignSectionBounds(String body) {
        if (body == null) {
            return null;
        }
        String[] lines = body.split("\n", -1);
        int lineStart = 0;
        int contentStart = -1;
        for (String line : lines) {
            if (contentStart < 0) {
                if (isScenarioDesignHeading(line)) {
                    contentStart = lineStart + line.length() + 1;
                }
            } else if (line.startsWith("## ")) {
                return new int[]{contentStart, lineStart};
            }
            lineStart += line.length() + 1;
        }
        return contentStart < 0 ? null : new int[]{contentStart, body.length()};
    }

    private static boolean isScenarioDesignHeading(String line) {
        String title = canonicalTitleOf(line);
        return title != null && title.equals("八、场景设计") && !line.substring(3).trim().endsWith("测试方法");
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

    /** 场景块 = 场景设计章节内以 `### ` 开头且包含场景名的行，到下一 `### `/`## ` 或文末。 */
    private static int[] scenarioBlockBounds(String body, String scenarioName) {
        int[] section = scenarioDesignSectionBounds(body);
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
            if (!numeral.equals("十二、") && text.startsWith(numeral)) {
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
