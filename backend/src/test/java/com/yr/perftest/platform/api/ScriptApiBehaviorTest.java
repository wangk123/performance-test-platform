package com.yr.perftest.platform.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:script-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/script-api"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ScriptApiBehaviorTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadsAndListsJmeterScriptVersions() throws Exception {
        createProject();

        MockMultipartFile script = new MockMultipartFile(
                "file",
                "loan-search.jmx",
                MediaType.APPLICATION_XML_VALUE,
                "<jmeterTestPlan></jmeterTestPlan>".getBytes()
        );

        mockMvc.perform(multipart("/api/projects/1/scripts")
                        .file(script)
                        .header("X-User", "admin"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId", is(1)))
                .andExpect(jsonPath("$.versionNo", is(1)))
                .andExpect(jsonPath("$.originalFilename", is("loan-search.jmx")));

        mockMvc.perform(get("/api/projects/1/scripts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].versionNo", is(1)));
    }

    @Test
    void createsBlankScriptFromJson() throws Exception {
        createProject();

        mockMvc.perform(post("/api/projects/1/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"name\":\"登录链路压测\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId", is(1)))
                .andExpect(jsonPath("$.name", is("登录链路压测")))
                .andExpect(jsonPath("$.sourceFile", is("登录链路压测.jmx")))
                .andExpect(jsonPath("$.steps[0].type", is("THREAD_GROUP")));
    }

    @Test
    void rejectsNonJmxUploads() throws Exception {
        createProject();

        MockMultipartFile script = new MockMultipartFile(
                "file",
                "notes.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not a jmeter script".getBytes()
        );

        mockMvc.perform(multipart("/api/projects/1/scripts")
                        .file(script)
                        .header("X-User", "admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("only .jmx files are supported")));
    }

    private void createProject() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"loan-core\",\"name\":\"信贷核心压测\",\"description\":\"授信和放款链路\"}"))
                .andExpect(status().isCreated());
    }
}
