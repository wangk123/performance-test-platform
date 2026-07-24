package com.yr.perftest.platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.identity.AuthenticationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-exception-handler-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(AgentExceptionHandlerTest.ProbeConfig.class)
class AgentExceptionHandlerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void agentNotFoundIsEnvelopeWithStableCode() throws Exception {
        String token = loginToken();
        mockMvc.perform(get("/api/agent/probe/not-found")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.schemaVersion").isNotEmpty())
                .andExpect(jsonPath("$.data", nullValue()))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.truncated", nullValue()))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void agentValidationFailedIsEnvelope() throws Exception {
        String token = loginToken();
        mockMvc.perform(post("/api/agent/probe/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void agentUnauthenticatedIsEnvelopeAuthenticationFailed() throws Exception {
        mockMvc.perform(get("/api/agent/probe/not-found"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.schemaVersion").isNotEmpty());
    }

    @Test
    void uiFaceKeepsBareApiError() throws Exception {
        String token = loginToken();
        mockMvc.perform(get("/api/reports/plans/999999/data")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void agentAuthenticationExceptionIsEnvelope() throws Exception {
        String token = loginToken();
        mockMvc.perform(get("/api/agent/probe/auth-failed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"));
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    @TestConfiguration
    @Import(ProbeController.class)
    static class ProbeConfig {
    }

    @RestController
    @RequestMapping("/api/agent/probe")
    static class ProbeController {
        @GetMapping("/not-found")
        public void notFound() {
            throw new ExecutionValidationException("execution does not exist");
        }

        @GetMapping("/auth-failed")
        public void authFailed() {
            throw new AuthenticationException("invalid principal");
        }

        @PostMapping("/validate")
        public void validate(@Valid @RequestBody ProbeBody body) {
        }

        record ProbeBody(@NotBlank String name) {
        }
    }
}
