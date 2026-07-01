package com.openclaw.desktop;

import com.openclaw.desktop.agent.*;
import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.llm.*;
import com.openclaw.desktop.llm.provider.*;
import com.openclaw.desktop.session.*;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.tool.core.*;
import com.openclaw.desktop.keystore.*;
import com.openclaw.desktop.context.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 功能联调 + 真实API对接测试。
 *
 * 策略：
 * - Tier 1（无 API Key）：验证对象创建、消息流转、工具执行、AgentLoop 内部逻辑
 * - Tier 2（Ollama 本地）：如果本地有 Ollama 运行，验证本地模型
 * - Tier 3（有 API Key 时）：真实 LLM API 对接，需要环境变量
 */
class IntegrationSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(IntegrationSmokeTest.class);

    // ========== Tier 1: 无需 API Key ==========

    @Test
    @DisplayName("T1-01: AgentLoop 本地消息流转（Mock Provider）")
    void testAgentLoopLocalFlow() {
        var config = AgentConfig.defaults();
        var session = new Session(SessionKey.main("t1-01"));
        var toolRegistry = new ToolRegistry();
        toolRegistry.register(new ReadFileTool());
        toolRegistry.register(new WriteFileTool());

        // Mock provider — 返回固定响应
        var mockProvider = new StubLlmProvider("Hello from mock!");
        var agent = new Agent(config, mockProvider, toolRegistry, session);

        var response = agent.chat("Hi there").block();
        assertNotNull(response);
        assertTrue(response.length() > 0);
        log.info("T1-01 response: {}", response);
    }

    @Test
    @DisplayName("T1-02: Session 完整生命周期")
    void testSessionFullLifecycle() {
        var sm = new SessionManager();
        var session = sm.getOrCreate(SessionKey.main("t1-02")).block();
        assertNotNull(session);

        session.addUserMessage("What is Java?");
        session.addAssistantMessage("Java is a programming language.");
        session.addToolResult("call_0", "Java was created in 1995.");

        assertEquals(3, session.transcript().size());
        log.info("T1-02: session transcript entries: {}", session.transcript().size());
    }

    @Test
    @DisplayName("T1-03: Config 加载 + Provider 注册")
    void testConfigAndProviderRegistration() {
        var config = ClawConfig.defaults();
        assertNotNull(config);
        assertEquals("openai", config.llm().defaultProvider());

        var providerRegistry = new LlmProviderRegistry();
        providerRegistry.register(new OllamaProvider("http://localhost:11434"));
        assertTrue(providerRegistry.get("ollama").isPresent());
        log.info("T1-03: registered providers: {}", providerRegistry.all().stream().map(LlmProvider::id).toList());
    }

    @Test
    @DisplayName("T1-04: KeyStore 存储 + Provider 创建联动")
    void testKeyStoreAndProviderCreation() {
        var keyStore = new KeyStore(Path.of(System.getProperty("java.io.tmpdir"), "claw-test-ks.conf"));
        keyStore.put(ApiKeyEntry.of("openai", "sk-test-key", "https://api.openai.com/v1"));
        keyStore.put(ApiKeyEntry.of("deepseek", "sk-ds-test", "https://api.deepseek.com/v1"));

        assertTrue(keyStore.hasKey("openai"));
        var openaiEntry = keyStore.get("openai").orElseThrow();
        var provider = new OpenAiProvider(openaiEntry.apiKey(), openaiEntry.baseUrl());
        assertEquals("openai", provider.id());
        log.info("T1-04: provider created from KeyStore: {}", provider.name());
    }

    @Test
    @DisplayName("T1-05: Tool 注册 + 描述符 → LlmRequest")
    void testToolDescriptorsToLlmRequest() {
        var registry = new ToolRegistry();
        registry.register(new ReadFileTool());
        registry.register(new WriteFileTool());
        registry.register(new ListFilesTool());

        var descriptors = registry.listDescriptors();
        assertEquals(3, descriptors.size());

        var request = new LlmRequest(
            "gpt-4o",
            List.of(new Message.SystemMessage("You are helpful"),
                    new Message.UserMessage("Read /tmp/test.txt")),
            0.7, 4096, ReasoningLevel.OFF,
            descriptors, null, java.util.Map.of()
        );
        assertEquals(3, request.tools().size());
        log.info("T1-05: tools in request: {}", request.tools().stream().map(ToolDescriptor::name).toList());
    }

    @Test
    @DisplayName("T1-06: ContextEngine token 估算 + compact")
    void testContextEngineCompact() {
        var estimator = new TokenEstimator();
        var messages = List.<Message>of(
            new Message.SystemMessage("You are helpful"),
            new Message.UserMessage("Hello"),
            new Message.AssistantMessage("Hi!", List.of()),
            new Message.UserMessage("Tell me about Java"),
            new Message.AssistantMessage("Java is...", List.of())
        );
        var totalTokens = estimator.estimateConversation(messages);
        assertTrue(totalTokens > 0);

        var engine = new ContextEngine(ContextEngineConfig.defaults());
        var compactConfig = new CompactConfig("summary", 1000, true, true, 2, "Summarize earlier conversation");
        var result = engine.compact(messages, compactConfig);
        assertNotNull(result);
        log.info("T1-06: compacted from {} to {} tokens", result.originalTokens(), result.compactedTokens());
    }

    @Test
    @DisplayName("T1-07: LlmEvent 生命周期 → LlmResponse 聚合")
    void testLlmEventToResponseAggregation() {
        var tcContent = new MessageContent.ToolCallContent("call_0", "read_file", "{\"path\":\"/tmp\"}");
        java.util.List<LlmEvent> events = java.util.List.of(
            new LlmEvent.TextDelta(0, "Let me"),
            new LlmEvent.TextDelta(0, " read that"),
            new LlmEvent.TextDelta(0, " file."),
            new LlmEvent.ToolCallStart(0, "read_file"),
            new LlmEvent.ToolCallDelta(0, "{\"path\":\"/tmp\"}"),
            new LlmEvent.ToolCallEnd(0, tcContent),
            new LlmEvent.Usage(new UsageInfo(50, 100, 150, null, null))
        );
        var response = LlmEvent.toResponse(events);
        assertEquals("Let me read that file.", response.text());
        assertEquals(1, response.toolCalls().size());
        assertEquals(150, response.usage().totalTokens());
        log.info("T1-07: text={}, toolCalls={}, tokens={}", response.text(), response.toolCalls().size(), response.usage().totalTokens());
    }

    // ========== Tier 2: Ollama 本地 API ==========

    @Test
    @DisplayName("T2-01: Ollama 健康检查")
    void testOllamaHealthCheck() {
        var provider = new OllamaProvider("http://localhost:11434");
        var healthy = provider.healthCheck().block();
        log.info("T2-01: Ollama health = {}", healthy);
        // 软判断 — 不要求 Ollama 必须运行
        if (healthy) {
            log.info("Ollama is available — can proceed with real API tests");
        } else {
            log.warn("Ollama not running — skipping real API tests (not a failure)");
        }
    }

    @Test
    @DisplayName("T2-02: Ollama 真实非流式对话")
    void testOllamaRealChat() {
        var provider = new OllamaProvider("http://localhost:11434");
        var healthy = provider.healthCheck().block();
        if (!healthy) {
            log.warn("T2-02: Ollama not running — skip");
            return;
        }

        var request = new LlmRequest(
            "llama3",
            List.of(new Message.SystemMessage("Reply in one sentence."),
                    new Message.UserMessage("Say hello")),
            0.7, 50, ReasoningLevel.OFF,
            List.of(), null, java.util.Map.of()
        );

        try {
            var response = provider.chat(request).block();
            assertNotNull(response);
            assertNotNull(response.text());
            log.info("T2-02: Ollama response = {}", response.text());
        } catch (Exception e) {
            log.warn("T2-02: Ollama chat failed (model may not exist): {}", e.getMessage());
        }
    }

    @Test
    @DisplayName("T2-03: Ollama 真实流式对话")
    void testOllamaRealStream() {
        var provider = new OllamaProvider("http://localhost:11434");
        var healthy = provider.healthCheck().block();
        if (!healthy) {
            log.warn("T2-03: Ollama not running — skip");
            return;
        }

        var request = new LlmRequest(
            "llama3",
            List.of(new Message.SystemMessage("Reply briefly."),
                    new Message.UserMessage("What is 2+2?")),
            0.7, 50, ReasoningLevel.OFF,
            List.of(), null, java.util.Map.of()
        );

        try {
            var events = provider.chatStream(request).collectList().block();
            assertNotNull(events);
            assertTrue(events.size() > 0);
            var response = LlmEvent.toResponse(events);
            log.info("T2-03: stream events={}, text={}", events.size(), response.text());
        } catch (Exception e) {
            log.warn("T2-03: Ollama stream failed: {}", e.getMessage());
        }
    }

    @Test
    @DisplayName("T2-04: Ollama 模型列表")
    void testOllamaListModels() {
        var provider = new OllamaProvider("http://localhost:11434");
        var healthy = provider.healthCheck().block();
        if (!healthy) {
            log.warn("T2-04: Ollama not running — skip");
            return;
        }

        try {
            var models = provider.listModels().collectList().block();
            assertNotNull(models);
            log.info("T2-04: Ollama models = {}", models.stream().map(Model::id).toList());
        } catch (Exception e) {
            log.warn("T2-04: Ollama list models failed: {}", e.getMessage());
        }
    }

    // ========== Tier 3: 外部 API Key Provider ==========

    @Test
    @DisplayName("T3-01: OpenAI 健康检查（需要 OPENAI_API_KEY）")
    void testOpenAiHealthCheck() {
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("T3-01: OPENAI_API_KEY not set — skip");
            return;
        }

        var provider = new OpenAiProvider(apiKey);
        var healthy = provider.healthCheck().block();
        log.info("T3-01: OpenAI health = {}", healthy);
        assertTrue(healthy, "OpenAI should be reachable with valid key");
    }

    @Test
    @DisplayName("T3-02: OpenAI 真实对话（需要 OPENAI_API_KEY）")
    void testOpenAiRealChat() {
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("T3-02: OPENAI_API_KEY not set — skip");
            return;
        }

        var provider = new OpenAiProvider(apiKey);
        var request = new LlmRequest(
            "gpt-4o-mini",
            List.of(new Message.SystemMessage("Reply in one sentence."),
                    new Message.UserMessage("Say hello")),
            0.7, 50, ReasoningLevel.OFF,
            List.of(), null, java.util.Map.of()
        );

        var response = provider.chat(request).block();
        assertNotNull(response);
        assertNotNull(response.text());
        assertFalse(response.text().isEmpty());
        log.info("T3-02: OpenAI response = {}", response.text());
    }

    @Test
    @DisplayName("T3-03: DeepSeek 健康检查（需要 DEEPSEEK_API_KEY）")
    void testDeepSeekHealthCheck() {
        var apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("T3-03: DEEPSEEK_API_KEY not set — skip");
            return;
        }

        var provider = new DeepSeekProvider(apiKey);
        var healthy = provider.healthCheck().block();
        log.info("T3-03: DeepSeek health = {}", healthy);
    }

    @Test
    @DisplayName("T3-04: Qwen 健康检查（需要 QWEN_API_KEY）")
    void testQwenHealthCheck() {
        var apiKey = System.getenv("QWEN_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("T3-04: QWEN_API_KEY not set — skip");
            return;
        }

        var provider = new QwenProvider(apiKey);
        var healthy = provider.healthCheck().block();
        log.info("T3-04: Qwen health = {}", healthy);
    }

    // ========== Stub Provider（用于 Tier 1 mock） ==========

    private static class StubLlmProvider implements LlmProvider {
        private final String fixedResponse;

        StubLlmProvider(String fixedResponse) {
            this.fixedResponse = fixedResponse;
        }

        @Override public String id() { return "stub"; }
        @Override public String name() { return "Stub Provider"; }

        @Override
        public Mono<LlmResponse> chat(LlmRequest request) {
            return Mono.just(new LlmResponse(fixedResponse, List.of(),
                new UsageInfo(10, 20, 30, null, null), "stop", java.util.Map.of()));
        }

        @Override
        public Flux<LlmEvent> chatStream(LlmRequest request) {
            return Flux.just(
                new LlmEvent.TextDelta(0, fixedResponse),
                new LlmEvent.Usage(new UsageInfo(10, 20, 30, null, null))
            );
        }

        @Override
        public Flux<Model> listModels() {
            return Flux.just(Model.of("stub-model", "Stub Model", "stub"));
        }

        @Override
        public Mono<Boolean> healthCheck() {
            return Mono.just(true);
        }
    }
}
