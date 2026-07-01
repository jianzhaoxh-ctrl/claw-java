package com.openclaw.desktop.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LlmProviderRegistry 单元测试。
 */
class LlmProviderRegistryTest {

    private LlmProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new LlmProviderRegistry();
    }

    @Test
    @DisplayName("register adds provider")
    void testRegister() {
        registry.register(new StubProvider("p1", "Provider 1"));
        assertEquals(1, registry.size());
        assertTrue(registry.get("p1").isPresent());
    }

    @Test
    @DisplayName("unregister removes provider")
    void testUnregister() {
        registry.register(new StubProvider("p1", "Provider 1"));
        registry.unregister("p1");
        assertEquals(0, registry.size());
        assertTrue(registry.get("p1").isEmpty());
    }

    @Test
    @DisplayName("getDefault returns first registered when no default set")
    void testGetDefaultNoExplicit() {
        var p1 = new StubProvider("p1", "Provider 1");
        var p2 = new StubProvider("p2", "Provider 2");
        registry.register(p1);
        registry.register(p2);
        // 没有显式设置 default，返回第一个（取决于 ConcurrentHashMap 迭代顺序）
        var def = registry.getDefault();
        assertNotNull(def);
    }

    @Test
    @DisplayName("setDefault sets the default provider")
    void testSetDefault() {
        var p1 = new StubProvider("p1", "Provider 1");
        var p2 = new StubProvider("p2", "Provider 2");
        registry.register(p1);
        registry.register(p2);
        registry.setDefault("p2");
        assertEquals("p2", registry.getDefault().id());
    }

    @Test
    @DisplayName("setDefault to non-existent falls back to first")
    void testSetDefaultNonExistent() {
        registry.register(new StubProvider("p1", "Provider 1"));
        registry.setDefault("nonexistent");
        var def = registry.getDefault();
        assertNotNull(def);
        assertEquals("p1", def.id());
    }

    @Test
    @DisplayName("getDefault returns null when empty")
    void testGetDefaultEmpty() {
        assertNull(registry.getDefault());
    }

    @Test
    @DisplayName("all returns all registered providers")
    void testAll() {
        registry.register(new StubProvider("p1", "P1"));
        registry.register(new StubProvider("p2", "P2"));
        var all = registry.all();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("get returns empty for non-existent")
    void testGetNonExistent() {
        assertTrue(registry.get("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("register same id overwrites")
    void testOverwrite() {
        registry.register(new StubProvider("p1", "Original"));
        registry.register(new StubProvider("p1", "Overwritten"));
        assertEquals(1, registry.size());
        assertEquals("Overwritten", registry.get("p1").get().name());
    }

    /** 简单的 Provider 桩。 */
    static class StubProvider implements LlmProvider {
        private final String id;
        private final String name;

        StubProvider(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override public String id() { return id; }
        @Override public String name() { return name; }
        @Override public reactor.core.publisher.Mono<LlmResponse> chat(LlmRequest request) {
            return reactor.core.publisher.Mono.empty();
        }
        @Override public reactor.core.publisher.Flux<LlmEvent> chatStream(LlmRequest request) {
            return reactor.core.publisher.Flux.empty();
        }
        @Override public reactor.core.publisher.Flux<Model> listModels() {
            return reactor.core.publisher.Flux.empty();
        }
        @Override public reactor.core.publisher.Mono<Boolean> healthCheck() {
            return reactor.core.publisher.Mono.just(true);
        }
    }
}
