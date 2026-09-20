package com.yr.perftest.platform.script;

import java.util.List;
import java.util.Map;

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
        parsesCustomHttpStepName();
        parsesSchedulerMode();
        parsesSteppingThreadGroup();
        parsesResponseAssertionConfig();
        parsesJsonAssertionConfig();
        parsesCsvFullAttributesAndDefaults();
        parsesJsr223ExternalFragment();
        parsesUserParamsMatrixFromJmeterNativeFormat();
        parsesUserVariablesFromArguments();
        parsesConstantAndRandomTimers();
        parsesRootLevelSharedComponentsInDocumentOrder();
        parsesDisabledElementsToConfig();
        parseInvalidXmlThrows();
        System.out.println("JmeterScriptParserTest passed");
    }

    static void parsesDisabledElementsToConfig() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true"/>
                    <hashTree>
                      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Main" enabled="false">
                        <stringProp name="ThreadGroup.num_threads">1</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">0</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <stringProp name="LoopController.loops">1</stringProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <ConstantTimer guiclass="ConstantTimerGui" testclass="ConstantTimer" testname="停用定时器" enabled="false">
                          <stringProp name="ConstantTimer.delay">300</stringProp>
                        </ConstantTimer>
                        <hashTree/>
                        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="GET /api" enabled="true">
                          <stringProp name="HTTPSampler.method">GET</stringProp>
                          <stringProp name="HTTPSampler.path">/api</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;
        List<ScriptStepDefinition> steps = new JmeterScriptParser().parseSteps(jmx);
        assertEquals(Boolean.FALSE, steps.get(0).config().get("enabled"), "disabled thread group parsed");
        assertEquals(Boolean.FALSE, steps.get(0).children().get(0).config().get("enabled"), "disabled timer parsed");
        assertEquals(Boolean.TRUE, steps.get(0).children().get(1).config().get("enabled"), "enabled sampler defaults to true");
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
        assertEquals("GET /api/users", http.name(), "name from testname attribute");
    }

    static void parsesCustomHttpStepName() {
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
                        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="用户登录" enabled="true">
                          <stringProp name="HTTPSampler.method">POST</stringProp>
                          <stringProp name="HTTPSampler.path">/api/login</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;
        ScriptStepDefinition http = new JmeterScriptParser().parseSteps(jmx).get(0).children().get(0);
        assertEquals("用户登录", http.name(), "custom testname preserved");
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

    static void parsesCsvFullAttributesAndDefaults() {
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
                        <CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="用户数据" enabled="true">
                          <stringProp name="filename">users.csv</stringProp>
                          <stringProp name="variableNames">mobile,name</stringProp>
                          <stringProp name="delimiter">|</stringProp>
                          <stringProp name="fileEncoding">GBK</stringProp>
                          <stringProp name="ignoreFirstLine">false</stringProp>
                          <stringProp name="recycle">false</stringProp>
                          <stringProp name="stopThread">true</stringProp>
                          <stringProp name="shareMode">shareMode.thread</stringProp>
                        </CSVDataSet>
                        <hashTree/>
                        <CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="旧数据" enabled="true">
                          <stringProp name="filename">legacy.csv</stringProp>
                          <stringProp name="variableNames">a,b</stringProp>
                        </CSVDataSet>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;
        JmeterScriptParser parser = new JmeterScriptParser();
        List<ScriptStepDefinition> children = parser.parseSteps(jmx).get(0).children();

        assertEquals(2, children.size(), "two csv steps");
        ScriptStepDefinition full = children.get(0);
        assertEquals(ScriptStepType.CSV_DATA.code(), full.type(), "csv step type");
        assertEquals("|", full.config().get("delimiter"), "delimiter parsed");
        assertEquals("GBK", full.config().get("fileEncoding"), "fileEncoding parsed");
        assertEquals(Boolean.FALSE, full.config().get("ignoreFirstLine"), "ignoreFirstLine parsed as Boolean");
        assertEquals(Boolean.FALSE, full.config().get("recycle"), "recycle parsed as Boolean");
        assertEquals(Boolean.TRUE, full.config().get("stopThread"), "stopThread parsed as Boolean");
        assertEquals("shareMode.thread", full.config().get("shareMode"), "shareMode parsed");

        ScriptStepDefinition legacy = children.get(1);
        assertEquals(",", legacy.config().get("delimiter"), "delimiter default");
        assertEquals("UTF-8", legacy.config().get("fileEncoding"), "fileEncoding default");
        assertEquals(Boolean.FALSE, legacy.config().get("ignoreFirstLine"), "ignoreFirstLine default aligns JMeter (false)");
        assertEquals(Boolean.TRUE, legacy.config().get("recycle"), "recycle default");
        assertEquals(Boolean.FALSE, legacy.config().get("stopThread"), "stopThread default");
        assertEquals("shareMode.all", legacy.config().get("shareMode"), "shareMode default");
    }

    static void parsesJsr223ExternalFragment() {
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
                        <JSR223PreProcessor guiclass="TestBeanGUI" testclass="JSR223PreProcessor" testname="前置脚本" enabled="true">
                          <stringProp name="cacheKey">true</stringProp>
                          <stringProp name="scriptLanguage">beanshell</stringProp>
                          <stringProp name="parameters">env=prod</stringProp>
                          <stringProp name="filename"></stringProp>
                          <stringProp name="script">String tag = "ok";</stringProp>
                        </JSR223PreProcessor>
                        <hashTree/>
                        <JSR223PostProcessor guiclass="TestBeanGUI" testclass="JSR223PostProcessor" testname="后置脚本" enabled="true">
                          <stringProp name="cacheKey">false</stringProp>
                          <stringProp name="scriptLanguage">groovy</stringProp>
                          <stringProp name="parameters"></stringProp>
                          <stringProp name="filename"></stringProp>
                          <stringProp name="script">prev.setData("done")</stringProp>
                        </JSR223PostProcessor>
                        <hashTree/>
                        <JSR223PreProcessor guiclass="TestBeanGUI" testclass="JSR223PreProcessor" testname="缺省脚本" enabled="true">
                          <stringProp name="script">log.info("only script")</stringProp>
                        </JSR223PreProcessor>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;
        JmeterScriptParser parser = new JmeterScriptParser();
        List<ScriptStepDefinition> children = parser.parseSteps(jmx).get(0).children();

        assertEquals(3, children.size(), "three jsr223 steps");
        ScriptStepDefinition pre = children.get(0);
        assertEquals(ScriptStepType.JSR223_PRE_PROCESSOR.code(), pre.type(), "pre type");
        assertTrue(pre.id().startsWith("jsr223-pre-"), "pre id prefix");
        assertEquals("beanshell", pre.config().get("scriptLanguage"), "beanshell language preserved");
        assertEquals("String tag = \"ok\";", pre.config().get("script"), "script text preserved");
        assertEquals("env=prod", pre.config().get("parameters"), "parameters preserved");
        assertEquals(Boolean.TRUE, pre.config().get("cacheKey"), "cacheKey parsed as Boolean");

        ScriptStepDefinition post = children.get(1);
        assertEquals(ScriptStepType.JSR223_POST_PROCESSOR.code(), post.type(), "post type");
        assertTrue(post.id().startsWith("jsr223-post-"), "post id prefix");
        assertEquals(Boolean.FALSE, post.config().get("cacheKey"), "cacheKey=false as Boolean");

        ScriptStepDefinition minimal = children.get(2);
        assertEquals("groovy", minimal.config().get("scriptLanguage"), "scriptLanguage default groovy");
        assertEquals(Boolean.TRUE, minimal.config().get("cacheKey"), "cacheKey default true");
        assertEquals("", minimal.config().get("parameters"), "parameters default empty");
    }

    static void parseInvalidXmlThrows() {
        JmeterScriptParser parser = new JmeterScriptParser();
        assertThrows(ScriptValidationException.class, () -> parser.parseSteps("not xml at all"), "invalid XML should throw");
    }

    /** 根层混排样例：JMeter 原生导出结构（公共元件与线程组同级）。 */
    private static final String SHARED_COMPONENTS_JMX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
              <hashTree>
                <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true"/>
                <hashTree>
                  <UserParameters guiclass="UserParametersGui" testclass="UserParameters" testname="用户参数" enabled="true">
                    <collectionProp name="UserParameters.names">
                      <stringProp name="85286">username</stringProp>
                      <stringProp name="-530799223">password</stringProp>
                    </collectionProp>
                    <collectionProp name="UserParameters.thread_values">
                      <collectionProp name="-1681486524">
                        <stringProp name="-1240149213">user1</stringProp>
                        <stringProp name="1687859746">pass1</stringProp>
                      </collectionProp>
                      <collectionProp name="11865703">
                        <stringProp name="312055646">user2</stringProp>
                        <stringProp name="-1466997670">pass2</stringProp>
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
                  <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="HTTP Header Manager" enabled="true">
                    <collectionProp name="HeaderManager.headers">
                      <elementProp name="" elementType="Header">
                        <stringProp name="Header.name">Content-Type</stringProp>
                        <stringProp name="Header.value">application/json</stringProp>
                      </elementProp>
                    </collectionProp>
                  </HeaderManager>
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

    static void parsesUserParamsMatrixFromJmeterNativeFormat() {
        List<ScriptStepDefinition> steps = new JmeterScriptParser().parseSteps(SHARED_COMPONENTS_JMX);
        ScriptStepDefinition userParams = steps.get(0);
        assertEquals("USER_PARAMS", userParams.type(), "UserParameters maps to USER_PARAMS");
        assertEquals(List.of("username", "password"), userParams.config().get("names"), "names parsed in order");
        assertEquals(List.of(List.of("user1", "pass1"), List.of("user2", "pass2")), userParams.config().get("users"), "per-user columns parsed");
        assertEquals(Boolean.TRUE, userParams.config().get("perIteration"), "per_iteration parsed");
    }

    static void parsesUserVariablesFromArguments() {
        List<ScriptStepDefinition> steps = new JmeterScriptParser().parseSteps(SHARED_COMPONENTS_JMX);
        ScriptStepDefinition variables = steps.get(1);
        assertEquals("USER_VARIABLES", variables.type(), "Arguments maps to USER_VARIABLES");
        assertEquals(1, ((List<?>) variables.config().get("variables")).size(), "variable count");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) variables.config().get("variables");
        assertEquals("host", items.get(0).get("key"), "variable key");
        assertEquals("api.example.com", items.get(0).get("value"), "variable value");
    }

    static void parsesConstantAndRandomTimers() {
        List<ScriptStepDefinition> steps = new JmeterScriptParser().parseSteps(SHARED_COMPONENTS_JMX);
        ScriptStepDefinition constant = steps.get(3);
        ScriptStepDefinition random = steps.get(4);
        assertEquals("CONSTANT_TIMER", constant.type(), "ConstantTimer type");
        assertEquals("300", constant.config().get("delay"), "constant delay raw value");
        assertEquals("RANDOM_TIMER", random.type(), "UniformRandomTimer type");
        assertEquals("1000", random.config().get("delay"), "random delay raw value");
        assertEquals("500.0", random.config().get("range"), "random range raw value preserved");
    }

    static void parsesRootLevelSharedComponentsInDocumentOrder() {
        List<ScriptStepDefinition> steps = new JmeterScriptParser().parseSteps(SHARED_COMPONENTS_JMX);
        assertEquals(6, steps.size(), "root holds shared components plus thread group");
        assertEquals("USER_PARAMS", steps.get(0).type(), "document order 1");
        assertEquals("USER_VARIABLES", steps.get(1).type(), "document order 2");
        assertEquals("HEADER_CONFIG", steps.get(2).type(), "document order 3");
        assertEquals("CONSTANT_TIMER", steps.get(3).type(), "document order 4");
        assertEquals("RANDOM_TIMER", steps.get(4).type(), "document order 5");
        assertEquals("THREAD_GROUP", steps.get(5).type(), "document order 6");
        ScriptStepDefinition header = steps.get(2);
        assertEquals(1, ((List<?>) header.config().get("headers")).size(), "header manager items parsed");
    }
}
