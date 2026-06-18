package com.yr.perftest.platform.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.yr.perftest.platform.TestSupport.*;

public class JmeterScriptPatcherTest {
    public static void runAll() {
        patchesKnownStepsWithoutDroppingUnknownNodes();
        patchesJsonAssertionUnderHttpSampler();
        System.out.println("JmeterScriptPatcherTest passed");
    }

    static void patchesKnownStepsWithoutDroppingUnknownNodes() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true"/>
                    <hashTree>
                      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Main" enabled="true">
                        <stringProp name="ThreadGroup.num_threads">10</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">1</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <stringProp name="LoopController.loops">1</stringProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="GET /old" enabled="true">
                          <stringProp name="HTTPSampler.method">GET</stringProp>
                          <stringProp name="HTTPSampler.domain">example.com</stringProp>
                          <stringProp name="HTTPSampler.path">/old</stringProp>
                          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
                            <collectionProp name="Arguments.arguments"/>
                          </elementProp>
                        </HTTPSamplerProxy>
                        <hashTree>
                          <ConstantTimer guiclass="ConstantTimerGui" testclass="ConstantTimer" testname="Think Time" enabled="true">
                            <stringProp name="ConstantTimer.delay">500</stringProp>
                          </ConstantTimer>
                          <hashTree/>
                        </hashTree>
                        <DebugSampler guiclass="TestBeanGUI" testclass="DebugSampler" testname="Debug" enabled="true"/>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;
        JmeterScriptParser parser = new JmeterScriptParser();
        List<ScriptStepDefinition> steps = parser.parseSteps(jmx);
        ScriptStepDefinition threadGroup = steps.get(0);
        ScriptStepDefinition http = threadGroup.children().get(0);
        Map<String, Object> httpConfig = new LinkedHashMap<>(http.config());
        httpConfig.put("path", "/new");
        httpConfig.put("url", "http://example.com/new");
        ScriptStepDefinition updatedHttp = new ScriptStepDefinition(
                http.id(),
                http.type(),
                http.name(),
                httpConfig,
                http.children()
        );
        ScriptStepDefinition updatedThreadGroup = new ScriptStepDefinition(
                threadGroup.id(),
                threadGroup.type(),
                threadGroup.name(),
                threadGroup.config(),
                new ArrayList<>(List.of(updatedHttp))
        );

        String patched = new JmeterScriptPatcher(new JmeterScriptRenderer()).patch(jmx, List.of(updatedThreadGroup));

        assertTrue(patched.contains("ConstantTimer"), "unknown timer remains");
        assertTrue(patched.contains("DebugSampler"), "unknown sampler remains");
        assertTrue(patched.contains("<stringProp name=\"HTTPSampler.path\">/new</stringProp>"), "known HTTP path is patched");
        assertFalse(patched.contains("<stringProp name=\"HTTPSampler.path\">/old</stringProp>"), "old HTTP path is replaced");
    }

    static void patchesJsonAssertionUnderHttpSampler() {
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
                        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="GET /api" enabled="true">
                          <stringProp name="HTTPSampler.method">GET</stringProp>
                          <stringProp name="HTTPSampler.path">/api</stringProp>
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
        ScriptStepDefinition jsonAssertion = new ScriptStepDefinition(
                "json-assert-test",
                ScriptStepType.JSON_ASSERTION.code(),
                "业务码断言",
                Map.of(
                        "jsonPath", "$.code",
                        "validateValue", true,
                        "expectedValue", "0",
                        "useRegex", false
                ),
                List.of()
        );
        JmeterScriptParser parser = new JmeterScriptParser();
        ScriptStepDefinition threadGroup = parser.parseSteps(jmx).get(0);
        ScriptStepDefinition http = threadGroup.children().get(0);
        ScriptStepDefinition updatedHttp = new ScriptStepDefinition(
                http.id(),
                http.type(),
                http.name(),
                http.config(),
                List.of(jsonAssertion)
        );
        ScriptStepDefinition updatedThreadGroup = new ScriptStepDefinition(
                threadGroup.id(),
                threadGroup.type(),
                threadGroup.name(),
                threadGroup.config(),
                List.of(updatedHttp)
        );

        String patched = new JmeterScriptPatcher(new JmeterScriptRenderer()).patch(jmx, List.of(updatedThreadGroup));

        assertTrue(patched.contains("JSONPathAssertion"), "json assertion is rendered");
        assertTrue(patched.contains("<stringProp name=\"JSON_PATH\">$.code</stringProp>"), "json path is rendered");
    }
}
