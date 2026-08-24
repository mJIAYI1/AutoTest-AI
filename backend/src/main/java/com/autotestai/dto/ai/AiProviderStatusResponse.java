package com.autotestai.dto.ai;

public record AiProviderStatusResponse(
        String provider,
        boolean configured,
        String model,
        String capability) {
}
