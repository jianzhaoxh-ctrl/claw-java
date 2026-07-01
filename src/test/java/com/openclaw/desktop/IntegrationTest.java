package com.openclaw.desktop;

import com.openclaw.desktop.agent.Agent;
import com.openclaw.desktop.agent.AgentConfig;
import com.openclaw.desktop.llm.*;
import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.tool.core.ReadFileTool;
import com.openclaw.desktop.tool.core.WriteFileTool;
import com.openclaw.desktop.tool.core.ListFilesTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试 — Agent + LLM + Tool + Session。
 */
class IntegrationTest {

    @Test
    void testAgentWithMockProviderAndTools(@TempDir Path tempDir) {
        // 1. 创建 Mock LLM Provider
        var mockProvider = new LlmProvider() {
            @Override public String id() { return "mock"; }
            @Override public String name() { return "Mock Provider"; }

            @Override
            public Mono<LlmResponse> chat(LlmRequest request) {
                // 如果有 tools，模拟一个 tool call
                if (request.tools() != null && !request.tools().isEmpty()) {
                    var toolCall = new ToolCall("call_1", "list_files", 0,
                        "{\"path\":\"" + tempDir.toString().replace("\\", "\\\\") + "\"}");
                    return Mono.just(new LlmResponse("", List.of(toolCall),
                        new UsageInfo(10, 5, 15, null, null), "tool_calls", java.util.Map.of()));
                }
                // 第二轮：返回最终文本
                return Mono.just(new LlmResponse("Hello! I listed your files.",
                    List.of(), new UsageInfo(20, 10, 30, null, null), "stop", java.util.Map.of()));
            }

            @Override
            public Flux<LlmEvent> chatStream(LlmRequest request) {
                return Flux.just(new LlmEvent.TextDelta(0, "Hello!"), new LlmEvent.AgentEnd(java.util.List.of()));
            }

            @Override public Flux<Model> listModels() { return Flux.empty(); }
            @Override public Mono<Boolean> healthCheck() { return Mono.just(true); }
        };

        // 2. 创建 ToolRegistry
        var toolRegistry = new ToolRegistry();
        toolRegistry.register(new ListFilesTool());
        toolRegistry.register(new ReadFileTool());
        toolRegistry.register(new WriteFileTool());

        // 3. 创建 Session + Agent
        var session = new Session(SessionKey.main("test-integration"));
        var config = AgentConfig.defaults();
        var agent = new Agent(config, mockProvider, toolRegistry, session);

        // 4. 发送消息
        var response = agent.chat("List files in " + tempDir.toString()).block();
        assertNotNull(response, "Agent should return a response");

        // 5. 验证 session 有消息记录
        var entries = session.transcript().entries();
        assertFalse(entries.isEmpty(), "Session should have transcript entries");
        assertTrue(entries.stream().anyMatch(e -> e.role().equals("user")),
            "Session should contain user message");
    }

    @Test
    void testToolExecutionEndToEnd(@TempDir Path tempDir) {
        // 1. 创建工具注册表
        var toolRegistry = new ToolRegistry();
        var writeFileTool = new WriteFileTool();
        var readFileTool = new ReadFileTool();
        toolRegistry.register(writeFileTool);
        toolRegistry.register(readFileTool);

        // 2. 写文件
        var testFile = tempDir.resolve("test.txt");
        var writeTool = toolRegistry.get("write_file").orElseThrow();
        var writeResult = writeTool.execute(
            new com.openclaw.desktop.tool.ToolInput("call_1",
                "{\"path\":\"" + testFile.toString().replace("\\", "\\\\") + "\",\"content\":\"Hello World\"}"),
            new com.openclaw.desktop.tool.ToolContext(null, null, tempDir, java.util.Map.of())
        ).block();

        assertNotNull(writeResult);
        assertTrue(writeResult.success());

        // 3. 读文件
        var readTool = toolRegistry.get("read_file").orElseThrow();
        var readResult = readTool.execute(
            new com.openclaw.desktop.tool.ToolInput("call_2",
                "{\"path\":\"" + testFile.toString().replace("\\", "\\\\") + "\"}"),
            new com.openclaw.desktop.tool.ToolContext(null, null, tempDir, java.util.Map.of())
        ).block();

        assertNotNull(readResult);
        assertTrue(readResult.success());
        assertTrue(readResult.content().contains("Hello World"));
    }

    @Test
    void testLlmEventToResponse() {
        var events = List.<LlmEvent>of(
            new LlmEvent.TextDelta(0, "Hello "),
            new LlmEvent.TextDelta(0, "World"),
            new LlmEvent.AgentEnd(java.util.List.of())
        );

        var response = LlmEvent.toResponse(events);
        assertEquals("Hello World", response.text());
        assertTrue(response.toolCalls().isEmpty());
    }

    @Test
    void testToolCallEventToResponse() {
        var events = List.<LlmEvent>of(
            new LlmEvent.ToolCallStart(0, "list_files"),
            new LlmEvent.ToolCallDelta(0, "{\"path\":\"/tmp\"}"),
            new LlmEvent.ToolCallEnd(0, new com.openclaw.desktop.llm.MessageContent.ToolCallContent("call_0", "list_files", "{\"path\":\"/tmp\"}", null, "parallel")),
            new LlmEvent.AgentEnd(java.util.List.of())
        );

        var response = LlmEvent.toResponse(events);
        assertEquals(1, response.toolCalls().size());
        assertEquals("list_files", response.toolCalls().get(0).name());
        assertEquals("{\"path\":\"/tmp\"}", response.toolCalls().get(0).arguments());
    }

    @Test
    void testSessionLifecycle() {
        var session = new Session(SessionKey.main("lifecycle-test"));

        session.addUserMessage("Hello");
        assertEquals(1, session.transcript().size());

        session.addAssistantMessage("Hi there!");
        assertEquals(2, session.transcript().size());

        session.addToolResult("call_1", "result data");
        assertEquals(3, session.transcript().size());

        session.reset().block();
        assertEquals(0, session.transcript().size());
    }
}
