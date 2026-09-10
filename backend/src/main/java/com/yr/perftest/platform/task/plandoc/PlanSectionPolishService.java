package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.llm.LlmCallScene;
import com.yr.perftest.platform.llm.LlmChatMessage;
import com.yr.perftest.platform.llm.LlmGateway;
import com.yr.perftest.platform.llm.LlmModelService;
import com.yr.perftest.platform.llm.LlmValidationException;
import com.yr.perftest.platform.llm.PersistentModelDefinitionRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 计划章节 AI 润色：整章正文单次调用底座（无 Tool Calling），用平台默认模型。
 * 只生成润色建议文本，不落计划文档；采纳与否由用户在编辑弹窗内确认，
 * 落库仍走全文保存链路（revision 冲突保护）。调用明细由 LlmGateway 落
 * PLAN_POLISH 场景的调用记录。
 */
@Service
public class PlanSectionPolishService {
    public static final String PROMPT_VERSION = "v1";

    /** 与前端弹窗编辑器容量对齐的上限，防超长内容打满模型上下文。 */
    static final int MAX_CONTENT_CHARS = 32_768;

    private static final String SYSTEM_PROMPT = """
            你是性能测试平台的计划文档编辑助手，负责润色「性能测试计划」的章节文本。硬性规则：
            1. 只优化措辞、语病、冗余和标点，使表达更专业、清晰、简洁；不得改写语义、不得增删内容。
            2. 技术事实一律原样保留：数字、指标值、目标值、阈值、百分比、日期、时间、版本号、人名、脚本与接口名称等不得修改。
            3. Markdown 结构原样保留：标题层级、段落、表格（表头、分隔行、对齐冒号、管道符与列数）、任务清单行（「- [ ]」「- [x]」）、有序/无序列表的标记与缩进不得改变；不得增删表格列、行或清单项。
            4. 只输出润色后的 Markdown 正文，不要用代码块包裹，不要任何前后缀说明。
            5. 输出语言与输入一致。""";

    private final LlmGateway llmGateway;
    private final LlmModelService modelService;

    public PlanSectionPolishService(LlmGateway llmGateway, LlmModelService modelService) {
        this.llmGateway = llmGateway;
        this.modelService = modelService;
    }

    public PolishResult polish(String sectionTitle, String content, String requestedBy) {
        if (content == null || content.isBlank()) {
            throw new PlanValidationException("章节内容为空，无需润色");
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            throw new PlanValidationException("章节内容超过润色上限（" + MAX_CONTENT_CHARS + " 字符）");
        }
        var model = requireDefaultModel();
        String title = sectionTitle == null || sectionTitle.isBlank() ? "未命名章节" : sectionTitle.trim();
        String prompt = "请润色以下计划章节。\n\n章节标题：" + title + "\n\n原文：\n" + content;
        LlmGateway.InvokeResult result = llmGateway.invoke(new LlmGateway.InvokeRequest(
                model.getId(),
                LlmCallScene.PLAN_POLISH,
                List.of(
                        new LlmChatMessage("system", SYSTEM_PROMPT),
                        new LlmChatMessage("user", prompt)
                ),
                true,
                requestedBy
        ));
        if (!result.success() || result.content() == null || result.content().isBlank()) {
            String reason = result.errorMessage() == null ? "模型返回空内容" : result.errorMessage();
            throw new PlanValidationException("AI 润色失败：" + reason);
        }
        return new PolishResult(
                stripCodeFence(result.content()),
                result.latencyMs(),
                result.callRecordId(),
                PROMPT_VERSION,
                model.getId(),
                model.getModelName()
        );
    }

    /** 润色依赖平台默认模型；未配置时以业务语义而非 LLM 校验异常暴露给前端。 */
    private PersistentModelDefinitionRecord requireDefaultModel() {
        try {
            return modelService.requireDefaultEnabled();
        } catch (LlmValidationException exception) {
            throw new PlanValidationException("AI 润色失败：未配置可用的默认模型，请先在「模型设置」中设置默认模型");
        }
    }

    /** 模型无视「不用代码块包裹」约束时的兜底：剥掉首尾 ``` 围栏。 */
    private static String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstBreak = trimmed.indexOf('\n');
        if (firstBreak < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstBreak + 1);
        if (body.endsWith("```")) {
            body = body.substring(0, body.length() - 3);
        }
        return body.trim();
    }

    public record PolishResult(
            String content,
            long latencyMs,
            long callRecordId,
            String promptVersion,
            long modelId,
            String modelName
    ) {
    }
}
