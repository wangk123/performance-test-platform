package com.yr.perftest.platform.task.plandoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「二、测试目的与指标」章节表格解析器（spec §3）：保存校验与报告判等共用，
 * 纯静态、无状态、不落库。格式非法抛 PLAN_INVALID；章节缺失/无表格/空表 → 无指标路径。
 */
public final class PlanAcceptanceParser {

    public enum MetricType { TPS, AVG_RT, P95, P99, ERROR_RATE, PEAK_CONCURRENCY, CAPACITY, OTHER }

    /** lineNumber 为数据行序号（1 基，仅数据行）；targetValue 对不可自动判类型为 null。 */
    public record AcceptanceMetricRow(
            int lineNumber, String objectName, String metricRaw, MetricType metricType,
            String targetRaw, Double targetValue) {
    }

    public record AcceptanceSection(boolean present, List<AcceptanceMetricRow> rows) {
        public static final AcceptanceSection EMPTY = new AcceptanceSection(false, List.of());
    }

    /** trim + 大小写不敏感的别名映射（spec §3.4）。 */
    private static final Map<String, MetricType> ALIASES = Map.ofEntries(
            Map.entry("tps", MetricType.TPS),
            Map.entry("吞吐量", MetricType.TPS),
            Map.entry("throughput", MetricType.TPS),
            Map.entry("平均rt", MetricType.AVG_RT),
            Map.entry("响应时间", MetricType.AVG_RT),
            Map.entry("p95", MetricType.P95),
            Map.entry("p95响应时间", MetricType.P95),
            Map.entry("p99", MetricType.P99),
            Map.entry("错误率", MetricType.ERROR_RATE),
            Map.entry("并发峰值", MetricType.PEAK_CONCURRENCY),
            Map.entry("容量", MetricType.CAPACITY));

    private static final String OBJECT_COLUMN = "对象";
    private static final String LEGACY_OBJECT_COLUMN = "交易"; // P0-1 存量文档表头，向后兼容（spec §3.3）
    private static final String METRIC_COLUMN = "指标";
    private static final String TARGET_COLUMN = "目标值";

    private PlanAcceptanceParser() {
    }

    /** 报告期兜底（spec §3.2）：存量文档解析失败不阻断，返回无指标节（调用方以 prefill 文本标注原因）。 */
    public static AcceptanceSection parseLeniently(String body) {
        try {
            return parse(body);
        } catch (PlanValidationException exception) {
            return AcceptanceSection.EMPTY;
        }
    }

    public static AcceptanceSection parse(String body) {
        String section = PlanMarkdownSupport.extractSection(body, "二、测试目的与指标");
        if (section == null) {
            return AcceptanceSection.EMPTY;
        }
        List<String[]> table = firstTableOf(section);
        if (table.isEmpty()) {
            return AcceptanceSection.EMPTY;
        }
        String[] header = table.get(0);
        int objectCol = columnOf(header, OBJECT_COLUMN, LEGACY_OBJECT_COLUMN);
        int metricCol = columnOf(header, METRIC_COLUMN);
        int targetCol = columnOf(header, TARGET_COLUMN);
        List<String> missing = new ArrayList<>();
        if (objectCol < 0) {
            missing.add(OBJECT_COLUMN);
        }
        if (metricCol < 0) {
            missing.add(METRIC_COLUMN);
        }
        if (targetCol < 0) {
            missing.add(TARGET_COLUMN);
        }
        if (!missing.isEmpty()) {
            throw new PlanValidationException("PLAN_INVALID：指标表表头缺少列：" + String.join("、", missing));
        }
        List<AcceptanceMetricRow> rows = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) { // i=0 是表头；table 已剔除分隔线行
            String[] cells = table.get(i);
            if (cells.length < header.length) {
                throw new PlanValidationException("PLAN_INVALID：指标表第 " + i + " 行单元格数不足（需 "
                        + header.length + " 列，实际 " + cells.length + " 列）");
            }
            String objectName = cells[objectCol].trim();
            String metricRaw = cells[metricCol].trim();
            String targetRaw = cells[targetCol].trim();
            MetricType type = resolveType(metricRaw);
            Double targetValue = parseTargetValue(type, targetRaw, i);
            rows.add(new AcceptanceMetricRow(i, objectName, metricRaw, type, targetRaw, targetValue));
        }
        return rows.isEmpty() ? AcceptanceSection.EMPTY : new AcceptanceSection(true, List.copyOf(rows));
    }

    /** 章节内第一个 Markdown 表：表头行 + 数据行（剔除 |---| 分隔线）；无表返回空。 */
    private static List<String[]> firstTableOf(String section) {
        List<String[]> rows = new ArrayList<>();
        for (String line : section.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() > 1) {
                String[] cells = splitCells(trimmed);
                if (isSeparatorRow(cells)) {
                    continue;
                }
                rows.add(cells);
            } else if (!rows.isEmpty()) {
                break; // 表格中断
            }
        }
        return rows;
    }

    private static String[] splitCells(String row) {
        String inner = row.substring(1, row.length() - 1);
        return inner.split("\\|", -1);
    }

    private static boolean isSeparatorRow(String[] cells) {
        boolean any = false;
        for (String cell : cells) {
            String c = cell.trim();
            if (c.isEmpty()) {
                continue;
            }
            if (!c.matches(":?-{2,}:?")) {
                return false;
            }
            any = true;
        }
        return any;
    }

    private static int columnOf(String[] header, String... names) {
        for (int i = 0; i < header.length; i++) {
            for (String name : names) {
                if (header[i].trim().equalsIgnoreCase(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static MetricType resolveType(String metricRaw) {
        MetricType type = ALIASES.get(metricRaw.trim().toLowerCase(Locale.ROOT));
        return type == null ? MetricType.OTHER : type;
    }

    /** 已知可判类型剥方向符与单位后须为数字（spec §3.1 第 4 行）；其余类型不解析数值。 */
    private static Double parseTargetValue(MetricType type, String targetRaw, int lineNumber) {
        if (type != MetricType.TPS && type != MetricType.AVG_RT && type != MetricType.P95
                && type != MetricType.P99 && type != MetricType.ERROR_RATE) {
            return null;
        }
        String stripped = targetRaw
                .replaceFirst("^[≥≤><=]{1,2}", "")
                .replaceFirst("^(>=|<=)", "")
                .trim()
                .replaceFirst("(?i)(ms|毫秒|%|req/s|r/s|次/s|次/秒|笔/s|笔/秒)$", "")
                .trim();
        try {
            return Double.parseDouble(stripped);
        } catch (NumberFormatException exception) {
            throw new PlanValidationException("PLAN_INVALID：指标表第 " + lineNumber
                    + " 行目标值「" + targetRaw + "」非数字（指标 " + type + " 需可解析数值目标）");
        }
    }
}
