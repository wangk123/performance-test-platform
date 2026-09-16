package com.yr.perftest.platform.datafile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.TestSupport;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.distributed.ExecutionScriptAssembler;
import com.yr.perftest.platform.execution.distributed.JmeterBackendListenerInjector;
import com.yr.perftest.platform.script.JmeterScriptNormalizer;
import com.yr.perftest.platform.script.JmeterScriptParser;
import com.yr.perftest.platform.script.JmeterScriptPatcher;
import com.yr.perftest.platform.script.JmeterScriptRenderer;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import com.yr.perftest.platform.script.ScriptStepType;
import com.yr.perftest.platform.script.ThreadGroupStepPatcher;
import com.yr.perftest.platform.task.ScenarioDataFileBinding;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfigSupport;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yr.perftest.platform.TestSupport.assertEquals;
import static com.yr.perftest.platform.TestSupport.assertTrue;

public class DataFileAssemblyServiceTest {

    private static final long PROJECT_ID = 71L;
    private static final long OTHER_PROJECT_ID = 55L;

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
                    <CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="订单数据" enabled="true">
                      <stringProp name="filename">orders.csv</stringProp>
                    </CSVDataSet>
                    <hashTree/>
                    <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="请求A" enabled="true">
                      <stringProp name="HTTPSampler.method">GET</stringProp>
                      <stringProp name="HTTPSampler.path">/api/ping</stringProp>
                    </HTTPSamplerProxy>
                    <hashTree/>
                  </hashTree>
                </hashTree>
              </hashTree>
            </jmeterTestPlan>
            """;

    private static final String DUPLICATE_JMX = """
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
                    </CSVDataSet>
                    <hashTree/>
                    <CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="订单数据" enabled="true">
                      <stringProp name="filename">users.csv</stringProp>
                    </CSVDataSet>
                    <hashTree/>
                  </hashTree>
                </hashTree>
              </hashTree>
            </jmeterTestPlan>
            """;

    public static void runAll() {
        try {
            boundStepResolvesLatestVersionAndCopiesFile();
            missingBindingFailsWithStepName();
            bareFilenameUniqueFallback();
            duplicateTargetFileNameRejected();
            parentPathTargetFileNameRejected();
            absolutePathTargetFileNameRejected();
        } catch (AssertionError error) {
            throw error;
        } catch (Exception exception) {
            throw new AssertionError("DataFileAssemblyServiceTest case failed", exception);
        }
        bindingJsonRoundTrip();
        System.out.println("DataFileAssemblyServiceTest passed");
    }

    static void boundStepResolvesLatestVersionAndCopiesFile() throws Exception {
        Path root = Files.createTempDirectory("assembly-bound");
        Path scriptPath = writeScript(root.resolve("plan.jmx"), SAMPLE_JMX);
        Path storedV1 = Files.writeString(root.resolve("stored-users-v1.csv"), "v1-mobile,v1-name");
        Path storedV2 = Files.writeString(root.resolve("stored-users-v2.csv"), "v2-mobile,v2-name");
        String usersStepId = csvStepId(SAMPLE_JMX, 0);
        DataFileAssemblyService assembly = assemblyService(
                new StubDataFileService(
                        Map.of(100L, version(100L, 2, "users.csv", storedV2, "sha256-v2")),
                        Map.of("users.csv", List.of(
                                version(100L, 1, "users.csv", storedV1, "sha256-v1"),
                                version(100L, 2, "users.csv", storedV2, "sha256-v2")))),
                projectOwnedFiles(Map.of(100L, PROJECT_ID)));
        String bindingsJson = bindingsJson(new ScenarioDataFileBinding(usersStepId, "用户数据", 100L));

        List<CsvAssemblyPlan> plans = assembly.plan(PROJECT_ID, bindingsJson, scriptPath, root);

        assertEquals(1, plans.size(), "bound csv step produces one plan");
        assertEquals(usersStepId, plans.get(0).stepId(), "plan keeps bound csv step id");
        assertEquals(2, plans.get(0).versionNo(), "plan resolves latest version");
        assertEquals("users.csv", plans.get(0).targetFileName(), "target keeps script filename");
        assertEquals(100L, plans.get(0).dataFileId(), "plan carries bound dataFileId");
        assertEquals("sha256-v2", plans.get(0).sha256(), "plan carries latest sha256");

        ExecutionScriptAssembler assembler = new ExecutionScriptAssembler(
                new JmeterScriptNormalizer(),
                new JmeterScriptParser(),
                new ThreadGroupStepPatcher(),
                new JmeterScriptPatcher(new JmeterScriptRenderer(), new JmeterScriptNormalizer()),
                new JmeterBackendListenerInjector(new JmeterScriptNormalizer()),
                new ScenarioThreadGroupConfigSupport(new ObjectMapper(), new JmeterScriptParser()),
                assembly);
        Path executionDirectory = root.resolve("exec");
        Files.createDirectories(executionDirectory);
        Path originalTestPlan = executionDirectory.resolve("plan.jmx");
        Path distributedTestPlan = executionDirectory.resolve("distributed-plan.jmx");
        assembler.prepare(null, "[]", PROJECT_ID, bindingsJson, scriptPath, originalTestPlan, distributedTestPlan);

        assertTrue(Files.exists(distributedTestPlan), "assembler still produces distributed plan");
        assertEquals("v2-mobile,v2-name", Files.readString(executionDirectory.resolve("users.csv")),
                "latest version copied into execution directory");
        String manifest = Files.readString(executionDirectory.resolve("data-files.json"));
        assertTrue(manifest.contains("users.csv") && manifest.contains("\"versionNo\":2"),
                "manifest records the assembled plan: " + manifest);
    }

    static void missingBindingFailsWithStepName() throws Exception {
        Path root = Files.createTempDirectory("assembly-missing");
        Path scriptPath = writeScript(root.resolve("plan.jmx"), SAMPLE_JMX);
        String usersStepId = csvStepId(SAMPLE_JMX, 0);
        DataFileAssemblyService assembly = assemblyService(
                new StubDataFileService(Map.of(), Map.of()),
                projectOwnedFiles(Map.of()));

        ExecutionValidationException exception = TestSupport.assertThrows(ExecutionValidationException.class,
                () -> assembly.plan(PROJECT_ID,
                        bindingsJson(new ScenarioDataFileBinding(usersStepId, "用户数据", 999L)),
                        scriptPath, root),
                "missing bound data file should be rejected");

        assertTrue(exception.getMessage().contains("用户数据"),
                "message contains step name: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("999"),
                "message contains dataFileId: " + exception.getMessage());
    }

    static void bareFilenameUniqueFallback() throws Exception {
        Path root = Files.createTempDirectory("assembly-fallback");
        Path scriptPath = writeScript(root.resolve("plan.jmx"), SAMPLE_JMX);
        Path storedOrders = Files.writeString(root.resolve("stored-orders.csv"), "order-no\n1001");

        DataFileAssemblyService unique = assemblyService(
                new StubDataFileService(Map.of(), Map.of("orders.csv", List.of(
                        version(200L, 1, "orders.csv", storedOrders, "sha256-a"),
                        version(201L, 3, "orders.csv", storedOrders, "sha256-b")))),
                projectOwnedFiles(Map.of(200L, PROJECT_ID, 201L, OTHER_PROJECT_ID)));

        List<CsvAssemblyPlan> plans = unique.plan(PROJECT_ID, "[]", scriptPath, root);

        assertEquals(1, plans.size(), "unique project filename match produces fallback plan");
        assertEquals("订单数据", plans.get(0).stepName(), "fallback plan keeps step name");
        assertEquals("orders.csv", plans.get(0).targetFileName(), "fallback target filename");
        assertEquals(200L, plans.get(0).dataFileId(), "fallback keeps the project-owned version");

        DataFileAssemblyService ambiguous = assemblyService(
                new StubDataFileService(Map.of(), Map.of("orders.csv", List.of(
                        version(200L, 1, "orders.csv", storedOrders, "sha256-a"),
                        version(201L, 3, "orders.csv", storedOrders, "sha256-b")))),
                projectOwnedFiles(Map.of(200L, PROJECT_ID, 201L, PROJECT_ID)));

        assertEquals(List.of(), ambiguous.plan(PROJECT_ID, "[]", scriptPath, root),
                "ambiguous project matches are skipped");
    }

    static void duplicateTargetFileNameRejected() throws Exception {
        Path root = Files.createTempDirectory("assembly-duplicate");
        Path scriptPath = writeScript(root.resolve("plan.jmx"), DUPLICATE_JMX);
        Path storedUsers = Files.writeString(root.resolve("stored-users.csv"), "a,b");
        DataFileAssemblyService assembly = assemblyService(
                new StubDataFileService(Map.of(), Map.of("users.csv", List.of(
                        version(100L, 1, "users.csv", storedUsers, "sha256-a")))),
                projectOwnedFiles(Map.of(100L, PROJECT_ID)));

        ExecutionValidationException exception = TestSupport.assertThrows(ExecutionValidationException.class,
                () -> assembly.plan(PROJECT_ID, "[]", scriptPath, root),
                "duplicate target file names should be rejected");

        assertTrue(exception.getMessage().contains("用户数据") && exception.getMessage().contains("订单数据"),
                "message lists conflicting step names: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("users.csv"),
                "message lists conflicting filename: " + exception.getMessage());
    }

    static void parentPathTargetFileNameRejected() throws Exception {
        assertIllegalTargetFileNameRejected("../evil.csv", "assembly-traversal-parent");
    }

    static void absolutePathTargetFileNameRejected() throws Exception {
        assertIllegalTargetFileNameRejected("/tmp/evil.csv", "assembly-traversal-absolute");
    }

    private static void assertIllegalTargetFileNameRejected(String fileName, String tempDirPrefix) throws Exception {
        Path root = Files.createTempDirectory(tempDirPrefix);
        Path scriptPath = writeScript(root.resolve("plan.jmx"), singleCsvStepJmx(fileName));
        DataFileAssemblyService assembly = assemblyService(
                new StubDataFileService(Map.of(), Map.of()),
                projectOwnedFiles(Map.of()));

        ExecutionValidationException exception = TestSupport.assertThrows(ExecutionValidationException.class,
                () -> assembly.plan(PROJECT_ID, "[]", scriptPath, root),
                "illegal target file name should be rejected: " + fileName);

        assertTrue(exception.getMessage().contains("用户数据"),
                "message contains step name: " + exception.getMessage());
        assertTrue(exception.getMessage().contains(fileName),
                "message contains illegal file name: " + exception.getMessage());
    }

    static void bindingJsonRoundTrip() {
        ScenarioThreadGroupConfigSupport support =
                new ScenarioThreadGroupConfigSupport(new ObjectMapper(), new JmeterScriptParser());

        String json = support.writeStoredDataFileBindings(
                List.of(new ScenarioDataFileBinding("csv-1", "用户数据", 3L)));
        List<ScenarioDataFileBinding> roundTrip = support.readStoredDataFileBindings(json);

        assertEquals(1, roundTrip.size(), "round trip keeps one binding");
        assertEquals("csv-1", roundTrip.get(0).stepId(), "round trip keeps stepId");
        assertEquals("用户数据", roundTrip.get(0).stepName(), "round trip keeps stepName");
        assertEquals(3L, roundTrip.get(0).dataFileId(), "round trip keeps dataFileId");
    }

    public static StubDataFileService stubService(
            Map<Long, DataFileVersion> latestVersions,
            Map<String, List<DataFileVersion>> versionsByFilename
    ) {
        return new StubDataFileService(latestVersions, versionsByFilename);
    }

    public static DataFileAssemblyService assemblyService(DataFileService service, DataFileRepository repository) {
        return new DataFileAssemblyService(
                service,
                repository,
                new ScenarioThreadGroupConfigSupport(new ObjectMapper(), new JmeterScriptParser()),
                new ObjectMapper());
    }

    public static DataFileRepository projectOwnedFiles(Map<Long, Long> projectIdByDataFileId) {
        return (DataFileRepository) Proxy.newProxyInstance(
                DataFileRepository.class.getClassLoader(),
                new Class<?>[]{DataFileRepository.class},
                (proxy, method, args) -> {
                    if (!"findById".equals(method.getName()) || args == null || args.length != 1) {
                        return null;
                    }
                    Long projectId = projectIdByDataFileId.get(args[0]);
                    if (projectId == null) {
                        return Optional.empty();
                    }
                    return Optional.of(new PersistentDataFileRecord(
                            projectId, "df-" + args[0], null, null, LocalDateTime.now()));
                });
    }

    public static class StubDataFileService extends DataFileService {
        private final Map<Long, DataFileVersion> latestVersions;
        private final Map<String, List<DataFileVersion>> versionsByFilename;

        public StubDataFileService(
                Map<Long, DataFileVersion> latestVersions,
                Map<String, List<DataFileVersion>> versionsByFilename
        ) {
            super(null, null, "storage", 0, new ObjectMapper());
            this.latestVersions = latestVersions;
            this.versionsByFilename = versionsByFilename;
        }

        @Override
        public DataFileVersion latestVersion(long dataFileId) {
            DataFileVersion version = latestVersions.get(dataFileId);
            if (version == null) {
                throw new DataFileValidationException("data file has no versions: dataFileId=" + dataFileId);
            }
            return version;
        }

        @Override
        public List<DataFileVersion> findByOriginalFilename(String originalFilename) {
            return versionsByFilename.getOrDefault(originalFilename, List.of());
        }
    }

    private static Path writeScript(Path scriptPath, String jmx) throws Exception {
        Files.createDirectories(scriptPath.getParent());
        return Files.writeString(scriptPath, jmx);
    }

    private static String singleCsvStepJmx(String fileName) {
        return """
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
                          <stringProp name="filename">%s</stringProp>
                        </CSVDataSet>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """.formatted(fileName);
    }

    private static String csvStepId(String jmx, int index) {
        List<ScriptStepDefinition> csvSteps = new JmeterScriptParser().parseSteps(jmx).get(0).children().stream()
                .filter(step -> ScriptStepType.CSV_DATA.code().equals(step.type()))
                .toList();
        return csvSteps.get(index).id();
    }

    private static DataFileVersion version(
            long dataFileId, int versionNo, String originalFilename, Path storedPath, String sha256) {
        return new DataFileVersion(
                dataFileId + versionNo,
                dataFileId,
                versionNo,
                originalFilename,
                storedPath.toString(),
                16,
                2L,
                null,
                sha256,
                null,
                LocalDateTime.now(),
                null);
    }

    private static String bindingsJson(ScenarioDataFileBinding... bindings) {
        try {
            return new ObjectMapper().writeValueAsString(List.of(bindings));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to serialize bindings", exception);
        }
    }

    private DataFileAssemblyServiceTest() {
    }
}
