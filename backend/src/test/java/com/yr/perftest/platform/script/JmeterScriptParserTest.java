package com.yr.perftest.platform.script;

import java.util.List;

import static com.yr.perftest.platform.TestSupport.*;

public class JmeterScriptParserTest {

    private static final String SAMPLE_JMX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
              <hashTree>
                <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true">
                  <stringProp name="TestPlan.comments"></stringProp>
                  <boolProp name="TestPlan.functional_mode">false</boolProp>
                  <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
                </TestPlan>
                <hashTree>
                  <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Main Thread Group" enabled="true">
                    <stringProp name="ThreadGroup.num_threads">200</stringProp>
                    <stringProp name="ThreadGroup.ramp_time">30</stringProp>
                    <stringProp name="ThreadGroup.duration">600</stringProp>
                    <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                      <boolProp name="LoopController.continue_forever">false</boolProp>
                      <stringProp name="LoopController.loops">5</stringProp>
                    </elementProp>
                  </ThreadGroup>
                  <hashTree>
                    <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="GET /api/users" enabled="true">
                      <stringProp name="HTTPSampler.method">GET</stringProp>
                      <stringProp name="HTTPSampler.domain">localhost</stringProp>
                      <stringProp name="HTTPSampler.port">8080</stringProp>
                      <stringProp name="HTTPSampler.protocol">http</stringProp>
                      <stringProp name="HTTPSampler.path">/api/users</stringProp>
                      <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
                      <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
                      <stringProp name="HTTPSampler.connect_timeout">30000</stringProp>
                      <stringProp name="HTTPSampler.response_timeout">30000</stringProp>
                      <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
                        <collectionProp name="Arguments.arguments"/>
                      </elementProp>
                    </HTTPSamplerProxy>
                    <hashTree/>
                  </hashTree>
                </hashTree>
              </hashTree>
            </jmeterTestPlan>
            """;

    public static void runAll() {
        parsesThreadGroupWithCorrectConfig();
        threadGroupConfigMethodReturnsTypedConfig();
        parsesChildHttpSampler();
        parsesSchedulerMode();
        parsesSteppingThreadGroup();
        parsesResponseAssertionConfig();
        parsesJsonAssertionConfig();
        parseInvalidXmlThrows();
        System.out.println("JmeterScriptParserTest passed");
    }

    static void parsesThreadGroupWithCorrectConfig() {
        JmeterScriptParser parser = new JmeterScriptParser();
        List<ScriptStepDefinition> steps = parser.parseSteps(SAMPLE_JMX);

        assertEquals(1, steps.size(), "one thread group");
        ScriptStepDefinition threadGroup = steps.get(0);
        assertEquals(ScriptStepType.THREAD_GROUP.code(), threadGroup.type(), "type is THREAD_GROUP");
        assertEquals("Main Thread Group", threadGroup.name(), "name matches");
        assertEquals(ScriptStepType.THREAD_GROUP, threadGroup.stepType(), "stepType() returns enum");
    }

    static void threadGroupConfigMethodReturnsTypedConfig() {
        JmeterScriptParser parser = new JmeterScriptParser();
        List<ScriptStepDefinition> steps = parser.parseSteps(SAMPLE_JMX);

        ThreadGroupConfig config = steps.get(0).threadGroupConfig();
        assertEquals(200, config.threads(), "threads=200");
        assertEquals(30, config.rampUp(), "rampUp=30");
        assertEquals(5, config.loops(), "loops=5");
        assertEquals(600, config.duration(), "duration=600");
    }

    static void parsesChildHttpSampler() {
        JmeterScriptParser parser = new JmeterScriptParser();
        List<ScriptStepDefinition> steps = parser.parseSteps(SAMPLE_JMX);

        ScriptStepDefinition threadGroup = steps.get(0);
        assertEquals(1, threadGroup.children().size(), "one child");
        ScriptStepDefinition http = threadGroup.children().get(0);
        assertEquals(ScriptStepType.HTTP_REQUEST.code(), http.type(), "child is HTTP_REQUEST");
        assertEquals(ScriptStepType.HTTP_REQUEST, http.stepType(), "child stepType() returns enum");
    }

    static void parsesSchedulerMode() {
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
                      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Duration Group" enabled="true">
                        <stringProp name="ThreadGroup.num_threads">100</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">10</stringProp>
                        <boolProp name="ThreadGroup.scheduler">true</boolProp>
                        <stringProp name="ThreadGroup.duration">300</stringProp>
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
        List<ScriptStepDefinition> steps = parser.parseSteps(jmx);

        assertEquals(1, steps.size(), "one thread group");
        ThreadGroupConfig config = steps.get(0).threadGroupConfig();
        assertEquals(100, config.threads(), "threads=100");
        assertEquals(10, config.rampUp(), "rampUp=10");
        assertEquals(-1, config.loops(), "loops=-1 for scheduler mode");
        assertEquals(300, config.duration(), "duration=300");
        assertTrue(config.scheduler(), "scheduler=true");
    }

    static void parsesSteppingThreadGroup() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true"/>
                    <hashTree>
                      <kg.apc.jmeter.threads.SteppingThreadGroup guiclass="kg.apc.jmeter.threads.SteppingThreadGroupGui" testclass="kg.apc.jmeter.threads.SteppingThreadGroup" testname="Step Load" enabled="true">
                        <stringProp name="ThreadGroup.num_threads">100</stringProp>
                        <stringProp name="Threads initial delay">5</stringProp>
                        <stringProp name="Start users count">20</stringProp>
                        <stringProp name="Start users period">30</stringProp>
                        <stringProp name="Start users count burst">true</stringProp>
                        <stringProp name="rampUp">10</stringProp>
                        <stringProp name="flighttime">120</stringProp>
                        <stringProp name="Stop users count">10</stringProp>
                        <stringProp name="Stop users period">15</stringProp>
                      </kg.apc.jmeter.threads.SteppingThreadGroup>
                      <hashTree/>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;
        JmeterScriptParser parser = new JmeterScriptParser();
        List<ScriptStepDefinition> steps = parser.parseSteps(jmx);

        ThreadGroupConfig config = steps.get(0).threadGroupConfig();
        assertEquals("stepping", config.mode(), "mode stepping");
        assertEquals(100, config.threads(), "threads=100");
        assertEquals(5, config.stepping().initialDelay(), "initial delay");
        assertEquals(20, config.stepping().startUsersCount(), "start users count");
        assertEquals(30, config.stepping().startUsersPeriod(), "start users period");
        assertEquals(10, config.stepping().rampUp(), "stepping rampUp");
        assertEquals(120, config.stepping().flightTime(), "flight time");
        assertEquals(10, config.stepping().stopUsersCount(), "stop users count");
        assertEquals(15, config.stepping().stopUsersPeriod(), "stop users period");
        assertTrue(config.stepping().burst(), "burst true");
    }

    static void parsesResponseAssertionConfig() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true"/>
                    <hashTree>
                      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Main" enabled="true">
                        <stringProp name="ThreadGroup.num_threads">1</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">0</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <stringProp name="LoopController.loops">1</stringProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion" testname="状态码断言" enabled="true">
                          <collectionProp name="Assertion.test_strings"><stringProp name="0">200</stringProp></collectionProp>
                          <stringProp name="Assertion.test_field">Assertion.response_code</stringProp>
                          <intProp name="Assertion.test_type">8</intProp>
                        </ResponseAssertion>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;
        JmeterScriptParser parser = new JmeterScriptParser();
        ScriptStepDefinition assertion = parser.parseSteps(jmx).get(0).children().get(0);

        assertEquals(ScriptStepType.RESPONSE_ASSERTION.code(), assertion.type(), "assertion type");
        assertEquals("statusCode", assertion.config().get("target"), "assertion target");
        assertEquals("equals", assertion.config().get("match"), "assertion match");
        assertEquals("200", assertion.config().get("rule"), "assertion rule");
    }

    static void parsesJsonAssertionConfig() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true"/>
                    <hashTree>
                      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Main" enabled="true">
                        <stringProp name="ThreadGroup.num_threads">1</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">0</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <stringProp name="LoopController.loops">1</stringProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <JSONPathAssertion guiclass="JSONPathAssertionGui" testclass="JSONPathAssertion" testname="业务码断言" enabled="true">
                          <stringProp name="JSON_PATH">$.code</stringProp>
                          <stringProp name="EXPECTED_VALUE">0</stringProp>
                          <boolProp name="JSONVALIDATION">true</boolProp>
                          <boolProp name="ISREGEX">false</boolProp>
                        </JSONPathAssertion>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;
        JmeterScriptParser parser = new JmeterScriptParser();
        ScriptStepDefinition assertion = parser.parseSteps(jmx).get(0).children().get(0);

        assertEquals(ScriptStepType.JSON_ASSERTION.code(), assertion.type(), "json assertion type");
        assertEquals("$.code", assertion.config().get("jsonPath"), "json path");
        assertEquals(true, assertion.config().get("validateValue"), "validate value");
        assertEquals("0", assertion.config().get("expectedValue"), "expected value");
        assertEquals(false, assertion.config().get("useRegex"), "use regex");
    }

    static void parseInvalidXmlThrows() {
        JmeterScriptParser parser = new JmeterScriptParser();
        assertThrows(ScriptValidationException.class, () -> parser.parseSteps("not xml at all"), "invalid XML should throw");
    }
}
