package com.openclaw.desktop.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 注册表 — 管理技能的发现、加载和查询。
 * 对应 OpenClaw 的 skill 系统。
 *
 * <p>使用 {@link SkillMdParser} 解析 SKILL.md（YAML frontmatter + 正文），
 * 内部存储 {@link SkillDefinition}（完整定义），同时提供向后兼容的
 * {@link SkillDescriptor}（轻量描述符）访问方法。
 */
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();
    private final Path skillsDir;

    public SkillRegistry(Path skillsDir) {
        this.skillsDir = skillsDir;
    }

    /**
     * 扫描 skills 目录，发现并解析所有可用技能。
     */
    public void discover() {
        if (!Files.isDirectory(skillsDir)) {
            log.info("Skills directory not found: {}", skillsDir);
            return;
        }

        try (var stream = Files.list(skillsDir)) {
            stream.filter(Files::isDirectory)
                .forEach(this::loadSkill);
        } catch (IOException e) {
            log.error("Failed to scan skills directory: {}", e.getMessage());
        }

        log.info("Discovered {} skills", skills.size());
    }

    /**
     * 手动注册一个技能定义（用于插件动态注册）。
     */
    public void register(SkillDefinition definition) {
        skills.put(definition.name(), definition);
        log.debug("Skill registered: {}", definition.name());
    }

    /**
     * 注销技能。
     */
    public void unregister(String name) {
        skills.remove(name);
    }

    private void loadSkill(Path skillDir) {
        var skillMd = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillMd)) return;

        try {
            var def = SkillMdParser.parse(skillMd);
            if (!def.enabled()) {
                log.debug("Skill disabled, skipping: {}", def.name());
                return;
            }
            skills.put(def.name(), def);
            log.debug("Loaded skill: {} (triggers={})", def.name(), def.triggers());
        } catch (IOException e) {
            log.warn("Failed to read skill: {} - {}", skillDir, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to parse skill {}: {}", skillDir, e.getMessage(), e);
        }
    }

    // ---- SkillDefinition 访问（完整定义） ----

    public Optional<SkillDefinition> getDefinition(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public Collection<SkillDefinition> allDefinitions() {
        return Collections.unmodifiableCollection(skills.values());
    }

    public Optional<String> readSkillContent(String name) {
        var def = skills.get(name);
        return def != null ? Optional.of(def.content()) : Optional.empty();
    }

    // ---- SkillDescriptor 访问（轻量描述符，向后兼容） ----

    public Optional<SkillDescriptor> get(String name) {
        var def = skills.get(name);
        return Optional.ofNullable(def).map(SkillDefinition::toDescriptor);
    }

    public Collection<SkillDescriptor> all() {
        return skills.values().stream()
            .map(SkillDefinition::toDescriptor)
            .toList();
    }

    public List<SkillDescriptor> search(String query) {
        var lower = query.toLowerCase();
        return skills.values().stream()
            .filter(s -> s.name().toLowerCase().contains(lower)
                || s.description().toLowerCase().contains(lower))
            .sorted(Comparator.comparing(SkillDefinition::name))
            .map(SkillDefinition::toDescriptor)
            .toList();
    }

    public int size() {
        return skills.size();
    }
}
