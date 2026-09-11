package com.yr.perftest.platform.task.plandoc;

/** 计划单一状态（spec 2026-09-11 §3.1）：单行道流转，无回退。 */
public enum PlanStatus { PLANNING, IN_REVIEW, EXECUTING, REPORTING, PUBLISHED }
