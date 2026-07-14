package com.yr.perftest.platform.llm;

import java.util.List;

public interface LlmAdapter {
    List<String> listModels(String baseUrl, String apiKey) throws Exception;

    LlmChatResult chat(String baseUrl, String apiKey, String modelName, List<LlmChatMessage> messages) throws Exception;
}
