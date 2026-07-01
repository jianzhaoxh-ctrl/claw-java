package com.openclaw.desktop.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 技能管理器测试 — 验证内置技能加载、触发词匹配、系统提示注入、启用/禁用。
 */
class SkillManagerTest {

    private SkillManager newManagerWithBuiltin() {
        var registry = new SkillRegistry(Path.of("skills"));
        var manager = new SkillManager(registry);
        manager.loadBuiltin();
        return manager;
    }

    @Test
    @DisplayName("loadBuiltin loads 3 built-in skills from classpath")
    void testLoadBuiltin() {
        var manager = newManagerWithBuiltin();
        assertTrue(manager.count() >= 3, "expected >=3 builtin skills, got " + manager.count());
        assertTrue(manager.get("file-operations").isPresent());
        assertTrue(manager.get("web-search").isPresent());
        assertTrue(manager.get("code-review").isPresent());
    }

    @Test
    @DisplayName("match returns skills whose triggers match user input")
    void testMatchByTrigger() {
        var manager = newManagerWithBuiltin();
        var matched = manager.match("帮我读取文件内容");
        assertFalse(matched.isEmpty());
        assertTrue(matched.stream().anyMatch(s -> s.name().equals("file-operations")));
    }

    @Test
    @DisplayName("match returns web-search for search query")
    void testMatchWebSearch() {
        var manager = newManagerWithBuiltin();
        var matched = manager.match("帮我搜索一下天气");
        assertFalse(matched.isEmpty());
        assertTrue(matched.stream().anyMatch(s -> s.name().equals("web-search")));
    }

    @Test
    @DisplayName("match returns empty for unrelated input")
    void testNoMatch() {
        var manager = newManagerWithBuiltin();
        var matched = manager.match("你好");
        assertTrue(matched.isEmpty());
    }

    @Test
    @DisplayName("buildSystemPromptInjection includes all skill content")
    void testInjection() {
        var manager = newManagerWithBuiltin();
        var injection = manager.buildSystemPromptInjection();
        assertTrue(injection.contains("可用技能"));
        assertTrue(injection.contains("file-operations"));
        assertTrue(injection.contains("web-search"));
    }

    @Test
    @DisplayName("buildSystemPromptInjection for specific skills")
    void testInjectionForSpecific() {
        var manager = newManagerWithBuiltin();
        var injection = manager.buildSystemPromptInjection(java.util.List.of("code-review"));
        assertTrue(injection.contains("code-review"));
        assertFalse(injection.contains("file-operations"));
    }

    @Test
    @DisplayName("disable prevents matching")
    void testDisable() {
        var manager = newManagerWithBuiltin();
        manager.disable("file-operations");
        var matched = manager.match("帮我读取文件内容");
        assertTrue(matched.stream().noneMatch(s -> s.name().equals("file-operations")));
    }

    @Test
    @DisplayName("enable re-enables a disabled skill")
    void testEnable() {
        var manager = newManagerWithBuiltin();
        manager.disable("file-operations");
        assertFalse(manager.isEnabled("file-operations"));
        manager.enable("file-operations");
        assertTrue(manager.isEnabled("file-operations"));
        var matched = manager.match("帮我读取文件内容");
        assertTrue(matched.stream().anyMatch(s -> s.name().equals("file-operations")));
    }
}
