package com.openclaw.desktop.agent;

import com.openclaw.desktop.llm.*;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * AgentLoop 测试 — 使用 mock LlmProvider。
 */
class AgentLoopTest {

    @Test
    void testAgentChat() {
        var mockProvider = new LlmProvider() {
            @Override public String id() { return "mock"; }
            @Override public String name() { return "Mock"; }

            @Override
            public Mono<LlmResponse> chat(LlmRequest request) {
                return Mono.just(LlmResponse.text(
                    "Hello! I'm ClawDesktop.",
                    new UsageInfo(10, 20, 30, null, null),
                    "stop"
                ));
            }

            @Override
            public Flux<LlmEvent> chatStream(LlmRequest request) {
                return Flux.just(
                    new LlmEvent.TextDelta(0, "Hello"),
                    new LlmEvent.TextDelta(0, "! "),
                    new LlmEvent.TextDelta(0, "I'm ClawDesktop."),
                    new LlmEvent.AgentEnd(List.of())
                );
            }

            @Override public Flux<Model> listModels() { return Flux.empty(); }
            @Override public Mono<Boolean> healthCheck() { return Mono.just(true); }
        };

        var config = AgentConfig.defaults();
        var session = new Session(SessionKey.main("default"));
        var toolRegistry = new ToolRegistry();
        var agent = new Agent(config, mockProvider, toolRegistry, session);

        var response = agent.chat("Hello").block();
        assertNotNull(response);
        assertTrue(response.contains("ClawDesktop") || response.length() > 0);
    }
}
