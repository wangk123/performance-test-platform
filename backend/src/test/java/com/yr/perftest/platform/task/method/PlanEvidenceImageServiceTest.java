package com.yr.perftest.platform.task.method;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.api.AuthTestSupport;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 6：补充截图 multipart 上传（png/jpg/webp ≤5MB）、元数据改删、文件流成员鉴权读取。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:evidence-image-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class PlanEvidenceImageServiceTest {

    @TempDir
    static Path tempStorage;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.storage.root", () -> tempStorage.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PlanEvidenceImageRepository imageRepository;

    private String token;
    private long planId;
    private long scenarioId;
    private long otherPlanScenarioId;

    @BeforeEach
    void setUp() throws Exception {
        token = AuthTestSupport.loginToken(mockMvc, objectMapper);
        long projectId = projectRepository.save(
                new PersistentProjectRecord("P-IMG", "截图上传", "", "admin")).getId();
        planId = planRepository.save(
                new PersistentTaskPlanRecord(projectId, "截图计划", null, "admin")).getId();
        scenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, null, "场景甲", 0)).getId();
        long otherPlanId = planRepository.save(
                new PersistentTaskPlanRecord(projectId, "另一计划", null, "admin")).getId();
        otherPlanScenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(otherPlanId, null, "场景乙", 0)).getId();
    }

    @Test
    void uploadStoresFileOnDiskAndReturnsMetadata() throws Exception {
        byte[] payload = new byte[]{1, 2, 3, 4, 5};

        mockMvc.perform(multipart("/api/task-plans/" + planId + "/scenarios/" + scenarioId + "/evidence-images")
                        .file(new MockMultipartFile("file", "response-time.png", "image/png", payload))
                        .param("caption", "RT 曲线")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caption", is("RT 曲线")))
                .andExpect(jsonPath("$.sortOrder", is(0)))
                .andExpect(jsonPath("$.contentType", is("image/png")))
                .andExpect(jsonPath("$.sizeBytes", is(5)));

        assertThat(imageRepository.findByScenarioIdOrderBySortOrderAscIdAsc(scenarioId)).hasSize(1);
        PersistentPlanEvidenceImageRecord record = imageRepository
                .findByScenarioIdOrderBySortOrderAscIdAsc(scenarioId).get(0);
        assertThat(record.getStoredPath()).contains("images" + fileSeparator() + "plans" + fileSeparator() + planId);
        assertThat(Files.readAllBytes(Path.of(record.getStoredPath()))).isEqualTo(payload);
    }

    @Test
    void fileEndpointStreamsStoredBytesWithAuth() throws Exception {
        byte[] payload = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
        long imageId = uploadImage(payload, "gc-trend.png");

        mockMvc.perform(get("/api/images/" + imageId + "/file")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG_VALUE))
                .andExpect(content().bytes(payload));
    }

    @Test
    void fileEndpointReturns404WhenDiskFileMissing() throws Exception {
        long imageId = uploadImage(new byte[]{9, 9}, "missing.png");
        Path stored = Path.of(imageRepository.findById(imageId).orElseThrow().getStoredPath());
        Files.deleteIfExists(stored);

        mockMvc.perform(get("/api/images/" + imageId + "/file")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    void rejectsNonImageUpload() throws Exception {
        mockMvc.perform(multipart("/api/task-plans/" + planId + "/scenarios/" + scenarioId + "/evidence-images")
                        .file(new MockMultipartFile("file", "notes.txt", MediaType.TEXT_PLAIN_VALUE,
                                "not an image".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("only png/jpg/webp images are supported")));
        assertThat(imageRepository.findByScenarioIdOrderBySortOrderAscIdAsc(scenarioId)).isEmpty();
    }

    @Test
    void rejectsOversizedUpload() throws Exception {
        mockMvc.perform(multipart("/api/task-plans/" + planId + "/scenarios/" + scenarioId + "/evidence-images")
                        .file(new MockMultipartFile("file", "huge.png", "image/png",
                                new byte[6 * 1024 * 1024]))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("image must not exceed 5MB")));
    }

    @Test
    void rejectsScenarioFromAnotherPlan() throws Exception {
        mockMvc.perform(multipart("/api/task-plans/" + planId + "/scenarios/" + otherPlanScenarioId + "/evidence-images")
                        .file(new MockMultipartFile("file", "cross.png", "image/png", new byte[]{1}))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("scenario does not belong to plan")));
    }

    @Test
    void updatesCaptionAndSortOrderPartially() throws Exception {
        long imageId = uploadImage(new byte[]{1}, "partial.png");

        mockMvc.perform(put("/api/images/" + imageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"caption\":\"排序后图注\",\"sortOrder\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption", is("排序后图注")))
                .andExpect(jsonPath("$.sortOrder", is(3)));

        mockMvc.perform(put("/api/images/" + imageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"caption\":\"只改图注\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption", is("只改图注")))
                .andExpect(jsonPath("$.sortOrder", is(3)));
    }

    @Test
    void deleteRemovesRecordAndDiskFile() throws Exception {
        byte[] payload = new byte[]{7, 7, 7};
        long imageId = uploadImage(payload, "doomed.png");
        Path stored = Path.of(imageRepository.findById(imageId).orElseThrow().getStoredPath());

        mockMvc.perform(delete("/api/images/" + imageId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(imageRepository.findById(imageId)).isEmpty();
        assertThat(Files.exists(stored)).isFalse();
    }

    private long uploadImage(byte[] payload, String filename) throws Exception {
        MvcResult result = mockMvc
                .perform(multipart("/api/task-plans/" + planId + "/scenarios/" + scenarioId + "/evidence-images")
                        .file(new MockMultipartFile("file", filename, "image/png", payload))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asLong();
    }

    private String fileSeparator() {
        return java.io.File.separator;
    }
}
