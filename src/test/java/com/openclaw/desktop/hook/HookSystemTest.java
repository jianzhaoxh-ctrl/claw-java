package com.openclaw.desktop.hook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HookManager / HookRegistry / HookExecutor 单元测试。
 */
class HookSystemTest {

    private HookManager manager;

    @BeforeEach
    void setup() {
        manager = new HookManager();
    }

    @Test
    void testRegisterAndList() {
        var hook = HookDefinition.simple("test-hook", HookDefinition.HookTrigger.CHAT, HookDefinition.HookPhase.BEFORE);
        manager.register(hook);

        assertEquals(1, manager.count());
        assertEquals(1, manager.listAll().size());
        assertEquals("test-hook", manager.listAll().get(0).id());
    }

    @Test
    void testUnregister() {
        var hook = HookDefinition.simple("remove-me", HookDefinition.HookTrigger.CHAT, HookDefinition.HookPhase.BEFORE);
        manager.register(hook);
        assertEquals(1, manager.count());

        manager.unregister("remove-me");
        assertEquals(0, manager.count());
    }

    @Test
    void testEnableDisable() {
        var hook = HookDefinition.simple("toggle-hook", HookDefinition.HookTrigger.TOOL_CALL, HookDefinition.HookPhase.BEFORE);
        manager.register(hook);

        assertTrue(manager.listAll().get(0).enabled());
        manager.disable("toggle-hook");
        assertFalse(manager.registry().get("toggle-hook").enabled());

        manager.enable("toggle-hook");
        assertTrue(manager.registry().get("toggle-hook").enabled());
    }

    @Test
    void testBeforeChatPass() {
        // 注册一个 LOG 钩子（默认行为是 pass）
        var hook = HookDefinition.simple("log-chat", HookDefinition.HookTrigger.CHAT, HookDefinition.HookPhase.BEFORE);
        manager.register(hook);

        var result = manager.beforeChat("default", "gpt-4o", "Hello").block();
        assertNotNull(result);
        assertTrue(result.shouldContinue());
    }

    @Test
    void testBeforeChatBlock() {
        // 注册一个 BLOCK 钩子
        var hook = new HookDefinition(
            "block-hook", "Block Chat", HookDefinition.HookPhase.BEFORE,
            HookDefinition.HookTrigger.CHAT, HookDefinition.HookAction.BLOCK,
            0, true, java.time.Instant.now()
        );
        manager.register(hook);

        var result = manager.beforeChat("default", "gpt-4o", "Hello").block();
        assertNotNull(result);
        assertFalse(result.shouldContinue());
        assertNotNull(result.blockReason());
    }

    @Test
    void testBeforeToolCallPass() {
        var hook = HookDefinition.simple("log-tool", HookDefinition.HookTrigger.TOOL_CALL, HookDefinition.HookPhase.BEFORE);
        manager.register(hook);

        var result = manager.beforeToolCall("default", "read_file", "call_1", "{}").block();
        assertNotNull(result);
        assertTrue(result.shouldContinue());
    }

    @Test
    void testOnSessionStart() {
        var hook = HookDefinition.simple("session-log", HookDefinition.HookTrigger.SESSION_START, HookDefinition.HookPhase.BEFORE);
        manager.register(hook);

        var result = manager.onSessionStart("main:default", "default").block();
        assertNotNull(result);
        assertTrue(result.shouldContinue());
    }

    @Test
    void testPriorityOrdering() {
        var low = new HookDefinition("low", "Low Priority", HookDefinition.HookPhase.BEFORE,
            HookDefinition.HookTrigger.CHAT, HookDefinition.HookAction.LOG, 10, true, java.time.Instant.now());
        var high = new HookDefinition("high", "High Priority", HookDefinition.HookPhase.BEFORE,
            HookDefinition.HookTrigger.CHAT, HookDefinition.HookAction.LOG, 1, true, java.time.Instant.now());

        manager.register(low);
        manager.register(high);

        var matching = manager.registry().findMatching(HookDefinition.HookTrigger.CHAT, HookDefinition.HookPhase.BEFORE);
        assertEquals(2, matching.size());
        assertEquals("high", matching.get(0).id());  // 低 priority 值先执行
        assertEquals("low", matching.get(1).id());
    }

    @Test
    void testMultipleHooksChain() {
        // 注册两个 MODIFY 钩子
        var hook1 = new HookDefinition("modify-1", "Modify 1", HookDefinition.HookPhase.BEFORE,
            HookDefinition.HookTrigger.CHAT, HookDefinition.HookAction.MODIFY, 1, true, java.time.Instant.now());
        var hook2 = new HookDefinition("modify-2", "Modify 2", HookDefinition.HookPhase.BEFORE,
            HookDefinition.HookTrigger.CHAT, HookDefinition.HookAction.MODIFY, 2, true, java.time.Instant.now());

        manager.register(hook1);
        manager.register(hook2);

        var result = manager.beforeChat("default", "gpt-4o", "Hello").block();
        assertNotNull(result);
        assertTrue(result.shouldContinue());
        // 两个 MODIFY 钩子都应该修改内容
        assertNotNull(result.modifiedContent());
    }

    @Test
    void testRegistryClear() {
        manager.register(HookDefinition.simple("h1", HookDefinition.HookTrigger.CHAT, HookDefinition.HookPhase.BEFORE));
        manager.register(HookDefinition.simple("h2", HookDefinition.HookTrigger.TOOL_CALL, HookDefinition.HookPhase.AFTER));
        assertEquals(2, manager.count());

        manager.registry().clear();
        assertEquals(0, manager.registry().count());
    }
}
