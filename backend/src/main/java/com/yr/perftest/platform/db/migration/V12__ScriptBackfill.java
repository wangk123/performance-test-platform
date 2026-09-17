package com.yr.perftest.platform.db.migration;

import com.yr.perftest.platform.script.ScriptBackfillCalculator;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V12：为每条 script_id IS NULL 的存量版本记录建 scripts 壳并回填归属。
 * 版本记录 id 与 version_no 原样保留（task_scenarios.script_version_id 引用零断裂），全部标 PUBLISHED。
 * 声明为 Spring Bean 才会被 Spring Boot 的 Flyway 自动配置收集执行。
 */
@Component
public class V12__ScriptBackfill extends BaseJavaMigration {
    private final ScriptBackfillCalculator calculator = new ScriptBackfillCalculator();

    @Override
    public void migrate(Context context) throws Exception {
        Map<Long, Long> projectIdByVersionId = new HashMap<>();
        List<ScriptBackfillCalculator.LegacyVersionRow> rows = new ArrayList<>();
        try (Statement statement = context.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT id, project_id, original_filename, version_no, uploaded_by, uploaded_at "
                             + "FROM script_versions WHERE script_id IS NULL ORDER BY id")) {
            while (rs.next()) {
                long versionId = rs.getLong(1);
                projectIdByVersionId.put(versionId, rs.getLong(2));
                rows.add(new ScriptBackfillCalculator.LegacyVersionRow(
                        versionId, rs.getLong(2), rs.getString(3), rs.getInt(4),
                        rs.getString(5), rs.getTimestamp(6).toInstant()));
            }
        }
        for (ScriptBackfillCalculator.BackfillPlan plan : calculator.backfillPlans(rows)) {
            long scriptId = insertScript(context, projectIdByVersionId.get(plan.versionId()), plan);
            updateVersion(context, scriptId, plan.versionId());
        }
    }

    private long insertScript(Context context, long projectId, ScriptBackfillCalculator.BackfillPlan plan) throws Exception {
        try (PreparedStatement insert = context.getConnection().prepareStatement(
                "INSERT INTO scripts (project_id, name, latest_version_no, created_by, created_at) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, projectId);
            insert.setString(2, plan.scriptName());
            insert.setInt(3, plan.latestVersionNo());
            insert.setString(4, plan.createdBy());
            insert.setTimestamp(5, Timestamp.from(plan.createdAt()));
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void updateVersion(Context context, long scriptId, long versionId) throws Exception {
        try (PreparedStatement update = context.getConnection().prepareStatement(
                "UPDATE script_versions SET script_id = ?, status = 'PUBLISHED', updated_at = uploaded_at WHERE id = ?")) {
            update.setLong(1, scriptId);
            update.setLong(2, versionId);
            update.executeUpdate();
        }
    }
}
