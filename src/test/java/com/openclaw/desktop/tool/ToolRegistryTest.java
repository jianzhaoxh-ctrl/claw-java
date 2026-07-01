package com.openclaw.desktop.tool;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.types.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolRegistry 单元测试。
 */
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("register adds tool")
    void testRegister() {
        registry.register(new StubTool("tool1", "Tool 1"));
        assertEquals(1, registry.size());
        assertTrue(registry.contains("tool1"));
    }

    @Test
    @DisplayName("unregister removes tool")
    void testUnregister() {
        registry.register(new StubTool("tool1", "Tool 1"));
        registry.unregister("tool1");
        assertEquals(0, registry.size());
        assertFalse(registry.contains("tool1"));
    }

    @Test
    @DisplayName("get returns Optional")
    void testGet() {
        registry.register(new StubTool("tool1", "Tool 1"));
        assertTrue(registry.get("tool1").isPresent());
        assertTrue(registry.get("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("register same name overwrites")
    void testOverwrite() {
        registry.register(new StubTool("tool1", "Original"));
        registry.register(new StubTool("tool1", "Overwritten"));
        assertEquals(1, registry.size());
        assertEquals("Overwritten", registry.get("tool1").get().descriptor().title());
    }

    @Test
    @DisplayName("listDescriptors returns all descriptors")
    void testListDescriptors() {
        registry.register(new StubTool("t1", "T1"));
        registry.register(new StubTool("t2", "T2"));
        var descs = registry.listDescriptors();
        assertEquals(2, descs.size());
    }

    @Test
    @DisplayName("execute unknown tool returns failure")
    void testExecuteUnknown() {
        var input = new ToolInput("call1", "{}");
        var result = registry.execute("nonexistent", input, null).block();
        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("not found"));
    }

    @Test
    @DisplayName("execute known tool returns result")
    void testExecuteKnown() {
        registry.register(new StubTool("t1", "T1"));
        var input = new ToolInput("call1", "{}");
        var result = registry.execute("t1", input, null).block();
        assertNotNull(result);
        assertTrue(result.success());
    }

    @Test
    @DisplayName("empty registry size is 0")
    void testEmptySize() {
        assertEquals(0, registry.size());
    }

    /** 桩工具实现。 */
    static class StubTool implements Tool {
        private final String name;
        private final String title;

        StubTool(String name, String title) {
            this.name = name;
            this.title = title;
        }

        @Override
        public ToolDescriptor descriptor() {
            return new ToolDescriptor(name, title, "stub", JsonObject.empty(), JsonObject.empty());
        }

        @Override
        public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
            return Mono.just(ToolResult.success(input.toolCallId(), "stub result"));
        }
    }
}
