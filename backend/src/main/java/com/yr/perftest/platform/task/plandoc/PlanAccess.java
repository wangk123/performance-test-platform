package com.yr.perftest.platform.task.plandoc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动作可见性矩阵（spec 2026-09-11 §4）：无角色维度（权限专项前全员=项目成员门槛在服务层校验）。
 * 键集见 Global Constraints；流转键 SUBMIT/APPROVE/FINISH/PUBLISH 每状态至多一个为 true（单行道）。
 */
public final class PlanAccess {

    private PlanAccess() {
    }

    public static Map<String, Boolean> compute(PlanStatus status) {
        Map<String, Boolean> p = new LinkedHashMap<>();
        p.put("EDIT", true);                 // 任意状态可编辑，revision 冲突保护兜底
        p.put("COMMENT", true);              // 批注不再限评审域
        p.put("NEW_VERSION", true);          // 新增版本与状态解耦
        p.put("DELETE", true);
        p.put("PRECHECK_RUN", true);
        p.put("PRECHECK_SKIP", true);
        p.put("SUBMIT", status == PlanStatus.PLANNING);
        p.put("APPROVE", status == PlanStatus.IN_REVIEW);
        p.put("FINISH", status == PlanStatus.EXECUTING);
        p.put("PUBLISH", status == PlanStatus.REPORTING);
        p.put("EXECUTE", status == PlanStatus.EXECUTING || status == PlanStatus.REPORTING);
        p.put("SHARE", status == PlanStatus.PUBLISHED);
        return p;
    }
}
