package com.yr.perftest.platform.envcheck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/** 解析计划文档「测试资源 → 环境部署信息」表为目标机清单（spec E3）。 */
public class EnvTargetParser {

    private static final String SECTION_KEYWORD = "测试资源";
    private static final String SUBSECTION_HEADING = "### 环境部署信息";

    /** 取「测试资源」章节（`## ` 标题含「测试资源」，序号不限以兼容模板与自定义文档）；缺失返回空表。 */
    public List<TargetHost> parse(String body) {
        String section = resourceSection(body);
        if (section == null) {
            return List.of();
        }
        int start = section.indexOf(SUBSECTION_HEADING);
        if (start < 0) {
            return List.of();
        }
        String block = section.substring(start);
        int next = block.indexOf("\n### ", 1);
        if (next >= 0) {
            block = block.substring(0, next);
        }
        LinkedHashMap<String, TargetHost> byHost = new LinkedHashMap<>();
        for (String line : block.split("\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|") || trimmed.contains("---")) {
                continue;
            }
            String[] cells = Arrays.stream(trimmed.substring(1).split("\\|")).map(String::trim).toArray(String[]::new);
            if (cells.length < 3 || cells[0].isEmpty() || cells[0].equals("地址")) {
                continue;
            }
            byHost.merge(cells[0], new TargetHost(cells[0], cells[1]), EnvTargetParser::keepFirstNonBlankModule);
        }
        return new ArrayList<>(byHost.values());
    }

    /** `## 测试资源` 章节内容（标题行后到下一 `## ` 行前）；缺失返回 null。 */
    private static String resourceSection(String body) {
        if (body == null) {
            return null;
        }
        StringBuilder section = new StringBuilder();
        boolean inside = false;
        for (String line : body.split("\n", -1)) {
            if (line.startsWith("## ")) {
                if (inside) {
                    break;
                }
                inside = line.substring(3).trim().contains(SECTION_KEYWORD);
            } else if (inside) {
                section.append(line).append('\n');
            }
        }
        return inside ? section.toString() : null;
    }

    private static TargetHost keepFirstNonBlankModule(TargetHost first, TargetHost duplicate) {
        if ((first.module() == null || first.module().isBlank()) && !duplicate.module().isBlank()) {
            return duplicate;
        }
        return first;
    }
}
