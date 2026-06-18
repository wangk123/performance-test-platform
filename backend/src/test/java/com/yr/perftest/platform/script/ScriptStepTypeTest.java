package com.yr.perftest.platform.script;

import static com.yr.perftest.platform.TestSupport.*;

public class ScriptStepTypeTest {

    public static void runAll() {
        fromCodeReturnsCorrectEnum();
        fromCodeReturnsNullForUnknown();
        fromCodeReturnsNullForNull();
        codeMatchesExpectedStrings();
        stepTypeMethodOnDefinition();
        threadGroupConfigThrowsForNonThreadGroup();
        System.out.println("ScriptStepTypeTest passed");
    }

    static void fromCodeReturnsCorrectEnum() {
        assertEquals(ScriptStepType.THREAD_GROUP, ScriptStepType.fromCode("THREAD_GROUP"), "THREAD_GROUP lookup");
        assertEquals(ScriptStepType.HTTP_REQUEST, ScriptStepType.fromCode("HTTP_REQUEST"), "HTTP_REQUEST lookup");
        assertEquals(ScriptStepType.CSV_DATA, ScriptStepType.fromCode("CSV_DATA"), "CSV_DATA lookup");
        assertEquals(ScriptStepType.RESPONSE_ASSERTION, ScriptStepType.fromCode("ASSERTION"), "RESPONSE_ASSERTION lookup via ASSERTION code");
        assertEquals(ScriptStepType.JSON_ASSERTION, ScriptStepType.fromCode("JSON_ASSERTION"), "JSON_ASSERTION lookup");
        assertEquals(ScriptStepType.USER_PARAMS, ScriptStepType.fromCode("USER_PARAMS"), "USER_PARAMS lookup");
        assertEquals(ScriptStepType.HEADER_CONFIG, ScriptStepType.fromCode("HEADER_CONFIG"), "HEADER_CONFIG lookup");
    }

    static void fromCodeReturnsNullForUnknown() {
        assertNull(ScriptStepType.fromCode("UNKNOWN_TYPE"), "unknown code returns null");
        assertNull(ScriptStepType.fromCode(""), "empty code returns null");
    }

    static void fromCodeReturnsNullForNull() {
        assertNull(ScriptStepType.fromCode(null), "null code returns null");
    }

    static void codeMatchesExpectedStrings() {
        assertEquals("THREAD_GROUP", ScriptStepType.THREAD_GROUP.code(), "THREAD_GROUP code");
        assertEquals("HTTP_REQUEST", ScriptStepType.HTTP_REQUEST.code(), "HTTP_REQUEST code");
        assertEquals("ASSERTION", ScriptStepType.RESPONSE_ASSERTION.code(), "RESPONSE_ASSERTION code is ASSERTION");
        assertEquals("JSON_ASSERTION", ScriptStepType.JSON_ASSERTION.code(), "JSON_ASSERTION code");
        assertEquals("CSV_DATA", ScriptStepType.CSV_DATA.code(), "CSV_DATA code");
    }

    static void stepTypeMethodOnDefinition() {
        ScriptStepDefinition step = new ScriptStepDefinition("id-1", "THREAD_GROUP", "Test", java.util.Map.of(), java.util.List.of());
        assertEquals(ScriptStepType.THREAD_GROUP, step.stepType(), "stepType returns THREAD_GROUP");
    }

    static void threadGroupConfigThrowsForNonThreadGroup() {
        ScriptStepDefinition httpStep = new ScriptStepDefinition("id-2", "HTTP_REQUEST", "GET /", java.util.Map.of(), java.util.List.of());
        assertThrows(IllegalStateException.class, httpStep::threadGroupConfig, "non-THREAD_GROUP should throw");
    }

    private static void assertNull(Object value, String message) {
        assertTrue(value == null, message + " expected=null actual=" + value);
    }
}
