package com.openclaw.desktop;

import com.openclaw.desktop.agent.Agent;
import com.openclaw.desktop.agent.AgentConfig;
import com.openclaw.desktop.agent.AgentEvent;
import com.openclaw.desktop.llm.*;
import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.tool.core.ReadFileTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D2 集成测试 — Agent + LLM + Tool 端到端流程。
 * 使用 MockProvider 模拟 LLM 响应，验证 Agent 能调用工具并返回结果。
 */
class AgentToolIntegrationTest {

    @TempDir
    Path tempDir;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String jsonPath(Path path) {
        var node = MAPPER.createObjectNode();
        node.put("path", path.toString());
        return node.toString();
    }

    private String jsonPathContent(Path path, String content) {
        var node = MAPPER.createObjectNode();
        node.put("path", path.toString());
        node.put("content", content);
        return node.toString();
    }

    private String jsonPathOldNew(Path path, String oldText, String newText) {
        var node = MAPPER.createObjectNode();
        node.put("path", path.toString());
        node.put("oldText", oldText);
        node.put("newText", newText);
        return node.toString();
    }

    @Test
    @DisplayName("Agent chat with mock provider returns response")
    void testAgentChatWithMockProvider() {
        var provider = new MockProvider("mock", "Mock Provider", "Hello from mock!");
        var session = new Session(SessionKey.main("test"));
        var toolRegistry = new ToolRegistry();
        var agentConfig = AgentConfig.defaults();
        var agent = new Agent(agentConfig, provider, toolRegistry, session);

        var response = agent.chat("hi").block();
        assertNotNull(response);
        assertTrue(response.contains("Hello from mock"));
    }

    @Test
    @DisplayName("Agent chatStream emits TextDelta events")
    void testAgentChatStream() {
        var provider = new MockProvider("mock", "Mock", "streamed response");
        var session = new Session(SessionKey.main("test"));
        var toolRegistry = new ToolRegistry();
        var agent = new Agent(agentConfig(), provider, toolRegistry, session);

        var events = agent.chatStream("hello").collectList().block();
        assertNotNull(events);
        assertFalse(events.isEmpty());
    }

    @Test
    @DisplayName("ToolRegistry executes ReadFileTool successfully")
    void testToolExecutionViaRegistry() throws Exception {
        var filePath = tempDir.resolve("test.txt");
        Files.writeString(filePath, "file content here");

        var registry = new ToolRegistry();
        registry.register(new ReadFileTool());

        var input = new com.openclaw.desktop.tool.ToolInput("call-1", jsonPath(filePath));
        var result = registry.execute("read_file", input, null).block();
        assertNotNull(result);
        assertTrue(result.success());
        assertTrue(result.content().contains("file content here"));
    }

    @Test
    @DisplayName("ToolRegistry returns failure for unknown tool")
    void testUnknownTool() {
        var registry = new ToolRegistry();
        var input = new com.openclaw.desktop.tool.ToolInput("call-1", "{}");
        var result = registry.execute("nonexistent", input, null).block();
        assertNotNull(result);
        assertFalse(result.success());
    }

    @Test
    @DisplayName("Multiple tools registered and all callable")
    void testMultipleTools() throws Exception {
        var filePath = tempDir.resolve("multi.txt");
        Files.writeString(filePath, "original");

        var registry = new ToolRegistry();
        registry.register(new com.openclaw.desktop.tool.core.ReadFileTool());
        registry.register(new com.openclaw.desktop.tool.core.WriteFileTool());
        registry.register(new com.openclaw.desktop.tool.core.EditFileTool());

        assertEquals(3, registry.size());

        // 读
        var readInput = new com.openclaw.desktop.tool.ToolInput("c1", jsonPath(filePath));
        var readResult = registry.execute("read_file", readInput, null).block();
        assertTrue(readResult.success());

        // 写
        var writeInput = new com.openclaw.desktop.tool.ToolInput("c2", jsonPathContent(filePath, "new"));
        var writeResult = registry.execute("write_file", writeInput, null).block();
        assertTrue(writeResult.success());
        assertEquals("new", Files.readString(filePath));

        // 编辑
        var editInput = new com.openclaw.desktop.tool.ToolInput("c3", jsonPathOldNew(filePath, "new", "edited"));
        var editResult = registry.execute("edit_file", editInput, null).block();
        assertTrue(editResult.success());
        assertEquals("edited", Files.readString(filePath));
    }

    private AgentConfig agentConfig() {
        return AgentConfig.defaults();
    }

    /** Mock LLM Provider — 返回固定响应。 */
    static class MockProvider implements LlmProvider {
        private final String id;
        private final String name;
        private final String responseContent;

        MockProvider(String id, String name, String responseContent) {
            this.id = id;
            this.name = name;
            this.responseContent = responseContent;
        }

        @Override public String id() { return id; }
        @Override public String name() { return name; }

        @Override
        public Mono<LlmResponse> chat(LlmRequest request) {
            return Mono.just(new LlmResponse(responseContent, List.of(),
                new UsageInfo(10, 20, 30, null, null), "stop", java.util.Map.of()));
        }

        @Override
        public Flux<LlmEvent> chatStream(LlmRequest request) {
            return Flux.just(new LlmEvent.TextDelta(0, responseContent),
                new LlmEvent.Usage(new UsageInfo(10, 20, 30, null, null)),
                new LlmEvent.AgentEnd(java.util.List.of()));
        }

        @Override public Flux<Model> listModels() { return Flux.empty(); }
        @Override public Mono<Boolean> healthCheck() { return Mono.just(true); }
    }
}
