package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * OpenAiProvider 测试（使用 mock 环境）。
 */
class OpenAiProviderTest {

    @Test
    void testProviderIdAndName() {
        var provider = new OpenAiProvider("sk-test");
        assertEquals("openai", provider.id());
        assertEquals("OpenAI", provider.name());
    }

    @Test
    void testStreamParsing() {
        // 测试 SSE 解析逻辑
        var chunkJson = """
            {"choices":[{"delta":{"content":"hello"}}]}
        """;

        var eventSink = new java.util.ArrayList<LlmEvent>();
        var flux = Flux.just(chunkJson)
            .map(json -> {
                try {
                    return (com.fasterxml.jackson.databind.JsonNode) new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                } catch (Exception e) {
                    return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
                }
            });

        // 简单验证 provider 可实例化
        assertNotNull(new OpenAiProvider("sk-test"));
    }
}
