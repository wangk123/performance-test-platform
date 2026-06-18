package com.yr.perftest.platform.script;

import java.util.List;
import java.util.Map;

import static com.yr.perftest.platform.TestSupport.*;

public class JmeterScriptRendererTest {

    public static void runAll() {
        rendersThreadGroupWithCorrectValues();
        rendersSchedulerModeThreadGroup();
        rendersSteppingThreadGroup();
        rendersResponseAssertionConfig();
        rendersJsonAssertionConfig();
        roundTripPreservesThreadGroupConfig();
        roundTripPreservesSchedulerMode();
        rendersEmptyStepsList();
        System.out.println("JmeterScriptRendererTest passed");
    }

    static void rendersThreadGroupWithCorrectValues() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();

        ScriptStepDefinition threadGroup = new ScriptStepDefinition(
                "thread-1",
                ScriptStepType.THREAD_GROUP.code(),
                "Load Test",
                new ThreadGroupConfig(150, 45, 3, 300, false).toMap(),
                List.of()
        );

        String output = renderer.render(List.of(threadGroup));

        assertTrue(output.contains("<ThreadGroup"), "output contains ThreadGroup element");
        assertTrue(output.contains("testname=\"Load Test\""), "output contains test name");
        assertTrue(output.contains("<stringProp name=\"ThreadGroup.num_threads\">150</stringProp>"), "threads=150");
        assertTrue(output.contains("<stringProp name=\"ThreadGroup.ramp_time\">45</stringProp>"), "rampUp=45");
        assertTrue(output.contains("<stringProp name=\"LoopController.loops\">3</stringProp>"), "loops=3");
        assertTrue(output.contains("<stringProp name=\"ThreadGroup.duration\">300</stringProp>"), "duration=300");
        assertFalse(output.contains("ThreadGroup.scheduler"), "no scheduler prop when false");
    }

    static void rendersSchedulerModeThreadGroup() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();

        ScriptStepDefinition threadGroup = new ScriptStepDefinition(
                "thread-2",
                ScriptStepType.THREAD_GROUP.code(),
                "Duration Test",
                new ThreadGroupConfig(200, 30, 1, 600, true).toMap(),
                List.of()
        );

        String output = renderer.render(List.of(threadGroup));

        assertTrue(output.contains("<stringProp name=\"ThreadGroup.num_threads\">200</stringProp>"), "threads=200");
        assertTrue(output.contains("<boolProp name=\"ThreadGroup.scheduler\">true</boolProp>"), "scheduler=true");
        assertTrue(output.contains("<stringProp name=\"ThreadGroup.duration\">600</stringProp>"), "duration=600");
        assertTrue(output.contains("<stringProp name=\"LoopController.loops\">-1</stringProp>"), "loops=-1 for scheduler mode");
    }

    static void rendersSteppingThreadGroup() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();

        ScriptStepDefinition threadGroup = new ScriptStepDefinition(
                "thread-step",
                ScriptStepType.THREAD_GROUP.code(),
                "Step Load",
                Map.of(
                        "mode", "stepping",
                        "threads", 100,
                        "stepping", Map.of(
                                "initialDelay", 5,
                                "startUsersCount", 20,
                                "startUsersPeriod", 30,
                                "rampUp", 10,
                                "flightTime", 120,
                                "stopUsersCount", 10,
                                "stopUsersPeriod", 15,
                                "burst", true
                        )
                ),
                List.of()
        );

        String output = renderer.render(List.of(threadGroup));

        assertTrue(output.contains("kg.apc.jmeter.threads.SteppingThreadGroup"), "renders stepping thread group");
        assertTrue(output.contains("<stringProp name=\"ThreadGroup.num_threads\">100</stringProp>"), "threads=100");
        assertTrue(output.contains("<stringProp name=\"Threads initial delay\">5</stringProp>"), "initial delay");
        assertTrue(output.contains("<stringProp name=\"Start users count\">20</stringProp>"), "start users count");
        assertTrue(output.contains("<stringProp name=\"Start users period\">30</stringProp>"), "start users period");
        assertTrue(output.contains("<stringProp name=\"Start users count burst\">true</stringProp>"), "burst");
        assertTrue(output.contains("<stringProp name=\"rampUp\">10</stringProp>"), "stepping rampUp");
        assertTrue(output.contains("<stringProp name=\"flighttime\">120</stringProp>"), "flight time");
        assertTrue(output.contains("<stringProp name=\"Stop users count\">10</stringProp>"), "stop users count");
        assertTrue(output.contains("<stringProp name=\"Stop users period\">15</stringProp>"), "stop users period");
    }

    static void rendersResponseAssertionConfig() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();
        ScriptStepDefinition threadGroup = new ScriptStepDefinition(
                "thread-assert",
                ScriptStepType.THREAD_GROUP.code(),
                "Main",
                ThreadGroupConfig.DEFAULT.toMap(),
                List.of(new ScriptStepDefinition(
                        "assert-1",
                        ScriptStepType.RESPONSE_ASSERTION.code(),
                        "状态码断言",
                        Map.of("target", "statusCode", "match", "equals", "rule", "200"),
                        List.of()
                ))
        );

        String output = renderer.render(List.of(threadGroup));

        assertTrue(output.contains("<stringProp name=\"Assertion.test_field\">Assertion.response_code</stringProp>"), "response code target");
        assertTrue(output.contains("<intProp name=\"Assertion.test_type\">8</intProp>"), "equals match");
        assertTrue(output.contains("<stringProp name=\"0\">200</stringProp>"), "assertion rule");
    }

    static void rendersJsonAssertionConfig() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();
        ScriptStepDefinition threadGroup = new ScriptStepDefinition(
                "thread-json-assert",
                ScriptStepType.THREAD_GROUP.code(),
                "Main",
                ThreadGroupConfig.DEFAULT.toMap(),
                List.of(new ScriptStepDefinition(
                        "json-assert-1",
                        ScriptStepType.JSON_ASSERTION.code(),
                        "业务码断言",
                        Map.of(
                                "jsonPath", "$.code",
                                "validateValue", true,
                                "expectedValue", "0",
                                "useRegex", false
                        ),
                        List.of()
                ))
        );

        String output = renderer.render(List.of(threadGroup));

        assertTrue(output.contains("<JSONPathAssertion"), "json assertion element");
        assertTrue(output.contains("<stringProp name=\"JSON_PATH\">$.code</stringProp>"), "json path");
        assertTrue(output.contains("<stringProp name=\"EXPECTED_VALUE\">0</stringProp>"), "expected value");
        assertTrue(output.contains("<boolProp name=\"JSONVALIDATION\">true</boolProp>"), "json validation");
        assertTrue(output.contains("<boolProp name=\"ISREGEX\">false</boolProp>"), "regex disabled");
    }

    static void roundTripPreservesThreadGroupConfig() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true">
                      <stringProp name="TestPlan.comments"></stringProp>
                      <boolProp name="TestPlan.functional_mode">false</boolProp>
                      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
                    </TestPlan>
                    <hashTree>
                      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Round Trip" enabled="true">
                        <stringProp name="ThreadGroup.num_threads">80</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">20</stringProp>
                        <stringProp name="ThreadGroup.duration">120</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <boolProp name="LoopController.continue_forever">false</boolProp>
                          <stringProp name="LoopController.loops">7</stringProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree/>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        JmeterScriptParser parser = new JmeterScriptParser();
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();

        List<ScriptStepDefinition> steps = parser.parseSteps(jmx);
        String rendered = renderer.render(steps);
        List<ScriptStepDefinition> reParsed = parser.parseSteps(rendered);

        assertEquals(1, reParsed.size(), "one thread group after round trip");
        ThreadGroupConfig originalConfig = steps.get(0).threadGroupConfig();
        ThreadGroupConfig roundTripConfig = reParsed.get(0).threadGroupConfig();

        assertEquals(originalConfig.threads(), roundTripConfig.threads(), "threads preserved");
        assertEquals(originalConfig.rampUp(), roundTripConfig.rampUp(), "rampUp preserved");
        assertEquals(originalConfig.loops(), roundTripConfig.loops(), "loops preserved");
        assertEquals(originalConfig.duration(), roundTripConfig.duration(), "duration preserved");
    }

    static void roundTripPreservesSchedulerMode() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true">
                      <stringProp name="TestPlan.comments"></stringProp>
                      <boolProp name="TestPlan.functional_mode">false</boolProp>
                      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
                    </TestPlan>
                    <hashTree>
                      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Scheduler Trip" enabled="true">
                        <stringProp name="ThreadGroup.num_threads">100</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">30</stringProp>
                        <boolProp name="ThreadGroup.scheduler">true</boolProp>
                        <stringProp name="ThreadGroup.duration">900</stringProp>
                        <stringProp name="ThreadGroup.delay">0</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <boolProp name="LoopController.continue_forever">false</boolProp>
                          <stringProp name="LoopController.loops">-1</stringProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree/>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        JmeterScriptParser parser = new JmeterScriptParser();
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();

        List<ScriptStepDefinition> steps = parser.parseSteps(jmx);
        ThreadGroupConfig parsedConfig = steps.get(0).threadGroupConfig();
        assertTrue(parsedConfig.scheduler(), "parsed scheduler=true");
        assertEquals(-1, parsedConfig.loops(), "parsed loops=-1");
        assertEquals(900, parsedConfig.duration(), "parsed duration=900");

        String rendered = renderer.render(steps);
        assertTrue(rendered.contains("ThreadGroup.scheduler\">true"), "rendered scheduler=true");
        assertTrue(rendered.contains("LoopController.loops\">-1"), "rendered loops=-1");

        List<ScriptStepDefinition> reParsed = parser.parseSteps(rendered);
        ThreadGroupConfig roundTripConfig = reParsed.get(0).threadGroupConfig();
        assertTrue(roundTripConfig.scheduler(), "round-trip scheduler=true");
        assertEquals(-1, roundTripConfig.loops(), "round-trip loops=-1");
        assertEquals(900, roundTripConfig.duration(), "round-trip duration=900");
    }

    static void rendersEmptyStepsList() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();
        String output = renderer.render(List.of());

        assertTrue(output.contains("<jmeterTestPlan"), "output contains test plan root");
        assertTrue(output.contains("<hashTree>"), "output contains hashTree");
        assertFalse(output.contains("<ThreadGroup"), "no ThreadGroup in empty output");
    }
}
