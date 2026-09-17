package com.yr.perftest.platform.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:script-asset-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/script-asset"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ScriptAssetApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String authToken;

    @BeforeEach
    void authenticate() throws Exception {
        authToken = AuthTestSupport.loginToken(mockMvc, objectMapper);
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"code\":\"loan-core\",\"name\":\"信贷核心压测\",\"description\":\"授信和放款链路\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void listsScriptDimensionAssetsWithUsage() throws Exception {
        long versionId = uploadJmx("a.jmx");
        createBlankScript("b");
        long planId = createPlan();
        bindScenario(planId, versionId, "下单链路");

        mockMvc.perform(get("/api/projects/1/scripts/assets")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("a")))
                .andExpect(jsonPath("$[0].hasDraft", is(false)))
                .andExpect(jsonPath("$[0].latestVersionNo", is(1)))
                .andExpect(jsonPath("$[0].latestVersionLabel", is("1.0.0")))
                .andExpect(jsonPath("$[0].currentScenarioCount", is(1)))
                .andExpect(jsonPath("$[0].outdatedScenarioCount", is(0)))
                .andExpect(jsonPath("$[1].name", is("b")))
                .andExpect(jsonPath("$[1].hasDraft", is(true)))
                .andExpect(jsonPath("$[1].latestVersionNo", is(0)));
    }

    @Test
    void listsVersionHistoryWithReferencedScenarioNames() throws Exception {
        long versionId = uploadJmx("a.jmx");
        long planId = createPlan();
        bindScenario(planId, versionId, "下单链路");

        mockMvc.perform(get("/api/projects/1/scripts/1/versions")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].version.versionNo", is(1)))
                .andExpect(jsonPath("$[0].version.status", is("PUBLISHED")))
                .andExpect(jsonPath("$[0].referencedScenarioNames[0]", is("下单链路")));
    }

    private long uploadJmx(String name) throws Exception {
        MockMultipartFile script = new MockMultipartFile(
                "file", name, MediaType.APPLICATION_XML_VALUE,
                "<jmeterTestPlan></jmeterTestPlan>".getBytes());
        MvcResult result = mockMvc.perform(multipart("/api/projects/1/scripts")
                        .file(script)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private void createBlankScript(String name) throws Exception {
        mockMvc.perform(post("/api/projects/1/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content(objectMapper.writeValueAsString(new NameBody(name))))
                .andExpect(status().isCreated());
    }

    private long createPlan() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/1/task-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"name\":\"下单链路压测计划\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private void bindScenario(long planId, long versionId, String name) throws Exception {
        mockMvc.perform(post("/api/task-plans/" + planId + "/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content(objectMapper.writeValueAsString(new ScenarioBody(versionId, name))))
                .andExpect(status().isCreated());
    }

    private record NameBody(String name) {
    }

    private record ScenarioBody(long scriptVersionId, String name) {
    }
}
