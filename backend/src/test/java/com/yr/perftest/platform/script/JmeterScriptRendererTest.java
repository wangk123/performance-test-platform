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
        rendersCustomHttpStepName();
        roundTripPreservesHttpStepName();
        roundTripPreservesThreadGroupConfig();
        roundTripPreservesSchedulerMode();
        csvFullAttributesRoundTrip();
        csvDefaultRenderUnchanged();
        jsr223RoundTripEscapesGroovy();
        rendersEmptyStepsList();
        sharedComponentsRoundTripFromJmeterNativeFormat();
        headerManagerRendersHeadersArrayAndTextFallback();
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

    static void rendersCustomHttpStepName() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();
        ScriptStepDefinition threadGroup = new ScriptStepDefinition(
                "thread-http",
                ScriptStepType.THREAD_GROUP.code(),
                "Main",
                ThreadGroupConfig.DEFAULT.toMap(),
                List.of(new ScriptStepDefinition(
                        "http-1",
                        ScriptStepType.HTTP_REQUEST.code(),
                        "用户登录",
                        Map.of(
                                "method", "POST",
                                "url", "http://localhost/api/login",
                                "path", "/api/login",
                                "bodyType", "none"
                        ),
                        List.of()
                ))
        );

        String output = renderer.render(List.of(threadGroup));

        assertTrue(output.contains("testname=\"用户登录\""), "custom http step name rendered");
    }

    static void roundTripPreservesHttpStepName() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();
        JmeterScriptParser parser = new JmeterScriptParser();
        ScriptStepDefinition threadGroup = new ScriptStepDefinition(
                "thread-http-roundtrip",
                ScriptStepType.THREAD_GROUP.code(),
                "Main",
                ThreadGroupConfig.DEFAULT.toMap(),
                List.of(new ScriptStepDefinition(
                        "http-2",
                        ScriptStepType.HTTP_REQUEST.code(),
                        "查询订单",
                        Map.of(
                                "method", "GET",
                                "url", "http://localhost/api/orders",
                                "path", "/api/orders",
                                "bodyType", "none"
                        ),
                        List.of()
                ))
        );

        ScriptStepDefinition http = parser.parseSteps(renderer.render(List.of(threadGroup))).get(0).children().get(0);
        assertEquals("查询订单", http.name(), "custom http step name round-trips");
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

    static void csvFullAttributesRoundTrip() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();
        JmeterScriptParser parser = new JmeterScriptParser();
        ScriptStepDefinition threadGroup = new ScriptStepDefinition(
                "thread-csv",
                ScriptStepType.THREAD_GROUP.code(),
                "Main",
                ThreadGroupConfig.DEFAULT.toMap(),
                List.of(new ScriptStepDefinition(
                        "csv-1",
                        ScriptStepType.CSV_DATA.code(),
                        "用户数据",
                        Map.of("fileName", "users.csv", "variableNames", "mobile,name", "delimiter", "|",
                                "fileEncoding", "GBK", "ignoreFirstLine", false, "recycle", false,
                                "stopThread", true, "shareMode", "shareMode.thread"),
                        List.of()
                ))
        );

        String output = renderer.render(List.of(threadGroup));

        assertTrue(output.contains("<stringProp name=\"delimiter\">|</stringProp>"), "delimiter rendered");
        assertTrue(output.contains("<stringProp name=\"fileEncoding\">GBK</stringProp>"), "fileEncoding rendered");
        assertTrue(output.contains("<stringProp name=\"ignoreFirstLine\">false</stringProp>"), "ignoreFirstLine rendered");
        assertTrue(output.contains("<stringProp name=\"recycle\">false</stringProp>"), "recycle rendered");
        assertTrue(output.contains("<stringProp name=\"stopThread\">true</stringProp>"), "stopThread rendered");
        assertTrue(output.contains("<stringProp name=\"shareMode\">shareMode.thread</stringProp>"), "shareMode rendered");

        ScriptStepDefinition csv = parser.parseSteps(output).get(0).children().get(0);
        assertEquals("users.csv", csv.config().get("fileName"), "fileName round-trips");
        assertEquals("mobile,name", csv.config().get("variableNames"), "variableNames round-trips");
        assertEquals("|", csv.config().get("delimiter"), "delimiter round-trips");
        assertEquals("GBK", csv.config().get("fileEncoding"), "fileEncoding round-trips");
        assertEquals(Boolean.FALSE, csv.config().get("ignoreFirstLine"), "ignoreFirstLine round-trips as Boolean");
        assertEquals(Boolean.FALSE, csv.config().get("recycle"), "recycle round-trips as Boolean");
        assertEquals(Boolean.TRUE, csv.config().get("stopThread"), "stopThread round-trips as Boolean");
        assertEquals("shareMode.thread", csv.config().get("shareMode"), "shareMode round-trips");
    }

    static void csvDefaultRenderUnchanged() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();
        ScriptStepDefinition threadGroup = new ScriptStepDefinition(
                "thread-csv-default",
                ScriptStepType.THREAD_GROUP.code(),
                "Main",
                ThreadGroupConfig.DEFAULT.toMap(),
                List.of(new ScriptStepDefinition(
                        "csv-default",
                        ScriptStepType.CSV_DATA.code(),
                        "默认数据",
                        Map.of("fileName", "users.csv", "variableNames", "mobile,name"),
                        List.of()
                ))
        );

        String output = renderer.render(List.of(threadGroup));

        assertTrue(output.contains("<stringProp name=\"filename\">users.csv</stringProp>"), "filename rendered");
        assertTrue(output.contains("<stringProp name=\"variableNames\">mobile,name</stringProp>"), "variableNames rendered");
        assertFalse(output.contains("name=\"delimiter\""), "no delimiter prop when key absent");
        assertFalse(output.contains("name=\"fileEncoding\""), "no fileEncoding prop when key absent");
        assertFalse(output.contains("name=\"ignoreFirstLine\""), "no ignoreFirstLine prop when key absent");
        assertFalse(output.contains("name=\"recycle\""), "no recycle prop when key absent");
        assertFalse(output.contains("name=\"stopThread\""), "no stopThread prop when key absent");
        assertFalse(output.contains("name=\"shareMode\""), "no shareMode prop when key absent");
    }

    static void jsr223RoundTripEscapesGroovy() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();
        JmeterScriptParser parser = new JmeterScriptParser();
        ScriptStepDefinition step = new ScriptStepDefinition(
                "jsr223-pre-1",
                ScriptStepType.JSR223_PRE_PROCESSOR.code(),
                "签名",
                Map.of(
                        "scriptLanguage", "groovy",
                        "script", "def s = \"a<b\" & 'c'\nif (x < 1) {}",
                        "parameters", "env=prod",
                        "cacheKey", true
                ),
                List.of()
        );

        String xml = renderer.renderStepFragment(step);

        assertTrue(xml.contains("<JSR223PreProcessor guiclass=\"TestBeanGUI\" testclass=\"JSR223PreProcessor\""), "pre tag with TestBeanGUI");
        assertTrue(xml.contains("&lt;"), "< escaped as entity");
        assertTrue(xml.contains("&amp;"), "& escaped as entity");
        assertFalse(xml.contains("<![CDATA["), "no CDATA wrapping");
        assertTrue(xml.contains("<stringProp name=\"cacheKey\">true</stringProp>"), "cacheKey rendered");
        assertTrue(xml.contains("<stringProp name=\"scriptLanguage\">groovy</stringProp>"), "scriptLanguage rendered");
        assertTrue(xml.contains("<stringProp name=\"parameters\">env=prod</stringProp>"), "parameters rendered");
        assertTrue(xml.contains("<stringProp name=\"filename\"></stringProp>"), "empty filename prop rendered");
        assertTrue(xml.contains("<stringProp name=\"script\">def s = &quot;a&lt;b&quot; &amp; &apos;c&apos;\nif (x &lt; 1) {}</stringProp>"), "script fully escaped, newline preserved");

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
                """ + xml + """
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        ScriptStepDefinition parsed = parser.parseSteps(jmx).get(0).children().get(0);
        assertEquals(ScriptStepType.JSR223_PRE_PROCESSOR.code(), parsed.type(), "jsr223 pre type round-trips");
        assertEquals("签名", parsed.name(), "testname round-trips");
        assertEquals("groovy", parsed.config().get("scriptLanguage"), "scriptLanguage round-trips");
        assertEquals("def s = \"a<b\" & 'c'\nif (x < 1) {}", parsed.config().get("script"), "script round-trips with newline");
        assertEquals("env=prod", parsed.config().get("parameters"), "parameters round-trips");
        assertEquals(Boolean.TRUE, parsed.config().get("cacheKey"), "cacheKey round-trips as Boolean");
    }

    static void rendersEmptyStepsList() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();
        String output = renderer.render(List.of());

        assertTrue(output.contains("<jmeterTestPlan"), "output contains test plan root");
        assertTrue(output.contains("<hashTree>"), "output contains hashTree");
        assertFalse(output.contains("<ThreadGroup"), "no ThreadGroup in empty output");
    }

    /** JMeter 原生格式导入 → 平台渲染 → 再解析：公共元件（根层）配置无损往返。 */
    static void sharedComponentsRoundTripFromJmeterNativeFormat() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true"/>
                    <hashTree>
                      <UserParameters guiclass="UserParametersGui" testclass="UserParameters" testname="用户参数" enabled="true">
                        <collectionProp name="UserParameters.names">
                          <stringProp name="a">username</stringProp>
                          <stringProp name="b">password</stringProp>
                        </collectionProp>
                        <collectionProp name="UserParameters.thread_values">
                          <collectionProp name="c">
                            <stringProp name="d">user1</stringProp>
                            <stringProp name="e">pass1</stringProp>
                          </collectionProp>
                          <collectionProp name="f">
                            <stringProp name="g">user2</stringProp>
                            <stringProp name="h">pass2</stringProp>
                          </collectionProp>
                        </collectionProp>
                        <boolProp name="UserParameters.per_iteration">true</boolProp>
                      </UserParameters>
                      <hashTree/>
                      <Arguments guiclass="ArgumentsPanel" testclass="Arguments" testname="用户定义的变量" enabled="true">
                        <collectionProp name="Arguments.arguments">
                          <elementProp name="host" elementType="Argument">
                            <stringProp name="Argument.name">host</stringProp>
                            <stringProp name="Argument.value">api.example.com</stringProp>
                            <stringProp name="Argument.metadata">=</stringProp>
                          </elementProp>
                        </collectionProp>
                      </Arguments>
                      <hashTree/>
                      <ConstantTimer guiclass="ConstantTimerGui" testclass="ConstantTimer" testname="固定定时器" enabled="true">
                        <stringProp name="ConstantTimer.delay">300</stringProp>
                      </ConstantTimer>
                      <hashTree/>
                      <UniformRandomTimer guiclass="UniformRandomTimerGui" testclass="UniformRandomTimer" testname="随机定时器" enabled="true">
                        <stringProp name="UniformRandomTimer.delay">1000</stringProp>
                        <stringProp name="UniformRandomTimer.range">500.0</stringProp>
                      </UniformRandomTimer>
                      <hashTree/>
                      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Main" enabled="true">
                        <stringProp name="ThreadGroup.num_threads">10</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">1</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <stringProp name="LoopController.loops">1</stringProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree/>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;
        JmeterScriptParser parser = new JmeterScriptParser();
        List<ScriptStepDefinition> parsed = parser.parseSteps(jmx);
        String rendered = new JmeterScriptRenderer().render(parsed);
        List<ScriptStepDefinition> reparsed = parser.parseSteps(rendered);

        assertEquals(5, reparsed.size(), "all root components survive round-trip");
        assertEquals(parsed.get(0).config(), reparsed.get(0).config(), "user params matrix round-trips");
        assertEquals(parsed.get(1).config(), reparsed.get(1).config(), "user variables round-trip");
        assertEquals("300", reparsed.get(2).config().get("delay"), "constant timer delay round-trips");
        assertEquals("500.0", reparsed.get(3).config().get("range"), "random timer range round-trips");
        assertEquals("THREAD_GROUP", reparsed.get(4).type(), "thread group survives after shared components");
        assertTrue(rendered.contains("UserParametersGui"), "user params renders native guiclass");
        assertTrue(rendered.contains("ArgumentsPanel"), "user variables renders native guiclass");
        assertTrue(rendered.contains("UniformRandomTimerGui"), "random timer renders native guiclass");
    }

    static void headerManagerRendersHeadersArrayAndTextFallback() {
        JmeterScriptRenderer renderer = new JmeterScriptRenderer();
        ScriptStepDefinition fromArray = new ScriptStepDefinition(
                "header-1", "HEADER_CONFIG", "公共 Header",
                Map.of("headers", List.of(Map.of("enabled", true, "key", "X-Env", "value", "SIT", "description", ""))),
                List.of());
        String arrayOutput = renderer.renderStepFragment(fromArray);
        assertTrue(arrayOutput.contains("<stringProp name=\"Header.name\">X-Env</stringProp>"), "array headers render");
        assertTrue(arrayOutput.contains("<stringProp name=\"Header.value\">SIT</stringProp>"), "array header value renders");

        ScriptStepDefinition fromText = new ScriptStepDefinition(
                "header-2", "HEADER_CONFIG", "公共 Header",
                Map.of("headersText", "Content-Type: application/json"),
                List.of());
        String textOutput = renderer.renderStepFragment(fromText);
        assertTrue(textOutput.contains("<stringProp name=\"Header.name\">Content-Type</stringProp>"), "text fallback headers render");
        assertTrue(textOutput.contains("<stringProp name=\"Header.value\">application/json</stringProp>"), "text fallback value renders");
    }
}
