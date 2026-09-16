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

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:datafile-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/datafile-api"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class DataFileControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String authToken;

    public static void runAll() {
        System.out.println("DataFileControllerTest: @SpringBootTest cases run via JUnit all() "
                + "(./gradlew :backend:test); plain-JVM TestRunner path is a registration anchor only");
    }

    @BeforeEach
    void authenticate() throws Exception {
        authToken = AuthTestSupport.loginToken(mockMvc, objectMapper);
    }

    @Test
    void all() throws Exception {
        uploadsCsvWithDefaultsAndServesDownloadThenDeletes();
        mapsValidationFailuresTo400();
    }

    void uploadsCsvWithDefaultsAndServesDownloadThenDeletes() throws Exception {
        createProject();

        MockMultipartFile csv = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                "mobile,name\n138,张三\n139,李四".getBytes(StandardCharsets.UTF_8)
        );

        // 省略 hasHeader/encoding：验证默认值 true / UTF-8（headerColumns 非空即证 hasHeader=true）
        mockMvc.perform(multipart("/api/projects/1/data-files")
                        .file(csv)
                        .param("name", "用户数据")
                        .param("remark", "登录压测手机号池")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataFileId", is(1)))
                .andExpect(jsonPath("$.versionNo", is(1)))
                .andExpect(jsonPath("$.originalFilename", is("users.csv")))
                .andExpect(jsonPath("$.rowCount", is(3)))
                .andExpect(jsonPath("$.headerColumns[0]", is("mobile")))
                .andExpect(jsonPath("$.headerColumns[1]", is("name")))
                .andExpect(jsonPath("$.uploadedBy", is("admin")));

        mockMvc.perform(get("/api/projects/1/data-files/1/versions/1/download")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''users.csv"))
                .andExpect(content().bytes("mobile,name\n138,张三\n139,李四".getBytes(StandardCharsets.UTF_8)));

        // RFC 5987：空格须为 %20 而非 URLEncoder 的 "+"（my+data.csv 会成为字面加号）
        MockMultipartFile spaced = new MockMultipartFile(
                "file", "my data.csv", "text/csv", "mobile,name\n136,王五".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/projects/1/data-files")
                        .file(spaced)
                        .param("name", "用户数据")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo", is(2)));

        mockMvc.perform(get("/api/projects/1/data-files/1/versions/2/download")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''my%20data.csv"));

        mockMvc.perform(delete("/api/projects/1/data-files/1")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/1/data-files")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    void mapsValidationFailuresTo400() throws Exception {
        // 两条请求都不落项目/数据文件记录：blank name 在 service 入口即拒绝，999 版本查询直接走不存在分支，
        // 无需 createProject（all() 内 test1 已建 project 1，重复创建会 400）

        MockMultipartFile csv = new MockMultipartFile(
                "file", "users.csv", "text/csv", "mobile,name\n138,张三".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/projects/1/data-files")
                        .file(csv)
                        .param("name", " ")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DATAFILE_VALIDATION_FAILED")))
                .andExpect(jsonPath("$.message", is("data file name is required")));

        mockMvc.perform(get("/api/projects/1/data-files/999/versions/1")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DATAFILE_VALIDATION_FAILED")))
                .andExpect(jsonPath("$.message", is(
                        "data file does not exist: dataFileId=999")));
    }

    private void createProject() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"code\":\"loan-core\",\"name\":\"信贷核心压测\",\"description\":\"授信和放款链路\"}"))
                .andExpect(status().isCreated());
    }
}
