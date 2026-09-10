package com.yr.perftest.platform.task.plandoc;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存量计划文档「二、测试目的与指标」拆分迁移（11 章 → 12 章）：启动时对 task_plans.body
 * 做一次性行内重命名与章节拆分。属机械性结构改名，不走领域保存（不 bump revision、
 * 不动 updatedAt），用 JdbcTemplate 直改；plan_publish_snapshots 的历史快照按设计保持冻结。
 * 幂等：仅改写仍含旧规范标题精确行的 body，新结构文档空转跳过。
 */
@Component
public class PlanSectionSplitMigrator implements ApplicationRunner {

    /** 旧规范标题精确行 → 新规范标题（拆分章「二、测试目的与指标」→「二、测试目的」由 split 处理表格外，先改名）。 */
    private static final Map<String, String> OLD_TO_NEW = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("二、测试目的与指标", "二、测试目的"),
            Map.entry("三、测试范围", "四、测试范围"),
            Map.entry("四、测试资源", "五、测试资源"),
            Map.entry("五、测试约束", "六、测试约束"),
            Map.entry("六、测试策略", "七、测试策略"),
            Map.entry("七、场景设计", "八、场景设计"),
            Map.entry("八、风险与预案", "九、风险与预案"),
            Map.entry("九、排期与协作", "十、排期与协作"),
            Map.entry("十、附录", "十一、附录"),
            Map.entry("十一、结论", "十二、结论")
    ));

    static final String OLD_METRIC_HEADING = "## 二、测试目的与指标";
    static final String NEW_METRIC_HEADING = "## 三、测试指标";

    private final JdbcTemplate jdbcTemplate;

    public PlanSectionSplitMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM task_plans", Long.class);
        for (Long id : ids) {
            String body = jdbcTemplate.queryForObject("SELECT body FROM task_plans WHERE id = ?", String.class, id);
            String migrated = migrate(body);
            if (migrated != null) {
                jdbcTemplate.update("UPDATE task_plans SET body = ? WHERE id = ?", migrated, id);
            }
        }
    }

    /** 返回迁移后全文；无旧结构标记时返回 null（无需改写）。包内可见便于单测。 */
    static String migrate(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        List<String> lines = new ArrayList<>(List.of(body.split("\n", -1)));
        boolean changed = false;
        boolean splitMetricSection = false;
        int metricSectionIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            String title = oldHeadingOf(lines.get(i));
            if (title != null) {
                lines.set(i, "## " + OLD_TO_NEW.get(title));
                changed = true;
                if (title.equals("二、测试目的与指标")) {
                    splitMetricSection = true;
                    metricSectionIndex = i;
                }
            }
        }
        if (!changed) {
            return null;
        }
        if (splitMetricSection) {
            insertMetricHeading(lines, metricSectionIndex);
        }
        return String.join("\n", lines);
    }

    /** 行匹配旧规范标题精确文本（`## 二、测试目的与指标`）时返回旧标题，否则 null。 */
    private static String oldHeadingOf(String line) {
        if (line == null || !line.startsWith("## ")) {
            return null;
        }
        String text = line.substring(3).trim();
        return OLD_TO_NEW.containsKey(text) ? text : null;
    }

    /**
     * 在「二、测试目的」节内拆出「三、测试指标」：定位节内第一个表格行，回溯到最近的
     * `### ` 小节标题行（如「### 通过指标」）在其前插入新章节标题；无小节标题则插在表格行前；
     * 无表格则插到本节末尾（下一 `## ` 标题前，节为最后一节时接文末）。
     */
    private static void insertMetricHeading(List<String> lines, int sectionHeadingIndex) {
        int sectionEnd = lines.size();
        for (int i = sectionHeadingIndex + 1; i < lines.size(); i++) {
            if (lines.get(i).startsWith("## ")) {
                sectionEnd = i;
                break;
            }
        }
        int tableLine = -1;
        for (int i = sectionHeadingIndex + 1; i < sectionEnd; i++) {
            if (lines.get(i).trim().startsWith("|")) {
                tableLine = i;
                break;
            }
        }
        int insertAt;
        if (tableLine < 0) {
            insertAt = sectionEnd; // 无指标表：新章节追加到本节末尾
        } else {
            insertAt = tableLine;
            for (int i = tableLine - 1; i > sectionHeadingIndex; i--) {
                if (lines.get(i).startsWith("### ")) {
                    insertAt = i;
                    break;
                }
                if (lines.get(i).startsWith("## ")) {
                    break;
                }
            }
        }
        if (insertAt > sectionHeadingIndex + 1 && !lines.get(insertAt - 1).isBlank()) {
            lines.add(insertAt++, "");
        }
        lines.add(insertAt++, NEW_METRIC_HEADING);
        if (insertAt < lines.size() && !lines.get(insertAt).isBlank()) {
            lines.add(insertAt, "");
        }
    }
}
