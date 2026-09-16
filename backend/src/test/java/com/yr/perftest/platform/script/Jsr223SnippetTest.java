package com.yr.perftest.platform.script;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static com.yr.perftest.platform.TestSupport.*;

public class Jsr223SnippetTest {

    public static void runAll() {
        scannerFindsPlaintextSecretInJsr223Script();
        scannerNumbersMultipleHitsWithinStep();
        scannerSkipsShortValuesVariableRefsAndNonJsr223Steps();
        scannerReturnsEmptyWhenParseFails();
        registryLoadsSixSnippetsWithParameterPlaceholders();
        System.out.println("Jsr223SnippetTest passed");
    }

    static void scannerFindsPlaintextSecretInJsr223Script() {
        Jsr223SecretScanner scanner = new Jsr223SecretScanner(new JmeterScriptParser());
        String jmx = jsr223Jmx("签名脚本", "secret = 'a3f8d02c9e17b6f4ad55'");

        List<String> warnings = scanner.scanScriptContent(jmx);

        assertEquals(
                List.of("步骤 [签名脚本] 第 1 处疑似明文密钥（secret = 'a3f8d0…'），建议改用 ${__P(...)} 参数化引用"),
                warnings,
                "one masked warning with step name"
        );
    }

    static void scannerNumbersMultipleHitsWithinStep() {
        Jsr223SecretScanner scanner = new Jsr223SecretScanner(new JmeterScriptParser());
        String script = "secret = 'a3f8d02c9e17b6f4ad55'\ntoken = \"ffeeddccbbaa99887766\"";
        String jmx = jsr223Jmx("签名脚本", script);

        List<String> warnings = scanner.scanScriptContent(jmx);

        assertEquals(2, warnings.size(), "two warnings in one step");
        assertTrue(warnings.get(0).contains("第 1 处"), "first hit numbered 1");
        assertTrue(warnings.get(1).contains("第 2 处"), "second hit numbered 2");
        assertTrue(warnings.get(1).contains("token = 'ffeedd…'"), "second hit masked with keyword");
    }

    static void scannerSkipsShortValuesVariableRefsAndNonJsr223Steps() {
        Jsr223SecretScanner scanner = new Jsr223SecretScanner(new JmeterScriptParser());

        List<String> shortValue = scanner.scanScriptContent(jsr223Jmx("短密钥", "secret = 'a3f8d0'"));
        assertTrue(shortValue.isEmpty(), "value shorter than 16 chars is ignored");

        List<String> variableRef = scanner.scanScriptContent(jsr223Jmx("参数化", "secret = '${__P(secretKey)}'"));
        assertTrue(variableRef.isEmpty(), "parameterized reference is ignored");

        List<String> nonJsr223 = scanner.scanScriptContent(samplerBodyJmx());
        assertTrue(nonJsr223.isEmpty(), "same text outside JSR223 script is ignored");
    }

    static void scannerReturnsEmptyWhenParseFails() {
        Jsr223SecretScanner scanner = new Jsr223SecretScanner(new JmeterScriptParser());

        assertTrue(scanner.scanScriptContent("not xml at all").isEmpty(), "unparseable content yields no warnings");
    }

    static void registryLoadsSixSnippetsWithParameterPlaceholders() {
        Jsr223SnippetRegistry registry = new Jsr223SnippetRegistry(new ObjectMapper());

        List<Jsr223Snippet> snippets = registry.list();
        assertTrue(snippets.size() >= 6, "at least six jsr223 snippets, actual=" + snippets.size());

        List<String> keys = snippets.stream().map(Jsr223Snippet::key).toList();
        for (String expected : List.of(
                "hmac-sha256-sign", "aes256-encrypt", "md5-digest", "rsa-sign", "uuid-timestamp", "base64")) {
            assertTrue(keys.contains(expected), "snippet key exists: " + expected);
        }

        for (Jsr223Snippet snippet : snippets) {
            assertFalse(snippet.key().isBlank(), "key non-blank: " + snippet.key());
            assertFalse(snippet.name().isBlank(), "name non-blank: " + snippet.key());
            assertFalse(snippet.category().isBlank(), "category non-blank: " + snippet.key());
            assertFalse(snippet.description().isBlank(), "description non-blank: " + snippet.key());
            assertFalse(snippet.code().isBlank(), "code non-blank: " + snippet.key());
            assertTrue(snippet.params() != null, "params non-null: " + snippet.key());
            assertTrue(snippet.code().contains("${__P("), "code uses ${__P( placeholder: " + snippet.key());
        }
    }

    private static String jsr223Jmx(String stepName, String script) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true"/>
                    <hashTree>
                      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="下单链路" enabled="true">
                        <stringProp name="ThreadGroup.num_threads">1</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">0</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <stringProp name="LoopController.loops">1</stringProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <JSR223PreProcessor guiclass="TestBeanGUI" testclass="JSR223PreProcessor" testname="%s" enabled="true">
                          <stringProp name="scriptLanguage">groovy</stringProp>
                          <stringProp name="script">%s</stringProp>
                        </JSR223PreProcessor>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """.formatted(stepName, script);
    }

    private static String samplerBodyJmx() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true"/>
                    <hashTree>
                      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="下单链路" enabled="true">
                        <stringProp name="ThreadGroup.num_threads">1</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">0</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <stringProp name="LoopController.loops">1</stringProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="登录请求" enabled="true">
                          <stringProp name="HTTPSampler.method">POST</stringProp>
                          <stringProp name="HTTPSampler.path">/api/login</stringProp>
                          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
                          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
                            <collectionProp name="Arguments.arguments">
                              <elementProp name="" elementType="HTTPArgument">
                                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                                <stringProp name="Argument.value">secret = 'a3f8d02c9e17b6f4ad55'</stringProp>
                                <stringProp name="Argument.metadata">=</stringProp>
                              </elementProp>
                            </collectionProp>
                          </elementProp>
                        </HTTPSamplerProxy>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;
    }
}
