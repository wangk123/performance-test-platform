package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.script.JmeterScriptParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.yr.perftest.platform.TestSupport.assertEquals;

public class ScenarioDataFileBindingTest {

    private static final String SAMPLE_JMX = """
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
                    </CSVDataSet>
                    <hashTree/>
                    <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="请求A" enabled="true">
                      <stringProp name="HTTPSampler.method">GET</stringProp>
                      <stringProp name="HTTPSampler.path">/api/users</stringProp>
                    </HTTPSamplerProxy>
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

    public static void runAll() {
        try {
            normalizeKeepsOnlyBoundCsvSteps();
        } catch (Exception exception) {
            throw new AssertionError("normalizeKeepsOnlyBoundCsvSteps failed", exception);
        }
        normalizeEmptyWhenNoScript();
        System.out.println("ScenarioDataFileBindingTest passed");
    }

    static void normalizeKeepsOnlyBoundCsvSteps() throws Exception {
        Path tempDir = Files.createTempDirectory("data-file-bindings");
        Path scriptPath = tempDir.resolve("plan.jmx");
        Files.writeString(scriptPath, SAMPLE_JMX);
        var parser = new JmeterScriptParser();
        var csvStep = parser.parseSteps(SAMPLE_JMX).get(0).children().get(0);
        var httpStep = parser.parseSteps(SAMPLE_JMX).get(0).children().get(1);
        var support = new ScenarioThreadGroupConfigSupport(new ObjectMapper(), parser);

        List<ScenarioDataFileBinding> normalized = support.normalizeDataFileBindings(scriptPath, List.of(
                new ScenarioDataFileBinding(csvStep.id(), "过期步骤名", 3L),
                new ScenarioDataFileBinding(httpStep.id(), "请求A", 4L),
                new ScenarioDataFileBinding(csvStep.id(), "用户数据", null)
        ));

        assertEquals(1, normalized.size(), "only the bound csv step survives");
        assertEquals(csvStep.id(), normalized.get(0).stepId(), "kept stepId");
        assertEquals("用户数据", normalized.get(0).stepName(), "stepName refreshed from script");
        assertEquals(3L, normalized.get(0).dataFileId(), "dataFileId preserved");
    }

    static void normalizeEmptyWhenNoScript() {
        var support = new ScenarioThreadGroupConfigSupport(new ObjectMapper(), new JmeterScriptParser());

        assertEquals(List.of(), support.normalizeDataFileBindings(null, List.of(
                new ScenarioDataFileBinding("csv-1", "用户数据", 3L)
        )), "null scriptPath yields empty");
        assertEquals(List.of(), support.normalizeDataFileBindings(Path.of("missing/plan.jmx"), null),
                "null inputs yield empty");
        assertEquals(List.of(), support.normalizeDataFileBindings(null, null), "both null yield empty");
    }
}
