package com.autotestai.ai;

import java.net.http.HttpClient;

import com.autotestai.config.SiliconFlowProperties;
import com.autotestai.dto.ai.AiFailureDiagnosis;
import com.autotestai.dto.ai.AiGeneratedTestCases;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class SiliconFlowAiModelGateway implements AiModelGateway {

    private static final String PROVIDER = "SiliconFlow";

    private final SiliconFlowProperties properties;
    private final ChatClient chatClient;

    public SiliconFlowAiModelGateway(SiliconFlowProperties properties) {
        this.properties = properties;
        this.chatClient = properties.isConfigured() ? createClient(properties) : null;
    }

    @Override
    public boolean isConfigured() {
        return chatClient != null;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String model() {
        return StringUtils.hasText(properties.getModel()) ? properties.getModel().trim() : null;
    }

    @Override
    public AiGeneratedTestCases generateTestCases(String systemPrompt, String userPrompt) {
        return callStructured(systemPrompt, userPrompt, AiGeneratedTestCases.class, "test case generation");
    }

    @Override
    public AiFailureDiagnosis analyzeFailure(String systemPrompt, String userPrompt) {
        return callStructured(systemPrompt, userPrompt, AiFailureDiagnosis.class, "failure diagnosis");
    }

    private <T> T callStructured(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType,
            String operation) {
        if (chatClient == null) {
            throw new IllegalStateException("SiliconFlow is not configured");
        }
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(responseType);
        } catch (RuntimeException exception) {
            throw new AiModelException("SiliconFlow " + operation + " failed", exception);
        }
    }

    static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    private static ChatClient createClient(SiliconFlowProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getTimeout());

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .apiKey(properties.getApiKey().trim())
                .completionsPath("/v1/chat/completions")
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getModel().trim())
                .temperature(0.2)
                .maxTokens(properties.getMaxTokens())
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
        return ChatClient.builder(chatModel).build();
    }
}
