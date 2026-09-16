package com.yr.perftest.platform.datafile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:datafile-service-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false",
        "platform.datafile.max-size=1000"
})
public class DataFileServiceTest {
    private static final long PROJECT_ID = 424242L;

    static Path tempRoot;

    @BeforeAll
    static void init() throws IOException {
        tempRoot = Files.createTempDirectory("datafiles");
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("platform.storage.root", () -> tempRoot.toString());
    }

    @Autowired
    DataFileService service;

    private long dataFileId;

    public static void runAll() {
        System.out.println("DataFileServiceTest: @SpringBootTest cases run via JUnit all() "
                + "(./gradlew :backend:test); plain-JVM TestRunner path is a registration anchor only");
    }

    @Test
    void all() {
        uploadCreatesV1WithHeaderRowsAndSha256();
        reUploadSameNameAppendsV2();
        oversizeRejected();
        detailPreviewReturnsFirst50Rows();
        deleteRemovesRecordsAndFiles();
    }

    void uploadCreatesV1WithHeaderRowsAndSha256() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                "mobile,name\n138,张三\n139,李四".getBytes(StandardCharsets.UTF_8)
        );

        DataFileVersion version = service.upload(
                PROJECT_ID, "用户数据", file, true, "UTF-8", "登录压测手机号池", "tester-a");

        dataFileId = version.dataFileId();
        assertThat(version.versionNo()).isEqualTo(1);
        assertThat(version.rowCount()).isEqualTo(3);
        assertThat(version.headerColumns()).containsExactly("mobile", "name");
        assertThat(version.sha256()).hasSize(64).matches("[0-9a-f]{64}");
        Path stored = tempRoot
                .resolve("datafiles")
                .resolve(String.valueOf(PROJECT_ID))
                .resolve("df" + dataFileId)
                .resolve("v1-users.csv");
        assertThat(stored).exists();
        assertThat(version.storedPath()).isEqualTo(stored.toString());
    }

    void reUploadSameNameAppendsV2() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users-v2.csv",
                "text/csv",
                "mobile,name\n137,王五".getBytes(StandardCharsets.UTF_8)
        );

        DataFileVersion version = service.upload(
                PROJECT_ID, "用户数据", file, true, "UTF-8", "换号重传", "tester-a");

        assertThat(version.dataFileId()).isEqualTo(dataFileId);
        assertThat(version.versionNo()).isEqualTo(2);
        DataFileVersion v1 = service.version(PROJECT_ID, dataFileId, 1);
        assertThat(v1.versionNo()).isEqualTo(1);
        assertThat(v1.originalFilename()).isEqualTo("users.csv");
        assertThat(service.versions(PROJECT_ID, dataFileId)).hasSize(2);
    }

    void oversizeRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "big.csv", "text/csv", new byte[1001]);

        assertThatThrownBy(() -> service.upload(
                PROJECT_ID, "超限数据", file, true, "UTF-8", null, "tester-a"))
                .isInstanceOf(DataFileValidationException.class)
                .hasMessageContaining("DATAFILE_TOO_LARGE");
    }

    void detailPreviewReturnsFirst50Rows() {
        StringBuilder csv = new StringBuilder("idx");
        for (int i = 0; i < 59; i++) {
            csv.append('\n').append(i);
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "rows.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8));
        DataFileVersion version = service.upload(
                PROJECT_ID, "预览数据", file, true, "UTF-8", null, "tester-a");

        DataFileVersionDetail detail = service.detail(PROJECT_ID, version.dataFileId(), 1);

        assertThat(detail.version().versionNo()).isEqualTo(1);
        assertThat(detail.previewRows()).hasSize(50);
        assertThat(detail.previewRows().get(0)).containsExactly("idx");
        assertThat(detail.previewRows().get(1)).containsExactly("0");
        assertThat(detail.previewRows().get(49)).containsExactly("48");
    }

    void deleteRemovesRecordsAndFiles() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "trash.csv", "text/csv", "k,v\n1,2".getBytes(StandardCharsets.UTF_8));
        DataFileVersion version = service.upload(
                PROJECT_ID, "待删数据", file, true, "UTF-8", null, "tester-a");
        long targetId = version.dataFileId();
        Path dir = tempRoot
                .resolve("datafiles")
                .resolve(String.valueOf(PROJECT_ID))
                .resolve("df" + targetId);
        assertThat(dir).exists();

        service.delete(PROJECT_ID, targetId);

        assertThat(service.list(PROJECT_ID))
                .extracting(DataFile::name)
                .contains("用户数据")
                .doesNotContain("待删数据");
        assertThat(dir).doesNotExist();
        assertThatThrownBy(() -> service.version(PROJECT_ID, targetId, 1))
                .isInstanceOf(DataFileValidationException.class);
    }
}
