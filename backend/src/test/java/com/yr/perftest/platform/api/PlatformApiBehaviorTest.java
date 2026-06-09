package com.yr.perftest.platform.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:platform-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlatformApiBehaviorTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void authenticatesDemoUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("admin")))
                .andExpect(jsonPath("$.displayName", is("平台管理员")))
                .andExpect(jsonPath("$.roles[0]", is("ADMIN")));
    }

    @Test
    void createsListsAndArchivesProjects() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"loan-core\",\"name\":\"信贷核心压测\",\"description\":\"授信和放款链路\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("loan-core")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("信贷核心压测")));

        mockMvc.perform(patch("/api/projects/1/archive")
                        .header("X-User", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ARCHIVED")));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/projects?includeArchived=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("ARCHIVED")));
    }

    @Test
    void managesProjectMembersAndRestrictsOwnerActions() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"risk-core\",\"name\":\"风控核心压测\",\"description\":\"规则引擎链路\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)));

        mockMvc.perform(get("/api/projects/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username", is("admin")))
                .andExpect(jsonPath("$[0].role", is("OWNER")));

        mockMvc.perform(post("/api/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"username\":\"tester\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("tester")))
                .andExpect(jsonPath("$.role", is("MEMBER")));

        mockMvc.perform(get("/api/projects/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(patch("/api/projects/1/archive")
                        .header("X-User", "tester"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("project owner permission is required")));
    }

    @Test
    void getsUpdatesAndRemovesProjectMembers() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"loan-core\",\"name\":\"信贷核心压测\",\"description\":\"授信链路\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("loan-core")))
                .andExpect(jsonPath("$.name", is("信贷核心压测")));

        mockMvc.perform(put("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"name\":\"信贷核心容量测试\",\"description\":\"授信和放款链路\",\"ownerUsername\":\"tester\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("信贷核心容量测试")))
                .andExpect(jsonPath("$.ownerUsername", is("tester")));

        mockMvc.perform(get("/api/projects/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].username", is("tester")))
                .andExpect(jsonPath("$[1].role", is("OWNER")));

        mockMvc.perform(delete("/api/projects/1/members/admin")
                        .header("X-User", "tester"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username", is("tester")));
    }

    @Test
    void returnsDashboardSummaryWithoutLoadingProjectAssets() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"loan-core\",\"name\":\"信贷核心压测\",\"description\":\"授信链路\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"risk-core\",\"name\":\"风控核心压测\",\"description\":\"规则链路\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/projects/2/archive")
                        .header("X-User", "admin"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProjectCount", is(1)))
                .andExpect(jsonPath("$.archivedProjectCount", is(1)))
                .andExpect(jsonPath("$.scriptAssetTotal", is(0)))
                .andExpect(jsonPath("$.taskTotal", is(0)))
                .andExpect(jsonPath("$.recentProjects", hasSize(2)))
                .andExpect(jsonPath("$.recentProjects[0].code", is("risk-core")));
    }
}
