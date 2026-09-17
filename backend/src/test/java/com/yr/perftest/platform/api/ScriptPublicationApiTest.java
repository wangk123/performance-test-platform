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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:script-pub-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/script-pub"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ScriptPublicationApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String authToken;

    @BeforeEach
    void authenticate() throws Exception {
        authToken = AuthTestSupport.loginToken(mockMvc, objectMapper);
    }

    @Test
    void savesDraftThenPublishesImmutableVersion() throws Exception {
        createProjectAndBlankScript();

        mockMvc.perform(put("/api/projects/1/scripts/1/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"filename\":\"登录链路压测.jmx\",\"steps\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.status", is("DRAFT")));

        mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"versionNo\":1,\"remark\":\"首发版本\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PUBLISHED")))
                .andExpect(jsonPath("$.versionNo", is(1)))
                .andExpect(jsonPath("$.remark", is("首发版本")));
    }

    @Test
    void publishRejectsNonIncreasingVersionNoAndBlankRemark() throws Exception {
        createProjectAndBlankScript();
        saveDraft();
        publishOk(1, "首发");
        saveDraft();

        mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"versionNo\":1,\"remark\":\"重复号\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("version no must be greater than 1")));

        mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"versionNo\":2,\"remark\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("change remark is required")));
    }

    @Test
    void publishRejectsWhenNoDraftExists() throws Exception {
        createProjectAndBlankScript();
        saveDraft();
        publishOk(1, "首发");

        mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"versionNo\":2,\"remark\":\"草稿已转发布\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("no draft to publish")));
    }

    @Test
    void forkDraftCopiesPublishedContentAsNewDraft() throws Exception {
        createProjectAndBlankScript();
        saveDraft();
        long publishedVersionId = publishOk(1, "首发");

        mockMvc.perform(post("/api/projects/1/scripts/1/fork-draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"sourceVersionId\":" + publishedVersionId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")));
    }

    @Test
    void savingDraftOnPublishedVersionEndpointIsRejected() throws Exception {
        createProjectAndBlankScript();
        saveDraft();
        long publishedVersionId = publishOk(1, "首发");

        mockMvc.perform(put("/api/projects/1/scripts/" + publishedVersionId + "/definition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"filename\":\"x.jmx\",\"steps\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("published version is immutable, edit the draft instead")));
    }

    @Test
    void bindingDraftVersionIsRejected() throws Exception {
        createProjectAndBlankScript();
        long planId = createPlan();
        long draftVersionId = draftVersionIdOfScript(1);

        mockMvc.perform(post("/api/task-plans/" + planId + "/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content(objectMapper.writeValueAsString(
                                new CreateScenarioBody(draftVersionId, "下单场景"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("script version is not published")));
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

    private long draftVersionIdOfScript(long scriptId) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/projects/1/scripts/" + scriptId + "/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"filename\":\"登录链路压测.jmx\",\"steps\":[]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("version").path("id").asLong();
    }

    private record CreateScenarioBody(long scriptVersionId, String name) {
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

    private void saveDraft() throws Exception {
        mockMvc.perform(put("/api/projects/1/scripts/1/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"filename\":\"登录链路压测.jmx\",\"steps\":[]}"))
                .andExpect(status().isCreated());
    }

    private long publishOk(int versionNo, String remark) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content(objectMapper.writeValueAsString(new PublishRequest(versionNo, remark))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private record PublishRequest(int versionNo, String remark) {
    }
}
