package com.autotestai.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.autotestai.config.SiliconFlowProperties;

import org.junit.jupiter.api.Test;

class SiliconFlowAiModelGatewayTest {

    @Test
    void normalizesSiliconFlowOpenAiCompatibleBaseUrl() {
        assertThat(SiliconFlowAiModelGateway.normalizeBaseUrl("https://api.siliconflow.cn/v1"))
                .isEqualTo("https://api.siliconflow.cn");
        assertThat(SiliconFlowAiModelGateway.normalizeBaseUrl("https://gateway.example.test/"))
                .isEqualTo("https://gateway.example.test");
    }

    @Test
    void staysDisabledUntilBothApiKeyAndModelAreConfigured() {
        SiliconFlowProperties properties = new SiliconFlowProperties();
        properties.setApiKey("test-key");

        SiliconFlowAiModelGateway gateway = new SiliconFlowAiModelGateway(properties);

        assertThat(gateway.isConfigured()).isFalse();
        assertThat(gateway.provider()).isEqualTo("SiliconFlow");
        assertThat(gateway.model()).isNull();
    }
}
