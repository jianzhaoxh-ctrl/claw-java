package com.openclaw.desktop;

import com.openclaw.desktop.agent.*;
import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.llm.*;
import com.openclaw.desktop.session.*;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.tool.core.*;
import com.openclaw.desktop.cron.*;
import com.openclaw.desktop.memory.*;
import com.openclaw.desktop.plugin.*;
import com.openclaw.desktop.skill.*;
import com.openclaw.desktop.mcp.*;
import com.openclaw.desktop.command.*;
import com.openclaw.desktop.keystore.*;
import com.openclaw.desktop.approval.*;
import com.openclaw.desktop.desktop.*;
import com.openclaw.desktop.context.*;
import com.openclaw.desktop.hook.*;
import com.openclaw.desktop.task.TaskStatus;
import com.openclaw.desktop.flow.FlowStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全系统集成测试 — 验证各模块之间的协作。
 * 阶段 S（质量保障）。
 */
class QualityAssuranceTest {

    @Test
    @DisplayName("All core modules can be instantiated")
    void testModuleInstantiation() {
        // Config
        var config = ClawConfig.defaults();
        assertNotNull(config);

        // Session
        var sessionManager = new SessionManager();
        var session = sessionManager.getOrCreate(SessionKey.main("qa-test")).block();
        assertNotNull(session);

        // Agent
        var agentConfig = AgentConfig.defaults();
        assertNotNull(agentConfig);

        // Tool
        var toolRegistry = new ToolRegistry();
        toolRegistry.register(new ReadFileTool());
        toolRegistry.register(new WriteFileTool());
        assertEquals(2, toolRegistry.listDescriptors().size());

        // LLM Provider Registry
        var providerRegistry = new LlmProviderRegistry();
        assertNotNull(providerRegistry);

        // Memory
        assertNotNull(new MemoryDatabase("data/test.db"));

        // Plugin
        var eventBus = new EventBus();
        assertNotNull(eventBus);

        // Skill
        var skillManager = new SkillManager(new SkillRegistry(Path.of("skills")));
        assertNotNull(skillManager);

        // MCP
        var mcpManager = new McpClientManager();
        assertNotNull(mcpManager);

        // Cron
        var cronStore = new CronJobStore("data/cron/jobs.db");
        assertNotNull(cronStore);

        // KeyStore
        var keyStore = new KeyStore();
        assertNotNull(keyStore);

        // Approval
        var approvalManager = new ApprovalManager(ApprovalPolicy.CONFIRM_DANGEROUS);
        assertNotNull(approvalManager);

        // Command
        var commandRegistry = new CommandRegistry();
        assertNotNull(commandRegistry);

        // AbortSignal
        var abortSignal = new AbortSignal();
        assertNotNull(abortSignal);

        // GoalTracker
        var goalTracker = new GoalTracker();
        assertNotNull(goalTracker);

        // SubAgentManager defaults
        assertNotNull(SubAgentManager.SubAgentConfig.defaults("test"));

        // AgentContext
        assertNotNull(AgentContext.defaults(agentConfig, providerRegistry, toolRegistry));

        // SystemInfo
        var sysInfo = new SystemInfo();
        assertNotNull(sysInfo.summary());

        // DesktopOps
        var ops = new DesktopOps();
        assertNotNull(ops.getHomeDir());

        // HookManager
        var hookManager = new HookManager();
        assertNotNull(hookManager);
    }

    @Test
    @DisplayName("ReasoningLevel and GoalStatus enums are complete")
    void testEnumsComplete() {
        assertEquals(4, ReasoningLevel.values().length);
        assertEquals(4, GoalTracker.GoalStatus.values().length);
        assertEquals(4, ApprovalPolicy.values().length);
        assertEquals(4, SubAgentManager.SubAgentStatus.values().length);
    }

    @Test
    @DisplayName("Sealed interface TaskStatus has expected subtypes")
    void testTaskStatusSubtypes() {
        var now = java.time.Instant.now();
        var pending = new TaskStatus.Pending(now);
        var running = new TaskStatus.Running(now);
        var completed = new TaskStatus.Completed(now, "done");
        var failed = new TaskStatus.Failed(now, "error");
        var cancelled = new TaskStatus.Cancelled(now, "stopped");

        assertNotNull(pending);
        assertNotNull(running);
        assertNotNull(completed);
        assertNotNull(failed);
        assertNotNull(cancelled);
    }

    @Test
    @DisplayName("Sealed interface FlowStatus has expected subtypes")
    void testFlowStatusSubtypes() {
        var now = java.time.Instant.now();
        var notStarted = new FlowStatus.NotStarted();
        var running = new FlowStatus.Running(now, "step1");
        var completed = new FlowStatus.Completed(now, java.util.Map.of());
        var failed = new FlowStatus.Failed(now, "error", "step1");
        var paused = new FlowStatus.Paused(now, "step1", "waiting");

        assertNotNull(notStarted);
        assertNotNull(running);
        assertNotNull(completed);
        assertNotNull(failed);
        assertNotNull(paused);
    }

    @Test
    @DisplayName("Session lifecycle end-to-end")
    void testSessionLifecycleE2E() {
        var sm = new SessionManager();
        var session = sm.getOrCreate(SessionKey.main("e2e")).block();
        assertNotNull(session);

        session.addUserMessage("Hello");
        session.addAssistantMessage("Hi!");
        assertEquals(2, session.transcript().size());

        session.addToolResult("call_1", "file content");
        assertEquals(3, session.transcript().size());

        session.reset().block();
        assertEquals(0, session.transcript().size());
    }

    @Test
    @DisplayName("Tool registration and descriptor generation")
    void testToolRegistrationE2E() {
        var registry = new ToolRegistry();
        registry.register(new ReadFileTool());
        registry.register(new WriteFileTool());
        registry.register(new ListFilesTool());

        var descriptors = registry.listDescriptors();
        assertEquals(3, descriptors.size());

        for (var desc : descriptors) {
            assertNotNull(desc.name());
            assertNotNull(desc.description());
        }

        assertTrue(registry.get("read_file").isPresent());
        assertTrue(registry.get("write_file").isPresent());
        assertTrue(registry.get("list_files").isPresent());
        assertFalse(registry.get("nonexistent").isPresent());
    }

    @Test
    @DisplayName("Config defaults are consistent")
    void testConfigDefaultsConsistency() {
        var config = ClawConfig.defaults();
        assertNotNull(config.gateway());
        assertNotNull(config.agent());
        assertNotNull(config.llm());
        assertNotNull(config.memory());

        assertEquals(7180, config.gateway().port());
        assertEquals("gpt-4o", config.agent().modelId());
        assertEquals("openai", config.llm().defaultProvider());
    }

    @Test
    @DisplayName("LlmEvent v2 complete lifecycle")
    void testLlmEventLifecycle() {
        var tcContent = new MessageContent.ToolCallContent("call_1", "read_file", "{\"path\":\"/tmp\"}");
        java.util.List<LlmEvent> events = java.util.List.of(
            new LlmEvent.TextDelta(0, "Hello"),
            new LlmEvent.TextDelta(0, " world"),
            new LlmEvent.ToolCallStart(0, "read_file"),
            new LlmEvent.ToolCallDelta(0, "{\"path\":\"/tmp\"}"),
            new LlmEvent.ToolCallEnd(0, tcContent),
            new LlmEvent.Usage(new UsageInfo(10, 20, 30, null, null))
        );

        var response = LlmEvent.toResponse(events);
        assertEquals("Hello world", response.text());
        assertEquals(1, response.toolCalls().size());
        assertEquals("read_file", response.toolCalls().get(0).name());
    }

    @Test
    @DisplayName("Message v2 types all work")
    void testMessageV2Types() {
        var system = new Message.SystemMessage("You are helpful");
        assertNotNull(system.content());
        assertEquals("You are helpful", MessageContent.extractText(system.content()));

        var user = new Message.UserMessage("Hello");
        assertNotNull(user.content());
        assertEquals("Hello", MessageContent.extractText(user.content()));

        var assistant = new Message.AssistantMessage("Hi there!", java.util.List.of());
        assertEquals("Hi there!", assistant.text());

        var toolResult = new Message.ToolResultMessage("call_1", "result");
        assertEquals("result", toolResult.text());
        assertEquals("call_1", toolResult.toolCallId());
    }

    @Test
    @DisplayName("Cron expression parsing works")
    void testCronParsing() {
        assertNotNull(CronExpression.parse("0 9 * * MON"));
        assertNotNull(CronExpression.parse("*/5 * * * *"));
        assertNotNull(CronExpression.parse("0 18 * * *"));
    }

    @Test
    @DisplayName("KeyStore store and retrieve cycle")
    void testKeyStoreCycle() throws Exception {
        var store = new KeyStore();
        var entry = ApiKeyEntry.of("openai", "sk-test-key", "https://api.openai.com/v1");
        store.put(entry);

        var entries = store.listAll();
        assertTrue(entries.size() >= 1);
    }

    @Test
    @DisplayName("Approval system needsApproval works")
    void testApprovalSystem() {
        var manager = new ApprovalManager(ApprovalPolicy.CONFIRM_DANGEROUS);
        // delete_file is dangerous — should need approval under CONFIRM_DANGEROUS
        assertTrue(manager.needsApproval("delete_file", "/tmp/test.txt"));
        // read_file is safe — should not need approval
        assertFalse(manager.needsApproval("read_file", "/tmp/test.txt"));
    }

    @Test
    @DisplayName("EventBus publish and subscribe")
    void testEventBus() {
        var bus = new EventBus();
        var received = new java.util.ArrayList<String>();
        bus.subscribe(PluginEvent.PluginLoaded.class, e -> received.add(e.pluginId()));
        bus.publish(new PluginEvent.PluginLoaded("test-plugin", "Test Plugin"));
        assertEquals(1, received.size());
        assertEquals("test-plugin", received.get(0));
    }

    @Test
    @DisplayName("ContextEngine can estimate tokens")
    void testContextEngineTokens() {
        var estimator = new TokenEstimator();
        var tokens = estimator.estimateText("Hello world, this is a test message for token counting.");
        assertTrue(tokens > 0);
        assertTrue(tokens < 100);
    }

    @Test
    @DisplayName("HookManager register and count")
    void testHookManager() {
        var manager = new HookManager();
        var hook = HookDefinition.simple("test-hook",
            HookDefinition.HookTrigger.TOOL_CALL,
            HookDefinition.HookPhase.BEFORE);
        manager.register(hook);
        assertEquals(1, manager.count());
    }

    @Test
    @DisplayName("All package names follow convention")
    void testPackageNamingConvention() {
        assertNotNull(Agent.class.getPackage());
        assertTrue(Agent.class.getPackage().getName().startsWith("com.openclaw.desktop"));
    }
}
