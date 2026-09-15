package com.yr.perftest.platform.task.method;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.method.MethodSectionResponse.EvidenceImage;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 补充截图：multipart 上传落盘 {storageRoot}/images/plans/{planId}/{uuid}.{ext}（storedPath 存相对路径），
 * 图注/排序可改、记录可删（磁盘文件尽力而为清理），文件流按 image→plan 反查成员权限后返回。
 */
@Service
public class PlanEvidenceImageService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSION_CONTENT_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp"
    );

    private final PlanEvidenceImageRepository imageRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PlanWorkflowService planWorkflowService;
    private final Path storageRoot;

    public PlanEvidenceImageService(
            PlanEvidenceImageRepository imageRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PlanWorkflowService planWorkflowService,
            @Value("${platform.storage.root:./storage}") String storageRoot
    ) {
        this.imageRepository = imageRepository;
        this.scenarioRepository = scenarioRepository;
        this.planWorkflowService = planWorkflowService;
        this.storageRoot = Path.of(storageRoot);
    }

    @Transactional
    public EvidenceImage store(long planId, long scenarioId, Long executionId,
                               String caption, MultipartFile file, HumanPrincipal actor) {
        planWorkflowService.requireActor(planId, actor, "EDIT");
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ExecutionValidationException("scenario does not exist"));
        if (scenario.getPlanId() != planId) {
            throw new ExecutionValidationException("scenario does not belong to plan");
        }
        String contentType = EXTENSION_CONTENT_TYPES.get(extensionOf(file));
        if (contentType == null) {
            throw new ExecutionValidationException("only png/jpg/webp images are supported");
        }
        if (file.isEmpty()) {
            throw new ExecutionValidationException("image file is empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ExecutionValidationException("image must not exceed 5MB");
        }
        byte[] content = readFile(file);
        Path target = storageRoot
                .resolve("images")
                .resolve("plans")
                .resolve(String.valueOf(planId))
                .resolve(UUID.randomUUID() + "." + extensionOf(file));
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw new ExecutionValidationException("failed to store image file");
        }
        int sortOrder = imageRepository.findByScenarioIdOrderBySortOrderAscIdAsc(scenarioId).size();
        PersistentPlanEvidenceImageRecord record = imageRepository.save(new PersistentPlanEvidenceImageRecord(
                planId, scenarioId, normalizeCaption(caption), sortOrder,
                target.toString(), contentType, content.length, actor.username()));
        record.setExecutionId(executionId);
        return toImage(record);
    }

    @Transactional
    public EvidenceImage update(long imageId, HumanPrincipal actor, String caption, Integer sortOrder) {
        PersistentPlanEvidenceImageRecord image = requireImage(imageId);
        planWorkflowService.requireActor(image.getPlanId(), actor, "EDIT");
        if (caption != null) {
            image.setCaption(normalizeCaption(caption));
        }
        if (sortOrder != null) {
            image.setSortOrder(sortOrder);
        }
        return toImage(image);
    }

    @Transactional
    public void delete(long imageId, HumanPrincipal actor) {
        PersistentPlanEvidenceImageRecord image = requireImage(imageId);
        planWorkflowService.requireActor(image.getPlanId(), actor, "EDIT");
        imageRepository.delete(image);
        try {
            Files.deleteIfExists(Path.of(image.getStoredPath()));
        } catch (IOException ignored) {
            // 尽力而为：记录已删，磁盘文件清理失败不阻断删除
        }
    }

    @Transactional(readOnly = true)
    public ImageFile openFile(long imageId, HumanPrincipal actor) {
        PersistentPlanEvidenceImageRecord image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ImageNotFoundException("image does not exist"));
        planWorkflowService.requireActor(image.getPlanId(), actor, "EDIT");
        try {
            return new ImageFile(image.getContentType(), Files.readAllBytes(Path.of(image.getStoredPath())));
        } catch (IOException exception) {
            throw new ImageNotFoundException("image file does not exist");
        }
    }

    private PersistentPlanEvidenceImageRecord requireImage(long imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new ExecutionValidationException("image does not exist"));
    }

    private String extensionOf(MultipartFile file) {
        String filename = file.getOriginalFilename();
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeCaption(String caption) {
        return caption == null || caption.trim().isEmpty() ? null : caption.trim();
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ExecutionValidationException("failed to read image file");
        }
    }

    private EvidenceImage toImage(PersistentPlanEvidenceImageRecord image) {
        return new EvidenceImage(image.getId(), image.getExecutionId(), image.getCaption(),
                image.getSortOrder(), image.getContentType(), image.getSizeBytes());
    }

    /** 文件流载荷：Content-Type 用库中存的 contentType，字节为磁盘原文。 */
    public record ImageFile(String contentType, byte[] content) {
    }

    /** 文件读取域 404 语义（记录不存在/磁盘被清），由控制器转 ApiError NOT_FOUND。 */
    public static class ImageNotFoundException extends RuntimeException {
        public ImageNotFoundException(String message) {
            super(message);
        }
    }
}
