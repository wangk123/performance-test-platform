package com.yr.perftest.platform.api;

import com.yr.perftest.platform.datafile.DataFile;
import com.yr.perftest.platform.datafile.DataFileVersion;
import com.yr.perftest.platform.datafile.DataFileVersionDetail;
import com.yr.perftest.platform.datafile.DataFileService;
import com.yr.perftest.platform.datafile.DataFileValidationException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/data-files")
public class DataFileController {
    private final DataFileService dataFileService;

    public DataFileController(DataFileService dataFileService) {
        this.dataFileService = dataFileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DataFileVersion upload(
            @PathVariable long projectId,
            @RequestParam MultipartFile file,
            @RequestParam String name,
            @RequestParam(defaultValue = "true") boolean hasHeader,
            @RequestParam(defaultValue = "UTF-8") String encoding,
            @RequestParam(required = false) String remark,
            @RequestHeader(name = "X-User", defaultValue = "admin") String uploadedBy
    ) {
        return dataFileService.upload(projectId, name, file, hasHeader, encoding, remark, uploadedBy);
    }

    @GetMapping
    public List<DataFile> list(@PathVariable long projectId) {
        return dataFileService.list(projectId);
    }

    @GetMapping("/{dataFileId:\\d+}/versions")
    public List<DataFileVersion> versions(@PathVariable long projectId, @PathVariable long dataFileId) {
        return dataFileService.versions(projectId, dataFileId);
    }

    @GetMapping("/{dataFileId:\\d+}/versions/{versionNo:\\d+}")
    public DataFileVersionDetail detail(
            @PathVariable long projectId,
            @PathVariable long dataFileId,
            @PathVariable int versionNo
    ) {
        return dataFileService.detail(projectId, dataFileId, versionNo);
    }

    @GetMapping("/{dataFileId:\\d+}/versions/{versionNo:\\d+}/download")
    public ResponseEntity<Resource> download(
            @PathVariable long projectId,
            @PathVariable long dataFileId,
            @PathVariable int versionNo
    ) throws MalformedURLException {
        DataFileVersion version = dataFileService.version(projectId, dataFileId, versionNo);
        // 显式补 file: 协议——service 落库的 storedPath 可能是相对路径（platform.storage.root=./storage），
        // UrlResource 单参构造对无协议路径抛 MalformedURLException
        UrlResource resource = new UrlResource("file:" + version.storedPath());
        String encodedFilename = URLEncoder.encode(version.originalFilename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{dataFileId:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long projectId, @PathVariable long dataFileId) {
        dataFileService.delete(projectId, dataFileId);
    }

    @ExceptionHandler(DataFileValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(DataFileValidationException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
