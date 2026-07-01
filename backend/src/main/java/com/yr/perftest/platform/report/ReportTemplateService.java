package com.yr.perftest.platform.report;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 报告模板服务，管理默认 Word 模板。
 * 一期仅内置默认模板，自定义模板延后。
 */
@Service
public class ReportTemplateService {

    private static final String DEFAULT_TEMPLATE_PATH = "templates/report/word-template.docx";

    /**
     * 加载默认 Word 模板的输入流。
     */
    public InputStream loadDefaultTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource(DEFAULT_TEMPLATE_PATH);
            if (!resource.exists()) {
                throw new ReportTemplateException("默认 Word 模板不存在: " + DEFAULT_TEMPLATE_PATH);
            }
            return resource.getInputStream();
        } catch (ReportTemplateException e) {
            throw e;
        } catch (Exception e) {
            throw new ReportTemplateException("无法加载 Word 模板: " + e.getMessage(), e);
        }
    }

    /**
     * 报告模板异常。
     */
    public static class ReportTemplateException extends RuntimeException {
        public ReportTemplateException(String message) {
            super(message);
        }

        public ReportTemplateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
