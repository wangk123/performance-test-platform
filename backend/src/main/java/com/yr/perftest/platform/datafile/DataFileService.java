package com.yr.perftest.platform.datafile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * 数据文件管理（P1-5）：CSV 上传（流式 sha256/表头/行数）、版本追加、预览、下载定位与级联删除。
 */
@Service
public class DataFileService {
    private static final String DATAFILES_DIR = "datafiles";
    private static final int PREVIEW_ROW_LIMIT = 50;
    private static final String FALLBACK_FILENAME = "upload.csv";

    private final DataFileRepository fileRepository;
    private final DataFileVersionRepository versionRepository;
    private final Path storageRoot;
    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public DataFileService(
            DataFileRepository fileRepository,
            DataFileVersionRepository versionRepository,
            @Value("${platform.storage.root:./storage}") String storageRoot,
            @Value("${platform.datafile.max-size:104857600}") long maxBytes,
            ObjectMapper objectMapper
    ) {
        this.fileRepository = fileRepository;
        this.versionRepository = versionRepository;
        this.storageRoot = Path.of(storageRoot);
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DataFileVersion upload(
            long projectId,
            String name,
            MultipartFile file,
            boolean hasHeader,
            String encoding,
            String remark,
            String uploadedBy
    ) {
        if (name == null || name.isBlank()) {
            throw new DataFileValidationException("data file name is required");
        }
        String originalFilename = file == null ? null : file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new DataFileValidationException("data file filename is required");
        }
        if (file.isEmpty()) {
            throw new DataFileValidationException("data file content is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new DataFileValidationException(
                    "DATAFILE_TOO_LARGE: " + file.getSize() + " bytes exceeds limit " + maxBytes);
        }
        Charset charset = resolveCharset(encoding);

        PersistentDataFileRecord dataFile = fileRepository
                .findByProjectIdAndName(projectId, name.trim())
                .orElseGet(() -> fileRepository.save(new PersistentDataFileRecord(
                        projectId,
                        name.trim(),
                        blankToNull(remark),
                        blankToNull(uploadedBy),
                        LocalDateTime.now()
                )));
        int versionNo = nextVersionNo(dataFile.getId());
        Path target = dataFileDir(projectId, dataFile.getId())
                .resolve("v" + versionNo + "-" + sanitize(originalFilename));
        StoredContent stored = writeStreaming(file, target);

        PersistentDataFileVersionRecord record = versionRepository.save(
                new PersistentDataFileVersionRecord(
                        dataFile.getId(),
                        versionNo,
                        originalFilename,
                        target.toString(),
                        stored.sizeBytes(),
                        stored.rowCount(),
                        hasHeader ? toJson(headerColumns(stored.firstLine(), charset)) : null,
                        stored.sha256Hex(),
                        blankToNull(uploadedBy),
                        LocalDateTime.now(),
                        blankToNull(remark)
                ));
        return toVersion(record);
    }

    @Transactional(readOnly = true)
    public List<DataFile> list(long projectId) {
        return fileRepository.findAllByProjectIdOrderByIdAsc(projectId).stream()
                .map(record -> new DataFile(
                        record.getId(),
                        record.getProjectId(),
                        record.getName(),
                        record.getRemark(),
                        record.getCreatedBy(),
                        record.getCreatedAt(),
                        latestOf(record.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DataFileVersion> versions(long projectId, long dataFileId) {
        requireDataFile(projectId, dataFileId);
        return versionRepository.findAllByDataFileIdOrderByVersionNoDesc(dataFileId).stream()
                .map(this::toVersion)
                .toList();
    }

    @Transactional(readOnly = true)
    public DataFileVersionDetail detail(long projectId, long dataFileId, int versionNo) {
        DataFileVersion version = version(projectId, dataFileId, versionNo);
        List<List<String>> previewRows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(
                Path.of(version.storedPath()), StandardCharsets.UTF_8)) {
            String line;
            while (previewRows.size() < PREVIEW_ROW_LIMIT && (line = reader.readLine()) != null) {
                previewRows.add(Arrays.asList(line.split(",")));
            }
        } catch (IOException exception) {
            throw new DataFileValidationException(
                    "failed to read data file preview: dataFileId=" + dataFileId);
        }
        return new DataFileVersionDetail(version, previewRows);
    }

    public DataFileVersion version(long projectId, long dataFileId, int versionNo) {
        requireDataFile(projectId, dataFileId);
        return versionRepository.findByDataFileIdAndVersionNo(dataFileId, versionNo)
                .map(this::toVersion)
                .orElseThrow(() -> new DataFileValidationException(
                        "data file version does not exist: dataFileId=" + dataFileId
                                + ", versionNo=" + versionNo));
    }

    @Transactional(readOnly = true)
    public DataFileVersion latestVersion(long dataFileId) {
        return versionRepository.findAllByDataFileIdOrderByVersionNoDesc(dataFileId).stream()
                .findFirst()
                .map(this::toVersion)
                .orElseThrow(() -> new DataFileValidationException(
                        "data file has no versions: dataFileId=" + dataFileId));
    }

    @Transactional(readOnly = true)
    public List<DataFileVersion> findByOriginalFilename(String originalFilename) {
        return versionRepository.findAllByOriginalFilename(originalFilename).stream()
                .map(this::toVersion)
                .toList();
    }

    @Transactional
    public void delete(long projectId, long dataFileId) {
        PersistentDataFileRecord dataFile = requireDataFile(projectId, dataFileId);
        versionRepository.deleteAll(versionRepository.findAllByDataFileIdOrderByVersionNoDesc(dataFileId));
        fileRepository.delete(dataFile);
        deleteDirectory(dataFileDir(projectId, dataFileId));
    }

    private int nextVersionNo(long dataFileId) {
        return versionRepository.findAllByDataFileIdOrderByVersionNoDesc(dataFileId).stream()
                .findFirst()
                .map(record -> record.getVersionNo() + 1)
                .orElse(1);
    }

    private StoredContent writeStreaming(MultipartFile file, Path target) {
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream firstLine = new ByteArrayOutputStream();
            long sizeBytes = 0L;
            long lineBreaks = 0L;
            boolean headerDone = false;
            byte lastByte = 0;
            byte[] buffer = new byte[8192];
            try (InputStream input = new DigestInputStream(file.getInputStream(), digest);
                 OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    for (int i = 0; i < read; i++) {
                        byte current = buffer[i];
                        if (current == '\n') {
                            lineBreaks++;
                            headerDone = true;
                        } else if (!headerDone) {
                            firstLine.write(current);
                        }
                        lastByte = current;
                    }
                    sizeBytes += read;
                }
            }
            long rowCount = lineBreaks + (sizeBytes > 0 && lastByte != '\n' ? 1 : 0);
            return new StoredContent(
                    sizeBytes,
                    rowCount,
                    firstLine.toByteArray(),
                    HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new DataFileValidationException("failed to store data file: " + exception.getMessage());
        }
    }

    private List<String> headerColumns(byte[] firstLine, Charset charset) {
        String line = new String(firstLine, charset);
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        return Arrays.stream(line.split(","))
                .map(String::trim)
                .toList();
    }

    private void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("failed to delete " + path, exception);
                }
            });
        } catch (IOException exception) {
            throw new DataFileValidationException("failed to delete data file directory: " + directory);
        }
    }

    private Path dataFileDir(long projectId, long dataFileId) {
        return storageRoot
                .resolve(DATAFILES_DIR)
                .resolve(String.valueOf(projectId))
                .resolve("df" + dataFileId);
    }

    private PersistentDataFileRecord requireDataFile(long projectId, long dataFileId) {
        PersistentDataFileRecord record = fileRepository.findById(dataFileId)
                .orElseThrow(() -> new DataFileValidationException(
                        "data file does not exist: dataFileId=" + dataFileId));
        if (record.getProjectId() != projectId) {
            throw new DataFileValidationException(
                    "data file does not belong to project " + projectId + ": dataFileId=" + dataFileId);
        }
        return record;
    }

    private DataFileVersion latestOf(long dataFileId) {
        return versionRepository.findAllByDataFileIdOrderByVersionNoDesc(dataFileId).stream()
                .findFirst()
                .map(this::toVersion)
                .orElse(null);
    }

    private DataFileVersion toVersion(PersistentDataFileVersionRecord record) {
        return new DataFileVersion(
                record.getId(),
                record.getDataFileId(),
                record.getVersionNo(),
                record.getOriginalFilename(),
                record.getStoredPath(),
                record.getSizeBytes(),
                record.getRowCount(),
                parseHeaderColumns(record.getHeaderColumnsJson()),
                record.getSha256(),
                record.getUploadedBy(),
                record.getUploadedAt(),
                record.getRemark());
    }

    private List<String> parseHeaderColumns(String headerColumnsJson) {
        if (headerColumnsJson == null || headerColumnsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(headerColumnsJson, new TypeReference<List<String>>() {
            });
        } catch (IOException exception) {
            throw new DataFileValidationException("failed to parse header columns json");
        }
    }

    private String toJson(List<String> headerColumns) {
        try {
            return objectMapper.writeValueAsString(headerColumns);
        } catch (IOException exception) {
            throw new DataFileValidationException("failed to serialize header columns json");
        }
    }

    private Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding.trim());
        } catch (Exception exception) {
            throw new DataFileValidationException("unsupported data file encoding: " + encoding);
        }
    }

    private String sanitize(String filename) {
        String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? FALLBACK_FILENAME : sanitized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record StoredContent(long sizeBytes, long rowCount, byte[] firstLine, String sha256Hex) {
    }
}
