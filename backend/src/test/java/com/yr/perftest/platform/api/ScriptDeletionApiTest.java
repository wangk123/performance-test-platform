package com.yr.perftest.platform.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:script-del-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/script-del"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ScriptDeletionApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String authToken;

    @BeforeEach
    void authenticate() throws Exception {
        authToken = AuthTestSupport.loginToken(mockMvc, objectMapper);
        createProjectAndBlankScript();
    }

    @Test
    void deletingReferencedPublishedVersionIsRejectedWithScenarioNames() throws Exception {
        long versionId = publishAndBindScenario("下单链路");

        mockMvc.perform(delete("/api/projects/1/scripts/1/versions/" + versionId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("该版本被场景「下单链路」引用，无法删除")));
    }

    @Test
    void deletingUnreferencedVersionSucceedsAndKeepsWatermark() throws Exception {
        long versionId = publishFirst();

        mockMvc.perform(delete("/api/projects/1/scripts/1/versions/" + versionId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"versionNo\":1,\"remark\":\"复用号应被拒绝\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("version no must be greater than 1")));
    }

    @Test
    void deletingScriptWithAnyReferencedVersionIsRejected() throws Exception {
        publishAndBindScenario("下单链路");

        mockMvc.perform(delete("/api/projects/1/scripts/1")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("脚本下存在被场景引用的版本（「下单链路」），无法删除")));
    }

    @Test
    void deletingUnreferencedScriptRemovesAllVersions() throws Exception {
        publishFirst();

        mockMvc.perform(delete("/api/projects/1/scripts/1")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/projects/1/scripts/1")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("script does not exist")));
    }

    private void createProjectAndBlankScript() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"code\":\"loan-core\",\"name\":\"信贷核心压测\",\"description\":\"授信和放款链路\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/projects/1/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"name\":\"登录链路压测\"}"))
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

    private long publishFirst() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"versionNo\":1,\"remark\":\"首发\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private long publishAndBindScenario(String scenarioName) throws Exception {
        long versionId = publishFirst();
        long planId = createPlan();
        mockMvc.perform(post("/api/task-plans/" + planId + "/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content(objectMapper.writeValueAsString(
                                new CreateScenarioBody(versionId, scenarioName))))
                .andExpect(status().isCreated());
        return versionId;
    }

    private record CreateScenarioBody(long scriptVersionId, String name) {
    }
}
