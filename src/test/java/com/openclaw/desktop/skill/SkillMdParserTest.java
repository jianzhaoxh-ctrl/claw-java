package com.openclaw.desktop.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SKILL.md 解析器测试。
 */
class SkillMdParserTest {

    @Test
    @DisplayName("parse SKILL.md with full frontmatter")
    void testParseWithFrontmatter() {
        var content = """
            ---
            name: my-skill
            description: 测试技能
            triggers:
              - "做某事"
              - "do something"
            allowed-tools:
              - read_file
              - write_file
            enabled: true
            ---
            # My Skill

            这是技能正文。
            """;
        var def = SkillMdParser.parse(content, Path.of("/skills/my-skill/SKILL.md"));

        assertEquals("my-skill", def.name());
        assertEquals("测试技能", def.description());
        assertEquals(2, def.triggers().size());
        assertTrue(def.triggers().contains("做某事"));
        assertTrue(def.triggers().contains("do something"));
        assertEquals(2, def.allowedTools().size());
        assertTrue(def.enabled());
        assertTrue(def.content().startsWith("# My Skill"));
        assertTrue(def.content().contains("技能正文"));
    }

    @Test
    @DisplayName("parse SKILL.md without frontmatter falls back to dir name")
    void testParseWithoutFrontmatter() {
        var content = "# Plain Skill\n\n这是无 frontmatter 的技能。";
        var def = SkillMdParser.parse(content, Path.of("/skills/plain-skill/SKILL.md"));

        assertEquals("plain-skill", def.name()); // 取父目录名
        assertTrue(def.description().contains("无 frontmatter"));
        assertTrue(def.enabled()); // 默认启用
        assertTrue(def.triggers().isEmpty());
    }

    @Test
    @DisplayName("disabled skill has enabled=false")
    void testDisabledSkill() {
        var content = """
            ---
            name: disabled-skill
            enabled: false
            ---
            内容
            """;
        var def = SkillMdParser.parse(content, Path.of("/skills/disabled/SKILL.md"));
        assertFalse(def.enabled());
        assertEquals("disabled-skill", def.name());
    }

    @Test
    @DisplayName("metadata captures extra frontmatter fields")
    void testMetadata() {
        var content = """
            ---
            name: meta-skill
            author: tester
            version: "1.0"
            ---
            正文
            """;
        var def = SkillMdParser.parse(content, Path.of("/skills/meta/SKILL.md"));
        assertEquals("tester", def.metadata().get("author"));
        assertEquals("1.0", def.metadata().get("version"));
    }

    @Test
    @DisplayName("toDescriptor converts to lightweight descriptor")
    void testToDescriptor() {
        var content = """
            ---
            name: d-skill
            description: desc
            ---
            正文
            """;
        var def = SkillMdParser.parse(content, Path.of("/skills/d/SKILL.md"));
        var desc = def.toDescriptor();
        assertEquals("d-skill", desc.name());
        assertEquals("desc", desc.description());
        assertTrue(desc.enabled());
    }
}
